package VectorCache

import Chisel._
import Literal._
import Node._

import CacheInterface._

// IMPORTANT:
// ensure FIFO queues at all input and output to avoid combinational loops

class CacheController(lineSize: Int, depth: Int, addrBits: Int) extends Module {
  val io = new Bundle {
    // ports towards processor and main mem
    val externalIF = new SinglePortCacheIF(addrBits, lineSize)
    
    // ports towards the data memory
    val dataPortA = new CacheDataReadWritePort(lineSize, addrBits).flip // for read reqs
    val dataPortB = new CacheDataReadWritePort(lineSize, addrBits).flip // for write reqs
    
    // ports towards the tag memory
    val tagPortA = new CacheDataReadWritePort(lineSize, addrBits).flip  // for read reqs
    val tagPortB = new CacheDataReadPort(lineSize, addrBits).flip  // for write reqs
  }
  
  // registers for read/miss counts
  val readCount = Reg(init = UInt(0, 32))
  val writeCount = Reg(init = UInt(0, 32))
  val readMissCount = Reg(init = UInt(0, 32))
  val writeMissCount = Reg(init = UInt(0, 32))
  
  // memory for keeping cache state
  val depthBitCount = log2Up(depth)
  val tagBitCount = addrBits - depthBitCount
  
  val sInit :: sActive :: sFinishPendingWrites :: sIssueReadReq :: sEvictWrite :: sCacheFill :: sInitCacheFlush :: sCacheFlush :: Nil = Enum(UInt(), 8)
  val state = Reg(init = UInt(sInit))
  val initCtr = Reg(init = UInt(0, depthBitCount))
  
  // shorthands for current read request
  val currentReq = io.externalIF.readPort.readReq.bits
  val currentReqInd = currentReq(depthBitCount-1, 0)         // lower bits as index
  val currentReqTag = currentReq(addrBits-1, depthBitCount)  // higher bits as tag
  
  // defaults for tag port A, dedicated to read requests
  io.tagPortA.addr := currentReqInd
  io.tagPortA.dataIn := Cat(currentReqTag, Bits(1, width=1))  // read miss replacement
  io.tagPortA.writeEn := Bool(false)
  
  // shorthands for tag/valid data for current read request
  val currentReqEntry = io.tagPortA.dataOut
  val currentReqLineValid = currentReqEntry(0)               // lowest bit = valid
  val currentReqLineTag = currentReqEntry(tagBitCount, 1)    // other bits = tag
  
  // shorthands for current write request
  val currentWriteReq = io.externalIF.writePort.writeReq.bits
  val currentWriteReqInd = currentWriteReq(depthBitCount-1, 0)
  val currentWriteReqTag = currentWriteReq(addrBits-1, depthBitCount)
  val currentWriteReqData = io.externalIF.writePort.writeData
  
  // defaults for tag port B, dedicated to write requests
  io.tagPortB.addr := currentWriteReqInd
   
  // shorthands for tag/valid data for current write request
  val currentWriteReqEntry = io.tagPortB.dataOut
  val currentWriteReqLineValid = currentWriteReqEntry(0)
  val currentWriteReqLineTag = currentWriteReqEntry(tagBitCount, 1)
  
  // shorthands for current memory read response
  val currentMemResp = io.externalIF.memRead.memResp.bits
  
  // read request handling
  val prevReadRequestReg = Reg(next = currentReq) // for driving readRespInd next cycle
  val enableReadRespReg = Reg(init = Bool(false))
  // data port A is dedicated to read requests
  io.dataPortA.addr := currentReqInd
  io.dataPortA.dataIn := currentMemResp // for read miss replacement
  io.dataPortA.writeEn := Bool(false)
  // data port B is dedicated to write requests
  io.dataPortB.addr := currentWriteReqInd
  io.dataPortB.dataIn := currentWriteReqData
  io.dataPortB.writeEn := Bool(false)
  
  // drive read response outputs
  io.externalIF.readPort.readResp.bits := io.dataPortA.dataOut
  io.externalIF.readPort.readResp.valid := enableReadRespReg
  io.externalIF.readPort.readRespInd := prevReadRequestReg
  
  // drive default outputs
  io.externalIF.cacheActive := (state === sActive)
  io.externalIF.writePort.writeReq.ready := Bool(false)  // whether cache can accept a write req
  io.externalIF.memWrite.memWriteReq.valid := Bool(false) // no write request to main mem
  io.externalIF.memWrite.memWriteReq.bits := currentWriteReq  // default write miss addr
  io.externalIF.memWrite.memWriteData := io.externalIF.writePort.writeData     // mem write request data comes right from the input
  io.externalIF.memRead.memReq.valid := Bool(false)    // no read request to main mem
  io.externalIF.memRead.memReq.bits := currentReq
  io.externalIF.readPort.readReq.ready := Bool(false)   // whether cache can accept a read req
  io.externalIF.memRead.memResp.ready := Bool(false)   // whether cache can accept a mem resp
  
  // drive outputs for counters
  io.externalIF.readCount := readCount         // total reads so far (hits = total - misses)
  io.externalIF.readMissCount := readMissCount         // total read misses so far
  io.externalIF.writeCount := writeCount     // total writes so far
  io.externalIF.writeMissCount := writeMissCount   // total write misses so far
  
  // default next-values for registers
  enableReadRespReg := Bool(false)
  
  when (state === sInit)
  {
    // unset all valid bit in cache at init time
    // TODO FPGA mem can be initialized at config time, this state
    // can be removed
    
    // reset every valid bit in the cache
    io.tagPortA.writeEn := Bool(true)
    io.tagPortA.dataIn := UInt(0)
    io.tagPortA.addr := initCtr
    
    // increment the initialization counter
    initCtr := initCtr + UInt(1)
    
    // go to sActive when all blocks initialized
    when (initCtr === UInt(depth-1)) { state := sActive}
  }
  .elsewhen (state === sInitCacheFlush)
  {
    // this state only exists to "prefetch" the line 0 content from cache data mem
    io.dataPortA.addr := initCtr
    initCtr := initCtr + UInt(1)
    state := sCacheFlush
  }
  .elsewhen (state === sCacheFlush)
  {
    // use initCtr as cache mem read address
    // data will be available next cycle
    io.dataPortA.addr := initCtr
    
    
    when (io.externalIF.memWrite.memWriteReq.ready)
    {
      initCtr := initCtr + UInt(1)
      val fetchedInd = initCtr - UInt(1)
      // read tag on port A
      // tag reads are immediately available
      io.tagPortA.addr := fetchedInd
      
      io.externalIF.memWrite.memWriteReq.valid := currentReqLineValid
      io.externalIF.memWrite.memWriteReq.bits := Cat(currentReqLineTag, fetchedInd)
      io.externalIF.memWrite.memWriteData := io.dataPortA.dataOut
      
      // go to sActive when all blocks flushed
      // when initCtr is 0 (overflow) we have reached the
      // last block, since we read the index initCtr-1
      when (initCtr === UInt(0)) { state := sActive}
    }
  }
  .elsewhen (state === sActive)
  {
    when (io.externalIF.flushCache)
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
      io.externalIF.writePort.writeReq.ready := io.externalIF.memWrite.memWriteReq.ready

      when (io.externalIF.writePort.writeReq.valid)
      {
        when (currentWriteReqTag === currentWriteReqLineTag & currentWriteReqLineValid)
        {
          // cache write hit
          // keep statistics for writes
          writeCount := writeCount + UInt(1)
          
          // update data in BRAM
          io.dataPortB.writeEn := Bool(true)
        }
        .elsewhen (io.externalIF.memWrite.memWriteReq.ready)
        {
          // cache write miss, issue as main memory write request
          io.externalIF.memWrite.memWriteReq.valid := Bool(true)
          // keep statistics for writes
          writeCount := writeCount + UInt(1)
          writeMissCount := writeMissCount + UInt(1)
        }
      }

      // read port stuff ------------------------
      // if something pending on the input queue
      when (io.externalIF.readPort.readReq.valid)
      {
        // if valid and tag match and space on output queue
        when (currentReqTag === currentReqLineTag & currentReqLineValid & io.externalIF.readPort.readResp.ready)
        {
          // cache hit!
          readCount := readCount + UInt(1)
          
          // read from input queue
          io.externalIF.readPort.readReq.ready := Bool(true)
          
          // enable write to output FIFO next cycle
          enableReadRespReg := Bool(true)
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
    io.externalIF.writePort.writeReq.ready := io.externalIF.memWrite.memWriteReq.ready
    
    when (io.externalIF.writePort.writeReq.valid)
    {
      // pending writes to be serviced
      when (currentWriteReqTag === currentWriteReqLineTag & currentWriteReqLineValid)
      {
        // cache write hit
        // keep statistics for writes
        writeCount := writeCount + UInt(1)
        
        // update data in BRAM
        io.dataPortB.writeEn := Bool(true)
      }
      .elsewhen (io.externalIF.memWrite.memWriteReq.ready)
      {
        // cache write miss, issue as write request
        io.externalIF.memWrite.memWriteReq.valid := Bool(true)
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
    io.externalIF.memRead.memReq.valid := Bool(true)
    // go to sEvictWrite state, figure out cold cacheline misses there
    state := sEvictWrite
  }
  .elsewhen (state === sEvictWrite)
  {
    when (io.externalIF.memWrite.memWriteReq.ready)
    {
      // write the evicted read data if it was valid
      io.externalIF.memWrite.memWriteReq.valid := currentReqLineValid
      // evicted address = line tag + current index
      io.externalIF.memWrite.memWriteReq.bits := Cat(currentReqLineTag, currentReqInd)
      // read evicted data from BRAM
      io.externalIF.memWrite.memWriteData := io.dataPortA.dataOut
      state := sCacheFill
    }
  }
  .elsewhen (state === sCacheFill)
  {
    // cache is waiting for a pending miss to be served from main mem
    // pop response from queue
    io.externalIF.memRead.memResp.ready := Bool(true)
   
    // check to see if any responses from memory are pending
    when (io.externalIF.memRead.memResp.valid)
    {
      // write returned data to cache through portA
      io.dataPortA.writeEn := Bool(true)
      
      // fix tag and valid bits by enabling tag write
      io.tagPortA.writeEn := Bool(true)
      
      // go back to active state
      state := sActive
    }
  }  
}
