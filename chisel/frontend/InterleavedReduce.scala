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
    val enable = Bool(INPUT)
    val opCount = UInt(OUTPUT, width = 32)
    val hazardStalls = UInt(OUTPUT, width = 32)
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

  // TODO expose hazard count to parent
  val guard = Module(new HazardGuard(p.opWidth, p.idWidth, rawLatency)).io
  guard.streamIn <> io.operands
  io.hazardStalls := guard.hazardStalls

  val hazardFreeOps = guard.streamOut
  // uncomment to debug hazard-free op inputs
  /*when(hazardFreeOps.valid) {
    printf("OP id=%x data=%x\n", hazardFreeOps.bits.id, hazardFreeOps.bits.data)
  }*/

  // TODO parametrize depths
  val opQ = Module(new Queue(opType, 4)).io
  val idQ = Module(new Queue(idType, 4)).io
  opQ.enq.bits := hazardFreeOps.bits.data
  idQ.enq.bits := hazardFreeOps.bits.id
  loadPort.req.addr := hazardFreeOps.bits.id
  // shift register to determine load completion
  val loadValid = ShiftRegister(hazardFreeOps.valid, p.ocmReadLatency)
  opQ.enq.valid := hazardFreeOps.valid & idQ.enq.ready
  idQ.enq.valid := hazardFreeOps.valid & opQ.enq.ready
  hazardFreeOps.ready := opQ.enq.ready & idQ.enq.ready


  // join operans for addition
  val fxn = {(a: UInt, b: UInt) => SemiringOperands(p.opWidth, a, b)}
  val addOpJoin = Module(new StreamJoin(opType, opType, syncOpType, fxn)).io
  addOpJoin.inA <> opQ.deq
  addOpJoin.inB.valid := loadValid
  addOpJoin.inB.bits := loadPort.rsp.readData
  //addOpJoin.inB.bits.ready
  addOpJoin.out <> adder.io.in
  // uncomment to debug adder inputs
  //when(adder.io.in.valid) {printf("ADD %x + %x\n", adder.io.in.bits.first, adder.io.in.bits.second)}

  // save adder output into contextStore (addr given by idQ)
  val resultValid = adder.io.out.valid & idQ.deq.valid
  savePort.req.writeEn := resultValid
  savePort.req.addr := idQ.deq.bits
  savePort.req.writeData := adder.io.out.bits

  // use enable as backpressure signal in several places
  // idQ and adder streams proceed in lockstep
  adder.io.out.ready := io.enable &  idQ.deq.valid
  idQ.deq.ready := io.enable & adder.io.out.valid

  // register for counting completed operations
  val regOpCount = Reg(init = UInt(0, 32))
  io.opCount := regOpCount
  // TODO use "write complete" signal when available -- may be write latency
  when (!io.enable) {regOpCount := UInt(0)}
  .otherwise {
    when (resultValid) {regOpCount := regOpCount + UInt(1)}
  }

  // TODO add more counters?
}
