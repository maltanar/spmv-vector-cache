package SpMVAccel

import Chisel._
import TidbitsOCM._
import TidbitsDMA._

// TODO add queue on write data to break dependencies on wrreq.ready

class CacheController(p: SpMVAccelWrapperParams, pOCM: OCMParameters) extends Module {
  val lineSize = p.opWidth
  val addrBits = p.ptrWidth
  val depth = p.ocmDepth
  val io = new Bundle {
    // ports towards processor and main mem
    val externalIF = new SinglePortCacheIF(p)

    // ports towards the data memory
    val dataPortA = new OCMMasterIF(lineSize, lineSize, addrBits) // for read reqs
    val dataPortB = new OCMMasterIF(lineSize, lineSize, addrBits) // for write reqs

    // ports towards the tag memory
    val tagPortA = new CacheDataReadWritePort(lineSize, addrBits).flip  // for read reqs
    val tagPortB = new CacheDataReadPort(lineSize, addrBits).flip  // for write reqs
  }

  val vecBase = io.externalIF.base

  // registers for read/miss counts
  val readCount = Reg(init = UInt(0, 32))
  val writeCount = Reg(init = UInt(0, 32))
  val readMissCount = Reg(init = UInt(0, 32))
  val writeMissCount = Reg(init = UInt(0, 32))

  // memory for keeping cache state
  val depthBitCount = log2Up(depth)
  val tagBitCount = addrBits - depthBitCount

  val sInit :: sActive :: sFinishPendingWrites :: sIssueReadReq :: sEvictWrite :: sCacheFill :: sInitCacheFlush :: sCacheFlush :: sDone :: Nil = Enum(UInt(), 9)
  val state = Reg(init = UInt(sActive))
  val initCtr = Reg(init = UInt(0, depthBitCount))

  // shorthands for current read request
  val currentReq = io.externalIF.read.req.bits
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
  val currentWriteReq = io.externalIF.write.req.bits.id
  val currentWriteReqInd = currentWriteReq(depthBitCount-1, 0)
  val currentWriteReqTag = currentWriteReq(addrBits-1, depthBitCount)
  val currentWriteReqData = io.externalIF.write.req.bits.data

  // defaults for tag port B, dedicated to write requests
  io.tagPortB.addr := currentWriteReqInd

  // shorthands for tag/valid data for current write request
  val currentWriteReqEntry = io.tagPortB.dataOut
  val currentWriteReqLineValid = currentWriteReqEntry(0)
  val currentWriteReqLineTag = currentWriteReqEntry(tagBitCount, 1)

  // shorthands for current memory read response bits
  val currentMemResp = io.externalIF.mem.memRdRsp.bits.readData

  // read request handling
  val prevReadRequestReg = Reg(next = currentReq) // for driving readRespInd next cycle
  val enableReadRespReg = Reg(init = Bool(false))
  // data port A is dedicated to read requests
  io.dataPortA.req.addr := currentReqInd
  io.dataPortA.req.writeData := currentMemResp // for read miss replacement
  io.dataPortA.req.writeEn := Bool(false)
  // data port B is dedicated to write requests
  io.dataPortB.req.addr := currentWriteReqInd
  io.dataPortB.req.writeData := currentWriteReqData
  io.dataPortB.req.writeEn := Bool(false)

  // drive read response outputs
  io.externalIF.read.rsp.bits := io.dataPortA.rsp.readData
  io.externalIF.read.rsp.valid := enableReadRespReg

  // drive default outputs
  io.externalIF.writeComplete := Bool(false)
  io.externalIF.done := Bool(false)

  val opBytes = UInt(p.opWidth/8)
  io.externalIF.write.req.ready := Bool(false)  // whether cache can accept a write req
  io.externalIF.mem.memWrReq.valid := Bool(false) // no write request to main mem
  io.externalIF.mem.memWrReq.bits.driveDefaults()
  io.externalIF.mem.memWrReq.bits.isWrite := Bool(true)
  io.externalIF.mem.memWrReq.bits.numBytes := opBytes
  io.externalIF.mem.memWrReq.bits.addr := vecBase + currentWriteReq*opBytes // default write miss addr

  io.externalIF.mem.memWrDat.valid := io.externalIF.mem.memWrReq.valid
  io.externalIF.mem.memWrDat.bits := io.externalIF.write.req.bits.data   // mem write request data comes right from the input


  io.externalIF.mem.memRdReq.valid := Bool(false)    // no read request to main mem
  io.externalIF.mem.memRdReq.bits.driveDefaults()
  io.externalIF.mem.memRdReq.bits.numBytes := opBytes
  io.externalIF.mem.memRdReq.bits.addr := vecBase + currentReq*opBytes
  io.externalIF.read.req.ready := Bool(false)   // whether cache can accept a read req
  io.externalIF.mem.memRdRsp.ready := Bool(false)   // whether cache can accept a mem resp

  // drive outputs for counters
  io.externalIF.readCount := readCount         // total reads so far (hits = total - misses)
  io.externalIF.readMissCount := readMissCount         // total read misses so far
  io.externalIF.writeCount := writeCount     // total writes so far
  io.externalIF.writeMissCount := writeMissCount   // total write misses so far

  // default next-values for registers
  enableReadRespReg := Bool(false)

  val mp = io.externalIF.mem
  val canDoExtWrite = mp.memWrReq.ready & mp.memWrDat.ready

  // write counter + response handling logic
  mp.memWrRsp.ready := Bool(true)
  val regPendingWrites = Reg(init = UInt(0, 32))

  def txn[T <: Data](intf: DecoupledIO[T]): Bool = {intf.ready & intf.valid}

  when (txn(mp.memWrReq) & !txn(mp.memWrRsp)) { regPendingWrites := regPendingWrites + UInt(1)}
  .elsewhen (!txn(mp.memWrReq) & txn(mp.memWrRsp)) { regPendingWrites := regPendingWrites - UInt(1)}

  when (state === sInit)
  {
    // unset all valid bit in cache at init time

    // reset every valid bit in the cache
    io.tagPortA.writeEn := Bool(true)
    io.tagPortA.dataIn := UInt(0)
    io.tagPortA.addr := initCtr

    // increment the initialization counter
    initCtr := initCtr + UInt(1)

    // go to sDone when all blocks initialized
    when (initCtr === UInt(depth-1)) { state := sDone}
  }
  .elsewhen (state === sInitCacheFlush)
  {
    // this state only exists to "prefetch" the line 0 content from cache data mem
    io.dataPortA.req.addr := initCtr
    initCtr := initCtr + UInt(1)
    state := sCacheFlush
  }
  .elsewhen (state === sCacheFlush)
  {
    // use initCtr as cache mem read address
    // data will be available next cycle
    io.dataPortA.req.addr := initCtr

    when (canDoExtWrite)
    {
      initCtr := initCtr + UInt(1)
      val fetchedInd = initCtr - UInt(1)
      // read tag on port A
      // tag reads are immediately available
      io.tagPortA.addr := fetchedInd

      mp.memWrReq.valid := currentReqLineValid
      mp.memWrReq.bits.addr := vecBase + opBytes*UInt(Cat(currentReqLineTag, fetchedInd))

      mp.memWrDat.bits := io.dataPortA.rsp.readData

      // go to sDone when all blocks flushed
      // when initCtr is 0 (overflow) we have reached the
      // last block, since we read the index initCtr-1
      when (initCtr === UInt(0)) { state := sDone}
    }
  }
  .elsewhen (state === sDone) {
    io.externalIF.done := Bool(true)
    when(!io.externalIF.startWrite & !io.externalIF.startInit) {
      state := sActive
    }
  }
  .elsewhen (state === sActive)
  {
    when (io.externalIF.startWrite)
    {
      // flush the cache
      state := sInitCacheFlush
      initCtr := UInt(0)
    } .elsewhen (io.externalIF.startInit) {
      state := sInit
      initCtr := UInt(0)
    }
    .otherwise
    {
      // no flush/init requested, cache ready to serve requests (no pending misses)

      // write port stuff ----------------------
      // write port dumps all misses straight to main mem, so it can
      // always accept as long as the main mem write port is available
      io.externalIF.write.req.ready := canDoExtWrite

      when (io.externalIF.write.req.valid)
      {
        when (currentWriteReqTag === currentWriteReqLineTag & currentWriteReqLineValid)
        {
          // cache write hit
          // keep statistics for writes
          writeCount := writeCount + UInt(1)

          // update data in BRAM
          io.dataPortB.req.writeEn := Bool(true)
          // signal write complete
          // hazard dequeue latency <-> write complete (next cycle)
          io.externalIF.writeComplete := Bool(true)
        }
        .elsewhen (canDoExtWrite)
        {
          // cache write miss, issue as main memory write request
          io.externalIF.mem.memWrReq.valid := Bool(true)
          // keep statistics for writes
          writeCount := writeCount + UInt(1)
          writeMissCount := writeMissCount + UInt(1)
          // signal write complete (even though this is not yet complete)
          // we atone for this by waiting until all writes have completed
          // when we get a read miss
          io.externalIF.writeComplete := Bool(true)
        }
      }

      // read port stuff ------------------------
      // if something pending on the input queue
      when (io.externalIF.read.req.valid)
      {
        // if valid and tag match and space on output queue
        when (currentReqTag === currentReqLineTag & currentReqLineValid & io.externalIF.read.rsp.ready)
        {
          // cache hit!
          readCount := readCount + UInt(1)

          // read from input queue
          io.externalIF.read.req.ready := Bool(true)

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
        // other cases are not explicitly covered (default values should suffice)
      }
    }
  }
  .elsewhen (state === sFinishPendingWrites)
  {
    // wait until no more pending writes before issuing the read request
    // to main memory -- we will get memory consistency problems otherwise
    when (regPendingWrites === UInt(0)) {state := sIssueReadReq}
  }
  .elsewhen (state === sIssueReadReq)
  {
    // enqueue the read miss
    io.externalIF.mem.memRdReq.valid := Bool(true)

    when(io.externalIF.mem.memRdReq.ready) {
      // go to sEvictWrite state, figure out cold cacheline misses there
      state := sEvictWrite
    }
  }
  .elsewhen (state === sEvictWrite)
  {
    when (canDoExtWrite)
    {
      // write the evicted read data if it was valid
      io.externalIF.mem.memWrReq.valid := currentReqLineValid
      // evicted address = line tag + current index
      io.externalIF.mem.memWrReq.bits.addr := vecBase+ opBytes*UInt(Cat(currentReqLineTag, currentReqInd))
      // read evicted data from BRAM
      io.externalIF.mem.memWrDat.bits := io.dataPortA.rsp.readData
      state := sCacheFill
    }
  }
  .elsewhen (state === sCacheFill)
  {
    // cache is waiting for a pending miss to be served from main mem
    // pop response from queue
    io.externalIF.mem.memRdRsp.ready := Bool(true)

    // check to see if any responses from memory are pending
    when (io.externalIF.mem.memRdRsp.valid)
    {
      // write returned data to cache through portA
      io.dataPortA.req.writeEn := Bool(true)

      // fix tag and valid bits by enabling tag write
      io.tagPortA.writeEn := Bool(true)

      // go back to active state
      state := sActive
    }
  }
}
