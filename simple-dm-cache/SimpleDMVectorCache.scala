package VectorCache
import Chisel._
import Literal._

// IMPORTANT:
// ensure FIFO queues at all input and output to avoid combinational loops

class SimpleDMVectorCache(lineSize: Int, depth: Int, addrBits: Int) extends Module {
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
  
  val sInit :: sActive :: sEvictWrite :: sCacheFill :: sCacheFlush :: Nil = Enum(UInt(), 5)
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
  .elsewhen (state === sCacheFlush)
  {
    when (io.memWriteReq.ready)
    {
      initCtr := initCtr + UInt(1)
      val lineToFlush = tagStorage(initCtr)
      val flushValid = lineToFlush(0)
      val flushTag = lineToFlush(tagBitCount, 1)
      
      io.memWriteReq.valid := flushValid
      io.memWriteReq.bits := Cat(flushTag, initCtr)
      io.memWriteData := flushDataReg
      
      // go to sActive when all blocks flushed
    when (initCtr === UInt(depth-1)) { state := sActive}
    }
  }
  .elsewhen (state === sActive)
  {
    when (io.flushCache)
    {
      // flush the cache
      state := sCacheFlush
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
        // read miss case, no eviction (cold cacheline)
        .elsewhen (~currentReqLineValid & io.memReq.ready)
        {
          readMissCount := readMissCount + UInt(1)
          state := sCacheFill
          io.memReq.enq(currentReq)
        }
        // read miss, with eviction
        .elsewhen (currentReqTag != currentReqLineTag & io.memReq.ready)
        {
          readMissCount := readMissCount + UInt(1)
          state := sEvictWrite
          io.memReq.enq(currentReq)
        }
        // other cases are not explicitly covered (default values suffice)
        // TODO a good place for bugs you say?
      }
    }
  }
  .elsewhen (state === sEvictWrite)
  {
    when (io.memWriteReq.ready)
    {
      // write the evicted read data
      // evicted address = line tag + current index
      io.memWriteReq.enq(Cat(currentReqLineTag, currentReqInd))
      // read evicted data from BRAM
      io.memWriteData := bramReadValue
      state := sCacheFill
    }
  }
  .elsewhen (state === sCacheFill)
  {
    // TODO should we service write requests while in this state?
    // cache is waiting for a pending miss to be served from main mem
   
    // check to see if any responses from memory are pending
    when (io.memResp.valid)
    {
      // write returned data to cache
      cacheLines(currentReqInd) := currentMemResp
      // fix tag and valid bits
      tagStorage(currentReqInd) := Cat(currentReqTag, Bits(1, width=1))
      // pop response from queue
      io.memResp.deq()
      // go back to active state
      state := sActive
    }
  }
}

class SimpleDMVectorCacheTester(c: SimpleDMVectorCache, depth: Int) extends Tester(c) {
  // TODO add some proper write testing
  // these tests should include:
  // - read cold miss
  // - read hit
  // - read conflict miss
  // - write hit
  // - write miss
  
  // start with no cache reqs, no mem resps
  poke(c.io.readReq.valid, 0)
  poke(c.io.memResp.valid, 0)
  poke(c.io.writeReq.valid, 0)
  poke(c.io.flushCache, 0)
  
  poke(c.io.memReq.ready, 1)  // memreq can always accept (infinite memory request FIFO)
  poke(c.io.memWriteReq.ready, 1) // infinite mem req FIFO for writes as well
  poke(c.io.readResp.ready, 1) // readresp can always accept (infinite datapath FIFO)
  expect(c.io.cacheActive, 0)
  // expect cacheActive after "depth" cycles
  step(depth)
  expect(c.io.cacheActive, 1)
  for(i <- 1 to 3)
  {
    expect(c.io.readResp.valid, 0)    // no read response appears
    expect(c.io.memReq.valid, 0)      // no mem request appears
    expect(c.io.memWriteReq.valid, 0) // no mem write req
    expect(c.io.readReq.ready, 0)     // nothing to pop from reads
    expect(c.io.writeReq.ready, 1)    // since memWriteReq is ready
    step(1)
  }
  // put in a write request
  poke(c.io.writeReq.bits, 9)
  poke(c.io.writeReq.valid, 1)
  poke(c.io.writeData, 111)
  
  // put in a read request
  poke(c.io.readReq.bits, 7)
  poke(c.io.readReq.valid, 1)
	// this will be a cache miss (nothing in cache)
  // request should immediately appear at memReq
  // (since tag reads/miss checks are combinational)
  expect(c.io.memReq.bits, 7)
  expect(c.io.memReq.valid, 1)
  // similarly, the write req should also miss and cause
  // a memory request
  expect(c.io.memWriteReq.valid, 1)
  expect(c.io.memWriteReq.bits, 9)
  expect(c.io.memWriteData, 111)
  step(1)
  // expect a cache miss to appear in the counters
  expect(c.io.readMissCount, 1)
  expect(c.io.readCount, 0)
  expect(c.io.writeCount, 1)
  expect(c.io.writeMissCount, 1)
  // cache shouldn't be in sActive now
  expect(c.io.cacheActive, 0)
  // wait a while
  for(i <- 1 to 3)
  {
    expect(c.io.readReq.ready, 0)    // even though memReq.valid=1
    expect(c.io.readResp.valid, 0)  // no read response
    expect(c.io.memResp.ready, 0)   // memResp not arrived yet, can't read
    expect(c.io.writeReq.ready, 0)  // can't do writes while blocked on read
    expect(c.io.memWriteReq.valid, 0)
    step(1)
  }
  // issue memory response
  poke(c.io.memResp.bits, 0x1f)
  poke(c.io.memResp.valid, 1)
  expect(c.io.memResp.ready, 1) // pop reply
  expect(c.io.readResp.valid, 0)  // still no read response - just tag write
  expect(c.io.readReq.ready, 0) 
  step(1)
  poke(c.io.memResp.valid, 0) // remove mem reply
  // the tag read should now succeed (back to sActive)
  expect(c.io.cacheActive, 1)
  // this hit should also pop from the request queue
  expect(c.io.readReq.ready, 1)
  step(1)
  // remove request
  poke(c.io.readReq.valid, 0)
  expect(c.io.readReq.ready, 0)
  // expect cache read response and counters
  expect(c.io.readResp.valid, 1)
  expect(c.io.readResp.bits, 0x1f)  // returned data
  expect(c.io.readRespInd, 7)   // returned index
  step(1)
  // make sure nothing happens while no request
  for(i <- 1 to 3)
  {
    expect(c.io.readResp.valid, 0)  // no read response appears
    expect(c.io.memReq.valid, 0)    // no mem request appears
    expect(c.io.readReq.ready, 0)   // nothing to pop
    step(1)
  }
  // repeat the same request - this time it should hit
  poke(c.io.readReq.valid, 1)
  poke(c.io.readReq.bits, 7)
  expect(c.io.cacheActive, 1)
  expect(c.io.readReq.ready, 1)
  step(1)
  for(i <- 1 to 3)
  {
    // keep repeating the same request
    // should stay in active state & keep hitting
    expect(c.io.cacheActive, 1)
    expect(c.io.memReq.valid, 0) // no mem request
    // check read/miss counters
    expect(c.io.readCount, i+1) // read count should go up
    expect(c.io.readMissCount, 1)   // miss count shouldn't go up
    // check read response
    expect(c.io.readResp.valid, 1)
    expect(c.io.readResp.bits, 0x1f)
    step(1)     
  }
}
