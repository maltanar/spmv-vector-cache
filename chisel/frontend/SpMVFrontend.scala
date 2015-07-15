package SpMVAccel

import Chisel._
import TidbitsAXI._
import TidbitsStreams._
import TidbitsOCM._

class SpMVFrontend(val p: SpMVAccelWrapperParams) extends Module {
  val io = new Bundle {
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
  rptGen.inElem <> io.nzDataIn
  rptGen.inRepCnt <> deltaGen.deltas

  // TODO wire up multiplier inputs with StreamJoin

  // add a queue to buffer multiplier results, in case of hazard stalls
  // this will let the pipes continue a while longer
  // TODO parametrize queue size
  val partialProductQ = Module(new Queue(UInt(width=p.opWidth), 16)).io
  partialProductQ.enq.valid := mul.io.out.valid
  partialProductQ.enq.bits := mul.io.out.bits

  // TODO instantiate InterleavedReduce

  // TODO emit statistics (hazards, etc)
  // TODO finish wire-up + test
}
