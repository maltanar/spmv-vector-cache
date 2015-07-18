package SpMVAccel

import Chisel._
import TidbitsAXI._
import TidbitsStreams._
import TidbitsOCM._

class SpMVFrontend(val p: SpMVAccelWrapperParams) extends Module {
  val opType = UInt(width=p.opWidth)
  val idType = UInt(width=p.ptrWidth)
  val oidType = new OperandWithID(p.opWidth, p.ptrWidth)
  val syncOpType = new SemiringOperands(p.opWidth)
  val pOCM = new OCMParameters( p.ocmDepth*p.opWidth, p.opWidth, p.opWidth, 2,
                                p.ocmReadLatency)
  val io = new Bundle {
    // control
    val startInit = Bool(INPUT)
    val startRegular = Bool(INPUT)
    val startWrite = Bool(INPUT)
    // status
    val doneInit = Bool(OUTPUT)
    val doneRegular = Bool(OUTPUT)
    val doneWrite = Bool(OUTPUT)
    // TODO debug+profiling outputs
    // value inputs
    val numNZ = UInt(INPUT, width = 32)
    val numRows = UInt(INPUT, width = 32)
    // data exchange with backend
    val colPtrIn = Decoupled(UInt(width = p.ptrWidth)).flip
    val rowIndIn = Decoupled(UInt(width = p.ptrWidth)).flip
    val nzDataIn = Decoupled(UInt(width = p.opWidth)).flip
    val inputVecIn = Decoupled(UInt(width = p.opWidth)).flip
    val outputVecOut = Decoupled(UInt(width = p.opWidth))
  }
  // instantiate multiply operator
  val mul = Module(p.makeMul())
  // instantiate StreamDelta and StreamRepeatElem
  val deltaGenM = Module(new StreamDelta(p.ptrWidth))
  val deltaGen = deltaGenM.io
  deltaGen.samples <> io.colPtrIn
  val rptGenM = Module(new StreamRepeatElem(p.opWidth, p.ptrWidth))
  val rptGen = rptGenM.io
  rptGen.inElem <> io.inputVecIn
  rptGen.inRepCnt <> deltaGen.deltas

  // wire up multiplier inputs with StreamJoin
  val fxnM = {(a: UInt, b: UInt) => SemiringOperands(p.opWidth, a, b)}
  val mulOpJoin = Module(new StreamJoin(opType, opType, syncOpType, fxnM)).io
  mulOpJoin.inA <> rptGen.out
  mulOpJoin.inB <> io.nzDataIn
  mul.io.in <> mulOpJoin.out
  // uncomment to debug multiplier inputs
  /*
  when(mulOpJoin.inA.valid & mulOpJoin.inB.valid) {
    printf("MUL %x * %x\n", mulOpJoin.inA.bits, mulOpJoin.inB.bits)
  }
  */

  // add a queue to buffer multiplier results, in case of stalls
  // this will let the pipes continue a while longer
  // TODO parametrize queue size
  val productQ = Module(new Queue(UInt(width=p.opWidth), 16)).io
  productQ.enq <> mul.io.out
  // join products and rowind data to pass to reducer
  val fxnR = {(op: UInt, id: UInt) => OperandWithID(op, id)}
  val redJoin = Module(new StreamJoin(opType, idType, oidType, fxnR)).io
  redJoin.inA <> productQ.deq
  redJoin.inB <> io.rowIndIn

  // instantiate + connect InterleavedReduce
  // TODO parametrize construction (e.g instantiate cache instead)?
  val reducerM = Module(new InterleavedReduceOCM(p))
  val reducer = reducerM.io
  reducer.operands <> redJoin.out

  reducer.enable := io.startRegular
  // TODO expose more specific controls instead
  // OCM controller kicks off with init (fill) or write (dump)
  reducer.mcif.start := io.startInit | io.startWrite
  reducer.mcif.mode := Mux(io.startWrite, UInt(1), UInt(0))
  // set fill port to an always-zero stream
  // TODO make result vec. initial value customizable?
  reducer.mcif.fillPort.valid := Bool(true)
  reducer.mcif.fillPort.bits := UInt(0)
  // set up fill/dump start+range
  // may not need entire OCM, depends on numRows
  reducer.mcif.fillDumpStart := UInt(0)
  reducer.mcif.fillDumpCount := UInt(io.numRows)
  // connect OCMC dump port to result vec. output
  io.outputVecOut <> reducer.mcif.dumpPort
  // init and write done signals driven directly by the OCMC
  io.doneInit := reducer.mcif.done & io.startInit
  io.doneWrite := reducer.mcif.done & io.startWrite
  io.doneRegular := (reducer.opCount === io.numNZ)

  // TODO emit statistics (hazards, etc)
  // TODO finish wire-up + test
}
