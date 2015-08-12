package SpMVAccel

import Chisel._
import TidbitsOCM._
import TidbitsDMA._
import TidbitsStreams._

class SearchableQueue(w: Int, entries: Int) extends Module {
  val io = new Bundle {
    val enq = Decoupled(UInt(width=w)).flip
    val deq = Decoupled(UInt(width=w))
    val search = UInt(INPUT, width=w)
    val found = Bool(OUTPUT)
    val count = UInt(OUTPUT, width = log2Up(entries+1))
  }
  // mostly copied from Chisel Queue, with a few modifications:
  // - vector of registers instead of Mem, to expose all outputs

  val ram = Vec.fill(entries) { Reg(init = UInt(0, w)) }
  val ramValid = Vec.fill(entries) { Reg(init = Bool(false)) }

  val enq_ptr = Counter(entries)
  val deq_ptr = Counter(entries)
  val maybe_full = Reg(init=Bool(false))

  val ptr_match = enq_ptr.value === deq_ptr.value
  val empty = ptr_match && !maybe_full
  val full = ptr_match && maybe_full

  val do_enq = io.enq.ready && io.enq.valid
  val do_deq = io.deq.ready && io.deq.valid
  when (do_enq) {
    ram(enq_ptr.value) := io.enq.bits
    ramValid(enq_ptr.value) := Bool(true)
    enq_ptr.inc()
  }
  when (do_deq) {
    ramValid(deq_ptr.value) := Bool(false)
    deq_ptr.inc()
  }
  when (do_enq != do_deq) {
    maybe_full := do_enq
  }

  // <search logic>
  val hits = Vec.tabulate(entries) {i: Int => ram(i) === io.search & ramValid(i)}
  io.found := hits.exists({x:Bool => x})
  // </search logic>

  io.deq.valid := !empty
  io.enq.ready := !full
  io.deq.bits := ram(deq_ptr.value)

  val ptr_diff = enq_ptr.value - deq_ptr.value
  if (isPow2(entries)) {
    io.count := Cat(maybe_full && ptr_match, ptr_diff)
  } else {
    io.count := Mux(ptr_match,
                  Mux(maybe_full, UInt(entries), UInt(0)),
                  Mux(deq_ptr.value > enq_ptr.value,
                      UInt(entries) + ptr_diff, ptr_diff)
                    )
  }
}

class NBVectorCache(val p: SpMVAccelWrapperParams) extends Module {
  val io = new NBCacheIF(p)
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

  /////////////////////////// cache read port ///////////////////////////
  // cache read request shorthands
  val rdReqValid = io.read.req.valid
  val rdReqTag = cacheTag(io.read.req.bits.id)
  val rdReqInd = cacheInd(io.read.req.bits.id)
  val rdReqRowStart = isStartOfRow(io.read.req.bits.id)
  val rdReqOp = io.read.req.bits.data

  val queueHasRoom = Bool()

  // registered version of the cache read request
  // TODO these should be ShiftRegs if tag read has higher latency
  val regRdReqValid = Reg(next = rdReqValid & queueHasRoom)
  val regRdReqTag = Reg(next = rdReqTag)
  val regRdReqInd = Reg(next = rdReqInd)
  val regRdReqRowStart = Reg(next = rdReqRowStart)
  val regRdReqOp = Reg(next = rdReqOp)

  // tag + data access for read port
  tagPortR.req.addr := rdReqInd
  dataPortR.req.addr := rdReqInd

  // shorthands for the returned read data
  val rdCacheTag = tagPortR.rsp.readData(tagBitCount, 1)
  // lowest bit of tag storage is used as valid indicator
  val rdCacheValid = tagPortR.rsp.readData(0)
  val rdCacheData = dataPortR.rsp.readData

  // tag response queue
  // TODO 2 is dependent on tag+data read latency (1) plus outstanding misses (1)?
  val tagRespType = new NBTagResponse(indBitCount, tagBitCount, lineSize)
  val qs: Int = 4
  val tagRespQ = Module(new Queue(tagRespType, qs)).io
  val qfree = UInt(qs)-tagRespQ.count
  tagRespQ.enq.valid := regRdReqValid
  io.read.req.ready := queueHasRoom
  tagRespQ.enq.bits.ind := regRdReqInd
  tagRespQ.enq.bits.reqCMS := regRdReqRowStart
  tagRespQ.enq.bits.reqTag := regRdReqTag
  tagRespQ.enq.bits.rspTag := rdCacheTag
  tagRespQ.enq.bits.rspValid := rdCacheValid
  tagRespQ.enq.bits.rspData := rdCacheData
  tagRespQ.enq.bits.opData := regRdReqOp

  val headValid = tagRespQ.deq.valid
  val head = tagRespQ.deq.bits

  val readHit = headValid & head.rspValid & (head.reqTag === head.rspTag)
  val readMiss = headValid & !(head.rspValid & (head.reqTag === head.rspTag))

  val regUnhandledMiss = Reg(init = tagRespType)
  val regHandledMiss = Reg(init = tagRespType)

  // pending miss queue
  val pendingDDRMissQ = Module(new Queue(tagRespType, p.maxMiss)).io
  pendingDDRMissQ.enq.bits := regUnhandledMiss
  pendingDDRMissQ.enq.valid := Bool(false)
  pendingDDRMissQ.deq.ready := Bool(false)

  val canAcceptDDRMiss = pendingDDRMissQ.enq.ready
  val pendingDDRMissHead = pendingDDRMissQ.deq.bits

  // cache read response
  io.read.rsp.bits.data := Cat(head.rspData, head.opData)
  io.read.rsp.bits.id := cacheAddr(head.reqTag, head.ind)

  // read miss replacement
  val regReadMissData = Reg(init = UInt(0, lineSize))
  tagPortR.req.writeData := Cat(regHandledMiss.reqTag, Bits("b1"))
  dataPortR.req.writeData := regReadMissData
  // write enables will be set by state machine
  tagPortR.req.writeEn := Bool(false)
  dataPortR.req.writeEn := Bool(false)

  /////////////////////////// cache write port ///////////////////////////
  // cache write request shorthands
  val wrReqValid = io.write.req.valid
  val wrReqTag = cacheTag(io.write.req.bits.id)
  val wrReqInd = cacheInd(io.write.req.bits.id)
  val wrReqData = io.write.req.bits.data

  // data access for write port
  dataPortW.req.addr := wrReqInd
  dataPortW.req.writeEn := wrReqValid
  dataPortW.req.writeData := wrReqData

  // cache write port
  io.write.req.ready := Bool(true)  // always ready to accept writes
  // write completion, 1 cycle after write request was valid
  io.writeComplete := Reg(next = wrReqValid)

  // default outputs
  io.done := Bool(false)
  // statistics
  io.readCount := Counter32Bit(txn(io.read.req))
  io.writeCount := Counter32Bit(txn(io.write.req))
  io.conflictMissCount := Counter32Bit(txn(ddr.memWrReq))
  io.readMissCount := regReadMissCount
  io.writeMissCount := UInt(0)  // this variant can have no write misses

  // DDR ports
  val opBytes = UInt(p.opWidth/8)
  val vecBase = io.base
  val missLocation = cacheAddr(regUnhandledMiss.reqTag, regUnhandledMiss.ind)
  val evictLocation = cacheAddr(regUnhandledMiss.rspTag, regUnhandledMiss.ind)
  val evictData = regUnhandledMiss.rspData
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


  /////////////////////////////////////////////////////////////////////////////
  // in-flight DDR write tracking logic
  def txn[T <: Data](intf: DecoupledIO[T]): Bool = {intf.ready & intf.valid}
  // always ready to accept write responses
  ddr.memWrRsp.ready := Bool(true)
  val wReqTxn = txn(ddr.memWrReq)
  val wRspTxn = txn(ddr.memWrRsp)
  val writeTxnQ = Module(new SearchableQueue(addrBits, 8)).io

  writeTxnQ.enq.bits := evictLocation
  writeTxnQ.enq.valid := Bool(false)
  writeTxnQ.deq.ready := wRspTxn
  writeTxnQ.search := missLocation

  val regPendingWrites = Reg(init = UInt(0, 4))
  val noPendingWrites = (regPendingWrites === UInt(0))
  // keep track of # DDR writes in flight
  when(wReqTxn & !wRspTxn) {regPendingWrites := regPendingWrites + UInt(1)}
  .elsewhen(!wReqTxn & wRspTxn) {regPendingWrites := regPendingWrites - UInt(1)}
  /////////////////////////////////////////////////////////////////////////////


  // cache control state machine
  val sActive :: sFill :: sFlush :: sDone :: sReadMiss1 :: sReadMiss2 :: sReadMiss3 :: sColdMiss :: Nil = Enum(UInt(), 8)
  val regState = Reg(init = UInt(sActive))
  io.cacheState := regState
  // register for fill/flush line counting
  val regCacheInd = Reg(init = UInt(0, 32))

  // use the mem wr req and data channels in lockstep
  val canDoExtWrite = ddr.memWrReq.ready & ddr.memWrDat.ready
  val canDoExtRead = ddr.memRdReq.ready

  queueHasRoom := (qfree > UInt(1))
  // regular cache activity
  tagRespQ.deq.ready := readHit & io.read.rsp.ready
  io.read.rsp.valid := readHit

  val needEvict = regUnhandledMiss.rspValid
  val writeOK = !needEvict | (writeTxnQ.enq.ready & canDoExtWrite)
  val readOK = canDoExtRead & canAcceptDDRMiss & !writeTxnQ.found

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
      .elsewhen (ddr.memRdRsp.valid) {regState := sReadMiss2}
      .elsewhen (readMiss & head.reqCMS) {
        // pull out the miss into register
        regUnhandledMiss := head
        tagRespQ.deq.ready := Bool(true)
        regState := sColdMiss
      }
      .elsewhen (readMiss & canDoExtRead & canAcceptDDRMiss) {
        // pull out the miss into register
        regUnhandledMiss := head
        tagRespQ.deq.ready := Bool(true)
        regState := sReadMiss1
      }
    }

    is(sColdMiss) {
      // cold miss does not need a DDR read, so we don't check readOK
      when(writeOK) {
        // write request only emitted on conflict miss (different valid tag)
        ddr.memWrReq.valid := needEvict
        ddr.memWrDat.valid := needEvict
        writeTxnQ.enq.valid := needEvict
        regReadMissCount := regReadMissCount + UInt(1)
        // special for cold miss handling: write 0 as cache data
        regReadMissData := UInt(0)
        regHandledMiss := regUnhandledMiss
        // prevent new tag reads
        // TODO must start more in advance if tag read latency > 1
        queueHasRoom := Bool(false)
        // proceed to tag and response write
        regState := sReadMiss3
      }
    }

    is(sReadMiss1) {
      when(readOK & writeOK) {
        ddr.memRdReq.valid := Bool(true) // load the missing index
        // write request only emitted on conflict miss (different valid tag)
        ddr.memWrReq.valid := needEvict
        ddr.memWrDat.valid := needEvict
        writeTxnQ.enq.valid := needEvict
        // count miss
        regReadMissCount := regReadMissCount + UInt(1)
        // put into miss queue
        pendingDDRMissQ.enq.valid := Bool(true)
        // back to serving requests
        regState := sActive
      }
    }

    is(sReadMiss2) {
      // wait for DDR read response
      ddr.memRdRsp.ready := Bool(true)
      when(ddr.memRdRsp.valid) {
        // save response to regReadMissData
        regReadMissData := ddr.memRdRsp.bits.readData
        // save the head of pending DDR miss to register
        regHandledMiss := pendingDDRMissHead
        // pop from pending miss queue
        pendingDDRMissQ.deq.ready := Bool(true)
        // prevent new tag reads
        // TODO must start more in advance if tag read latency > 1
        queueHasRoom := Bool(false)
        regState := sReadMiss3
      }
    }

    is(sReadMiss3) {
      // prevent regular cache hit activity + tag reads
      queueHasRoom := Bool(false)
      tagRespQ.deq.ready := Bool(false)

      // set addresses for tag and data write
      tagPortR.req.addr := regHandledMiss.ind
      dataPortR.req.addr := regHandledMiss.ind
      // make read response available
      io.read.rsp.valid := Bool(true)

      io.read.rsp.bits.data := Cat(regReadMissData, regHandledMiss.opData)
      io.read.rsp.bits.id := cacheAddr(regHandledMiss.reqTag, regHandledMiss.ind)

      when(io.read.rsp.ready) {
        // update cache data and tag
        tagPortR.req.writeEn := Bool(true)
        dataPortR.req.writeEn := Bool(true)
        regState := sActive
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
  }
}
