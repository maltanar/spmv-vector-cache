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
    val mcif = new OCMControllerIF(pOCM)
    // TODO mode setting input (init/regular/dump)
    // data exchange with backend
    val colPtrIn = Decoupled(UInt(width = p.ptrWidth)).flip
    val rowIndIn = Decoupled(UInt(width = p.ptrWidth)).flip
    val nzDataIn = Decoupled(UInt(width = p.opWidth)).flip
    val inputVecIn = Decoupled(UInt(width = p.opWidth)).flip
    val outputVecOut = Decoupled(UInt(width = p.opWidth))
  }
  // TODO connect backpressure signals!
  // instantiate multiply operator
  val mul = Module(p.makeMul())
  // instantiate StreamDelta and StreamRepeatElem
  val deltaGen = Module(new StreamDelta(p.ptrWidth)).io
  deltaGen.samples <> io.colPtrIn
  val rptGen = Module(new StreamRepeatElem(p.opWidth, p.ptrWidth)).io
  rptGen.inElem <> io.inputVecIn
  rptGen.inRepCnt <> deltaGen.deltas

  // wire up multiplier inputs with StreamJoin
  val fxnM = {(a: UInt, b: UInt) => SemiringOperands(p.opWidth, a, b)}
  val mulOpJoin = Module(new StreamJoin(opType, opType, syncOpType, fxnM)).io
  mulOpJoin.inA <> rptGen.out
  mulOpJoin.inB <> io.nzDataIn
  mul.io.in <> mulOpJoin.out

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
  val reducer = Module(new InterleavedReduceOCM(p)).io
  reducer.operands <> redJoin.out

  // TODO expose more specific controls instead
  reducer.mcif <> io.mcif

  io.outputVecOut <> io.mcif.dumpPort

  // TODO emit statistics (hazards, etc)
  // TODO finish wire-up + test
}
