package VectorCache
import Chisel._
import Literal._

// IMPORTANT:
// ensure FIFO queues at all input and output to avoid combinational loops

class CacheController(lineSize: Int, depth: Int, addrBits: Int) extends Module {
  val io = new Bundle {
    // interface towards processing element:
    // read port
    val readReq = new DeqIO(UInt(width = addrBits))
    val readResp = new EnqIO(UInt(width = lineSize))
    // readRespInd should be part of readResp, but Verilator sizes...
    val readRespInd = UInt(OUTPUT, width = addrBits)
    // interface towards processing element:
    // write port
    val writeReq = new DeqIO(UInt(width = addrBits))
    // writeData should be part of writeReq, but Verilator
    // best handles 1-, 32- and 64-bit widths, so it is separated
    val writeData = UInt(INPUT, lineSize)
    // interface towards main memory, read requests
    val memReq = new EnqIO(UInt(width = addrBits))
    val memResp = new DeqIO(UInt(width = lineSize))
    // interface towards main memory, write requests
    val memWriteReq = new EnqIO(UInt(width = addrBits))
    val memWriteData = UInt(OUTPUT, lineSize)
    // flush cache
    val flushCache = Bool(INPUT)
    // init indicator and cache stat outputs
    val cacheActive = Bool(OUTPUT)
    val readCount = UInt(OUTPUT, 32)
    val readMissCount = UInt(OUTPUT, 32)
    val writeCount = UInt(OUTPUT, 32)
    val writeMissCount = UInt(OUTPUT, 32)
  }
  // registers for read/miss counts
  val readCount = Reg(init = UInt(0, 32))
  val writeCount = Reg(init = UInt(0, 32))
  val readMissCount = Reg(init = UInt(0, 32))
  val writeMissCount = Reg(init = UInt(0, 32))
  
  // memory for keeping cache state
  val depthBitCount = log2Up(depth)
  val tagBitCount = addrBits - depthBitCount

  // combinational-read memory for tag and valid (LUTRAM)
  val tagStorage = Mem(UInt(width=tagBitCount+1), depth, false)
  // enable sequential reads for data storage (to infer BRAM)
  val cacheLines = Mem(UInt(width=lineSize), depth, true)
  
  val sInit :: sActive :: sFinishPendingWrites :: sIssueReadReq :: sEvictWrite :: sCacheFill :: sInitCacheFlush :: sCacheFlush :: Nil = Enum(UInt(), 8)
  val state = Reg(init = UInt(sInit))
  val initCtr = Reg(init = UInt(0, depthBitCount))
  
  // shorthands for current read request
  val currentReq = io.readReq.bits
  val currentReqInd = currentReq(depthBitCount-1, 0)         // lower bits as index
  val currentReqTag = currentReq(addrBits-1, depthBitCount)  // higher bits as tag
  
  // shorthands for tag/valid data for current read request
  val currentReqEntry = tagStorage(currentReqInd)
  val currentReqLineValid = currentReqEntry(0)               // lowest bit = valid
  val currentReqLineTag = currentReqEntry(tagBitCount, 1)    // other bits = tag
  
  // shorthands for current write request
  val currentWriteReq = io.writeReq.bits
  val currentWriteReqInd = currentWriteReq(depthBitCount-1, 0)
  val currentWriteReqTag = currentWriteReq(addrBits-1, depthBitCount)
  
  // shorthands for tag/valid data for current write request
  val currentWriteReqEntry = tagStorage(currentWriteReqInd)
  val currentWriteReqLineValid = currentWriteReqEntry(0)
  val currentWriteReqLineTag = currentWriteReqEntry(tagBitCount, 1)
  
  // register to enable writing to read response FIFO
  val enableWriteOutputReg = Reg(init = Bool(false))
  // register current request (registered reads to infer FPGA BRAM)
  val requestReg = Reg(next = currentReq)
  val bramReadAddr = requestReg(depthBitCount-1, 0)
  val bramReadValue = cacheLines(bramReadAddr)
  
  // register for cache flushing
  val flushDataReg = Reg(next = cacheLines(initCtr))
  
  io.readResp.bits := bramReadValue
  io.readResp.valid := enableWriteOutputReg
  
  io.readRespInd := requestReg
  
  // shorthands for current memory read response
  val currentMemResp = io.memResp.bits
  
  // drive default outputs
  io.cacheActive := (state === sActive)
  io.writeReq.ready := Bool(false)  // whether cache can accept a write req
  io.memWriteReq.valid := Bool(false) // no write request to main mem
  io.memWriteReq.bits := currentWriteReq  // default write miss addr
  io.memWriteData := io.writeData     // mem write request data comes right from the input
  io.memReq.valid := Bool(false)    // no read request to main mem
  io.readReq.ready := Bool(false)   // whether cache can accept a read req
  io.memResp.ready := Bool(false)   // whether cache can accept a mem resp
  
  // drive outputs for counters
  io.readCount := readCount         // total reads so far (hits = total - misses)
  io.readMissCount := readMissCount         // total read misses so far
  io.writeCount := writeCount     // total writes so far
  io.writeMissCount := writeMissCount   // total write misses so far
  
  // default next-values for registers
  enableWriteOutputReg := Bool(false)
  
  when (state === sInit)
  {
    // unset all valid bit in cache at init time
    // TODO FPGA mem can be initialized at config time, this state
    // can be removed
    
    // reset every valid bit in the cache
    tagStorage(initCtr) := UInt(0)
    
    // increment the initialization counter
    initCtr := initCtr + UInt(1)
    
    // go to sActive when all blocks initialized
    when (initCtr === UInt(depth-1)) { state := sActive}
  }
  .elsewhen (state === sInitCacheFlush)
  {
    // this state only exists to "prefetch" the line 0 content
    state := sCacheFlush
    initCtr := initCtr + UInt(1)
  }
  .elsewhen (state === sCacheFlush)
  {
    when (io.memWriteReq.ready)
    {
      initCtr := initCtr + UInt(1)
      val fetchedInd = initCtr - UInt(1)
      val lineToFlush = tagStorage(fetchedInd)
      val flushValid = lineToFlush(0)
      val flushTag = lineToFlush(tagBitCount, 1)
      
      io.memWriteReq.valid := flushValid
      io.memWriteReq.bits := Cat(flushTag, fetchedInd)
      io.memWriteData := flushDataReg
      
      // go to sActive when all blocks flushed
      // when initCtr is 0 (overflow) we have reached the
      // last block, since we read the index initCtr-1
      when (initCtr === UInt(0)) { state := sActive}
    }
  }
  .elsewhen (state === sActive)
  {
    when (io.flushCache)
    {
      // flush the cache
      state := sInitCacheFlush
      initCtr := UInt(0)
    }
    .otherwise
    {
      // no flush requested, cache ready to serve requests (no pending misses)
      
      // write port stuff ----------------------
      // write port dumps all misses straight to main mem, so it can
      // always accept as long as the main mem write port is available
      io.writeReq.ready := io.memWriteReq.ready

      when (io.writeReq.valid)
      {
        when (currentWriteReqTag === currentWriteReqLineTag & currentWriteReqLineValid)
        {
          // cache write hit
          // keep statistics for writes
          writeCount := writeCount + UInt(1)
          
          // update data in BRAM
          cacheLines(currentWriteReqInd) := io.writeData
        }
        .elsewhen (io.memWriteReq.ready)
        {
          // cache write miss, issue as write request
          io.memWriteReq.valid := Bool(true)
          // keep statistics for writes
          writeCount := writeCount + UInt(1)
          writeMissCount := writeMissCount + UInt(1)
        }
      }

      // read port stuff ------------------------
      // if something pending on the input queue
      when (io.readReq.valid)
      {
        // if valid and tag match and space on output queue
        when (currentReqTag === currentReqLineTag & currentReqLineValid & io.readResp.ready)
        {
          // cache hit!
          readCount := readCount + UInt(1)
          
          // read from input queue
          io.readReq.deq()
          
          // enable write to output FIFO next cycle
          enableWriteOutputReg := Bool(true)
        }
        .elsewhen (currentReqTag != currentReqLineTag || ~currentReqLineValid)
        {
          // read miss
          state := sFinishPendingWrites
          // increment the cache miss counter
          readMissCount := readMissCount + UInt(1)
        }
        // other cases are not explicitly covered (default values suffice)
        // TODO a good place for bugs you say?
      }
    }
  }
  .elsewhen (state === sFinishPendingWrites)
  {
    // to avoid the extra RAW hazards caused by conflict misses,
    // attempt to consume all write requests before issuing read
    
    // writes can always proceed if space available on the
    // memWriteReq queue
    io.writeReq.ready := io.memWriteReq.ready
    
    when (io.writeReq.valid)
    {
      // pending writes to be serviced
      when (currentWriteReqTag === currentWriteReqLineTag & currentWriteReqLineValid)
      {
        // cache write hit
        // keep statistics for writes
        writeCount := writeCount + UInt(1)
        
        // update data in BRAM
        cacheLines(currentWriteReqInd) := io.writeData
      }
      .elsewhen (io.memWriteReq.ready)
      {
        // cache write miss, issue as write request
        io.memWriteReq.valid := Bool(true)
        // keep statistics for writes
        writeCount := writeCount + UInt(1)
        writeMissCount := writeMissCount + UInt(1)
      }
    }
    .otherwise
    {
      // no further writes to service
      state := sIssueReadReq
    }
  }
  .elsewhen (state === sIssueReadReq)
  {
    // enqueue the read miss
    io.memReq.bits := currentReq
    io.memReq.valid := Bool(true)
    // go to sEvictWrite state, figure out cold
    // cacheline misses there
    state := sEvictWrite
  }
  .elsewhen (state === sEvictWrite)
  {
    when (io.memWriteReq.ready)
    {
      // write the evicted read data if it was valid
      io.memWriteReq.valid := currentReqLineValid
      // evicted address = line tag + current index
      io.memWriteReq.bits := Cat(currentReqLineTag, currentReqInd)
      // read evicted data from BRAM
      io.memWriteData := bramReadValue
      state := sCacheFill
    }
  }
  .elsewhen (state === sCacheFill)
  {
    // cache is waiting for a pending miss to be served from main mem
    // pop response from queue
    io.memResp.ready := Bool(true)
   
    // check to see if any responses from memory are pending
    when (io.memResp.valid)
    {
      // write returned data to cache
      cacheLines(currentReqInd) := currentMemResp
      // fix tag and valid bits
      tagStorage(currentReqInd) := Cat(currentReqTag, Bits(1, width=1))
      
      // go back to active state
      state := sActive
    }
  }
}
