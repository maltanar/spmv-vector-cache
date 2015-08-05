package SpMVAccel

import Chisel._
import TidbitsOCM._
import TidbitsDMA._

class NoWMVectorCache(val p: SpMVAccelWrapperParams) extends Module {
  val io = new SinglePortCacheIF(p)
  val pOCM = new OCMParameters( p.ocmDepth*p.opWidth, p.opWidth, p.opWidth, 2,
                                p.ocmReadLatency)

  // useful shorthands/definitions
  val lineSize = p.opWidth
  val addrBits = if (p.enableCMS) p.ptrWidth-1 else p.ptrWidth
  val depth = p.ocmDepth
  val indBitCount = log2Up(depth)
  val tagBitCount = addrBits - indBitCount
  def isStartOfRow(x: UInt): Bool = {x(p.ptrWidth-1)}
  def cacheTag(x: UInt): UInt = {x(indBitCount+tagBitCount-1, indBitCount)}
  def cacheInd(x: UInt): UInt = {x(indBitCount-1, 0)}
  def cacheAddr(tag: UInt , ind: UInt): UInt = {Cat(tag, ind)}

  // needs to be tagBits + 1 - wide (for the line valid bit)
  val tagMem = Module(new WMTagRAM(tagBitCount+1, p.ocmDepth)).io
  val dataMem = Module(if (p.ocmPrebuilt) new OnChipMemory(pOCM, p.ocmName) else
                  new AsymDualPortRAM(pOCM)).io

  val ddr = io.mem
  val tagPortR = tagMem
  val dataPortR = dataMem.ports(0)
  val dataPortW = dataMem.ports(1)

  // registers for keeping statistics
  val regReadMissCount = Reg(init = UInt(0, 32))

  // in-flight DDR write tracking logic
  def txn[T <: Data](intf: DecoupledIO[T]): Bool = {intf.ready & intf.valid}
  val regPendingWrites = Reg(init = UInt(0, 4))
  // always ready to accept write responses
  ddr.memWrRsp.ready := Bool(true)
  val wReqTxn = txn(ddr.memWrReq)
  val wRspTxn = txn(ddr.memWrRsp)
  val noPendingWrites = (regPendingWrites === UInt(0))
  // keep track of # DDR writes in flight
  when(wReqTxn & !wRspTxn) {regPendingWrites := regPendingWrites + UInt(1)}
  .elsewhen(!wReqTxn & wRspTxn) {regPendingWrites := regPendingWrites - UInt(1)}

  // cache read request shorthands
  val rdReqValid = io.read.req.valid
  val rdReqTag = cacheTag(io.read.req.bits)
  val rdReqInd = cacheInd(io.read.req.bits)
  val rdReqRowStart = isStartOfRow(io.read.req.bits)

  // cache write request shorthands
  val wrReqValid = io.write.req.valid
  val wrReqTag = cacheTag(io.write.req.bits.id)
  val wrReqInd = cacheInd(io.write.req.bits.id)
  val wrReqData = io.write.req.bits.data

  // registered version of the cache read request
  val regRdReqValid = Reg(next = rdReqValid)
  val regRdReqTag = Reg(next = rdReqTag)
  val regRdReqInd = Reg(next = rdReqInd)
  val regRdReqRowStart = Reg(next = rdReqRowStart)

  // tag access for read port
  tagPortR.req.addr := rdReqInd
  // writeEn & writeData are used during read miss replacement
  tagPortR.req.writeEn := Bool(false)
  tagPortR.req.writeData := Cat(regRdReqTag, Bits("b1"))

  // data access for read port
  dataPortR.req.addr := rdReqInd
  // writeEn & writeData are used during read miss replacement
  dataPortR.req.writeEn := Bool(false)
  dataPortR.req.writeData := ddr.memRdRsp.bits.readData

  // data access for write port
  dataPortW.req.addr := wrReqInd
  dataPortW.req.writeEn := wrReqValid
  dataPortW.req.writeData := wrReqData

  // shorthands for the returned read data
  val rdCacheTag = tagPortR.rsp.readData(tagBitCount, 1)
  // lowest bit of tag storage is used as valid indicator
  val rdCacheValid = tagPortR.rsp.readData(0)
  val rdCacheData = dataPortR.rsp.readData
  val rdTagMatch = (rdCacheTag === regRdReqTag)

  // read hit: valid request, cacheline valid and tag matches
  val readHit = regRdReqValid & rdTagMatch & rdCacheValid
  // read miss: valid request, but either invalid cacheline or tag mismatch
  val readMiss = regRdReqValid & (!rdTagMatch | !rdCacheValid)


  // default outputs
  io.done := Bool(false)
  // statistics
  io.readCount := Counter32Bit(txn(io.read.req))
  io.writeCount := Counter32Bit(txn(io.write.req))
  io.conflictMissCount := Counter32Bit(txn(ddr.memWrReq))
  io.readMissCount := regReadMissCount
  io.writeMissCount := UInt(0)  // this variant can have no write misses

  // cache read port
  io.read.req.ready := Bool(false)
  io.read.rsp.valid := Bool(false)
  io.read.rsp.bits := rdCacheData  // data read from cache

  // cache write port
  io.write.req.ready := Bool(true)  // always ready to accept writes
  // write completion, 1 cycle after write request was valid
  io.writeComplete := Reg(next = wrReqValid)

  // DDR ports
  val opBytes = UInt(p.opWidth/8)
  val vecBase = io.base
  val missLocation = cacheAddr(regRdReqTag, regRdReqInd)
  val evictLocation = cacheAddr(rdCacheTag, regRdReqInd)
  val evictData = rdCacheData
  // DDR read requests
  ddr.memRdReq.valid := Bool(false)
  ddr.memRdReq.bits.driveDefaults()
  ddr.memRdReq.bits.numBytes := opBytes
  ddr.memRdRsp.ready := Bool(false)
  // the only DDR read request is to the missed index
  ddr.memRdReq.bits.addr := vecBase + missLocation*opBytes
  // DDR write requests
  ddr.memWrReq.valid := Bool(false)
  ddr.memWrReq.bits.driveDefaults()
  ddr.memWrReq.bits.numBytes := opBytes
  ddr.memWrReq.bits.isWrite := Bool(true)
  // the only DDR write request is for the evicted location
  ddr.memWrReq.bits.addr := vecBase + evictLocation*opBytes

  // DDR write data
  ddr.memWrDat.valid := Bool(false)
  // the only DDR write data is the evicted data
  ddr.memWrDat.bits := evictData

  // cache control state machine
  val sActive :: sFill :: sFlush :: sDone :: sReadMiss1 :: sReadMiss2 :: sReadMiss3 :: sColdMiss :: Nil = Enum(UInt(), 8)
  val regState = Reg(init = UInt(sActive))
  io.cacheState := regState
  // register for fill/flush line counting
  val regCacheInd = Reg(init = UInt(0, 32))

  // "transaction indicator" to avoid repeated read results on a hit
  // (due to handshaking latency caused by the BRAM tag read)
  val regTxnInd = Reg(next = io.read.req.valid & io.read.req.ready)

  // use the mem wr req and data channels in lockstep
  val canDoExtWrite = ddr.memWrReq.ready & ddr.memWrDat.ready
  val canDoExtRead = ddr.memRdReq.ready

  switch(regState) {
    is(sActive) {
      regCacheInd := UInt(0)
      when (io.startWrite) {
        regState := sFlush
        // prefetch tag&data for line 0
        tagPortR.req.addr := UInt(0)
        dataPortR.req.addr := UInt(0)
      }
      .elsewhen (io.startInit) {regState := sFill}
      .elsewhen(readMiss) {
        if(p.enableCMS) {
          regState := Mux(regRdReqRowStart, sColdMiss, sReadMiss1)
        } else {regState := sReadMiss1}
      } .otherwise {
        // regular cache activity
        // only pass through read hits with the transaction indicator
        // TODO will response stalls affect correctness here?
        io.read.rsp.valid := readHit & regTxnInd
        io.read.req.ready := io.read.rsp.ready & readHit
      }
    }

    is(sFill) {
      // initialize cache by writing invalid to all tag bits
      when(regCacheInd === UInt(p.ocmDepth)) {regState := sDone}
      .otherwise {
        tagPortR.req.writeEn := Bool(true)
        tagPortR.req.addr := regCacheInd
        tagPortR.req.writeData := UInt(0)
        regCacheInd := regCacheInd + UInt(1)
      }
    }

    is(sFlush) {
      // flush cache
      tagPortR.req.addr := regCacheInd
      dataPortR.req.addr := regCacheInd

      val allLinesScanned = (regCacheInd === UInt(p.ocmDepth))

      when(allLinesScanned) {
        // wait til all pending writes are finished before sDone
        when(noPendingWrites) {regState := sDone}
      } .otherwise {
        when(canDoExtWrite) {
          val fetchedIndex = regCacheInd(indBitCount-1, 0)
          val flushLocation = cacheAddr(rdCacheTag, fetchedIndex)
          ddr.memWrDat.valid := rdCacheValid
          ddr.memWrDat.bits := rdCacheData
          ddr.memWrReq.valid := rdCacheValid
          ddr.memWrReq.bits.addr := vecBase + flushLocation*opBytes

          regCacheInd := regCacheInd + UInt(1)
          tagPortR.req.addr := regCacheInd + UInt(1)
          dataPortR.req.addr := regCacheInd + UInt(1)
        }
      }
    }

    is(sDone) {
      io.done := Bool(true)
      when(!io.startWrite & !io.startInit) {regState := sActive}
    }

    is(sColdMiss) {
      when(canDoExtWrite) {
        regReadMissCount := regReadMissCount + UInt(1)
        // update cache data and tag
        tagPortR.req.writeEn := Bool(true)
        dataPortR.req.writeEn := Bool(true)
        // special for cold miss handling: write 0 as cache data
        dataPortR.req.writeData := UInt(0)
        regState := sReadMiss3
      }
    }

    is(sReadMiss1) {
      // wait until all pending writes are complete before proceeding with
      // read miss handling, plus place on the read/write queues
      // TODO take care of these in separate states?
      when(noPendingWrites & canDoExtRead & canDoExtWrite) {
        ddr.memRdReq.valid := Bool(true) // load the missing index
        // write request only emitted on conflict miss (different valid tag)
        ddr.memWrReq.valid := rdCacheValid
        ddr.memWrDat.valid := rdCacheValid
        // count miss
        regReadMissCount := regReadMissCount + UInt(1)
        regState := sReadMiss2
      }
    }

    is(sReadMiss2) {
      // wait for read response
      ddr.memRdRsp.ready := Bool(true)

      when(ddr.memRdRsp.valid) {
        // update cache data and tag
        tagPortR.req.writeEn := Bool(true)
        dataPortR.req.writeEn := Bool(true)
        regState := sReadMiss3
      }
    }

    is(sReadMiss3) {
      // delay one more cycle to let tag change propagate (get a cache hit)
      regState := sActive
    }
  }
}
