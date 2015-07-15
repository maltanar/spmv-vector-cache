package SpMVAccel

import Chisel._
import TidbitsOCM._
import TidbitsStreams._


class InterleavedReduceOCM(val p: SpMVAccelWrapperParams) extends Module {
  val pOCM = new OCMParameters( p.ocmDepth*p.opWidth, p.opWidth, p.opWidth, 2,
                                p.ocmReadLatency)
  val opType = UInt(width=p.opWidth)
  val idType = UInt(width=p.ptrWidth)
  val syncOpType = new SemiringOperands(p.opWidth)
  val io = new Bundle {
    val operands = Decoupled(new OperandWithID(p.opWidth, p.ptrWidth)).flip
    val mcif = new OCMControllerIF(pOCM)
  }

  // instantiate OCM for storing contexts
  val contextStore = Module(new OCMAndController(pOCM, p.ocmName, p.ocmPrebuilt)).io
  contextStore.mcif <> io.mcif
  val loadPort = contextStore.ocmUser(0)
  val savePort = contextStore.ocmUser(1)
  loadPort.req.writeEn := Bool(false)
  loadPort.req.writeData := UInt(0)

  val adder = Module(p.makeAdd())

  // TODO is this +1 necessary? replace with ocmWriteLatency?
  lazy val rawLatency = adder.latency + p.ocmReadLatency + 1

  val guard = Module(new HazardGuard(p.opWidth, p.idWidth, rawLatency)).io
  guard.streamIn <> io.operands

  val hazardFreeOps = guard.streamOut

  // TODO parametrize depths
  val opQ = Module(new Queue(opType, 4)).io
  val idQ = Module(new Queue(idType, 4)).io
  opQ.enq.bits := hazardFreeOps.bits.data
  idQ.enq.bits := hazardFreeOps.bits.id
  loadPort.req.addr := hazardFreeOps.bits.id
  // shift register to determine load completion
  val loadValid = ShiftRegister(hazardFreeOps.valid, p.ocmReadLatency)
  opQ.enq.valid := hazardFreeOps.valid
  idQ.enq.valid := hazardFreeOps.valid


  // join operans for addition
  val fxn = {(a: UInt, b: UInt) => SemiringOperands(p.opWidth, a, b)}
  val addOpJoin = Module(new StreamJoin(opType, opType, syncOpType, fxn)).io
  addOpJoin.inA <> opQ.deq
  addOpJoin.inB.valid := loadValid
  addOpJoin.inB.bits := loadPort.rsp.readData
  addOpJoin.out <> adder.io.in

  // save adder output into contextStore (addr given by idQ)
  savePort.req.writeEn := adder.io.out.valid & idQ.deq.valid
  savePort.req.addr := idQ.deq.bits
  savePort.req.writeData := adder.io.out.bits

  // default outputs
  adder.io.out.ready := Bool(false)
  idQ.deq.ready := Bool(false)

  // TODO enable input
  // TODO add counters?
}
