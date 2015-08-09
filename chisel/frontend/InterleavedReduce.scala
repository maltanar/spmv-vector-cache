package SpMVAccel

import Chisel._
import TidbitsOCM._
import TidbitsStreams._


class InterleavedReduceOCM(val p: SpMVAccelWrapperParams) extends Module {
  val pOCM = new OCMParameters( p.ocmDepth*p.opWidth, p.opWidth, p.opWidth, 2,
                                p.ocmReadLatency)
  val opType = UInt(width=p.opWidth)
  val idType = UInt(width=p.ptrWidth)
  val opWidthIdType = new OperandWithID(p.opWidth, p.ptrWidth)
  val opsAndId = new OperandWithID(2*p.opWidth, p.ptrWidth)
  val shadowType = new OperandWithID(1, p.ptrWidth)
  val syncOpType = new SemiringOperands(p.opWidth)

  val io = new Bundle {
    val enable = Bool(INPUT)
    val opCount = UInt(OUTPUT, width = 32)
    val hazardStalls = UInt(OUTPUT, width = 32)
    val operands = Decoupled(new OperandWithID(p.opWidth, p.ptrWidth)).flip
    val mcif = new OCMControllerIF(pOCM)
  }
  // useful functions for working with stream forks and joins
  val forkAll = {x: OperandWithID => x}
  val forkShadow = {x: OperandWithID => OperandWithID(UInt(0, width=1), x.id)}
  val forkId = {x: OperandWithID => x.id}
  val forkOp = {x: OperandWithID => x.data}
  def forkOps(x: OperandWithID) : SemiringOperands = {
    val opA = x.data(2*p.opWidth-1, p.opWidth)
    val opB = x.data(p.opWidth-1, 0)
    return SemiringOperands(p.opWidth, opA, opB)
  }
  val joinOpIdOp= {
    (a: OperandWithID, b: UInt) => OperandWithID(Cat(a.data, b), a.id)
  }
  val joinOpId = {(op: UInt, id: UInt) => OperandWithID(op, id)}
  val joinOpOp = {(a: UInt, b: UInt) => SemiringOperands(p.opWidth, a, b)}

  // instantiate OCM for storing contexts
  val contextStore = Module(new OCMAndController(pOCM, p.ocmName, p.ocmPrebuilt)).io
  contextStore.mcif <> io.mcif
  val loadPort = contextStore.ocmUser(0)
  val savePort = contextStore.ocmUser(1)
  loadPort.req.writeEn := Bool(false)
  loadPort.req.writeData := UInt(0)

  val adder = Module(p.makeAdd())

  val shadow = Module(new UniqueQueue(1, p.ptrWidth, p.issueWindow)).io
  val cacheEntry = Module(new StreamFork(opWidthIdType, opWidthIdType, shadowType,
                      forkAll, forkShadow)).io
  val opIdFork = Module(new StreamFork(opWidthIdType, idType, opType, forkId, forkOp)).io

  cacheEntry.in <> io.operands
  cacheEntry.outB <> shadow.enq
  cacheEntry.outA <> opIdFork.in


  val doLoad = opIdFork.outA.ready & opIdFork.outA.valid

  val loadValid = ShiftRegister(doLoad, p.ocmReadLatency)
  loadPort.req.addr := opIdFork.outA.bits

  val addOpJoin = Module(new StreamJoin(opType, opType, syncOpType, joinOpOp)).io
  addOpJoin.inA <> Queue(opIdFork.outB, 2)
  addOpJoin.inB.bits := loadPort.rsp.readData
  addOpJoin.inB.valid := loadValid

  addOpJoin.out <> adder.io.in

  val idQDepth = adder.latency + p.ocmReadLatency + 1
  val writeJoin = Module(new StreamJoin(opType, idType, opWidthIdType, joinOpId)).io
  writeJoin.inA <> adder.io.out
  writeJoin.inB <> Queue(opIdFork.outA, idQDepth)

  val doWrite = writeJoin.out.valid & io.enable
  writeJoin.out.ready := io.enable
  savePort.req.writeEn := doWrite
  savePort.req.writeData := writeJoin.out.bits.data
  savePort.req.addr := writeJoin.out.bits.id

  // TODO parametrize write latency
  val writeComplete = ShiftRegister(doWrite, 1)

  shadow.deq.ready := writeComplete

  // TODO reimplement or remove
  io.hazardStalls := UInt(0)

  // register for counting completed operations
  val regOpCount = Reg(init = UInt(0, 32))
  io.opCount := regOpCount
  // TODO use "write complete" signal when available -- may be write latency
  when (!io.enable) {regOpCount := UInt(0)}
  .otherwise {
    when (writeComplete) {regOpCount := regOpCount + UInt(1)}
  }
}
