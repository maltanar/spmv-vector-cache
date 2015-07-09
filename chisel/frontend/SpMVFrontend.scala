package SpMVAccel

import Chisel._
import TidbitsAXI._
import TidbitsStreams._

class SpMVFrontend(val p: SpMVAccelWrapperParams) extends Module {
  val io = new Bundle {
    // TODO mode setting input (init/regular/dump)
    // data exchange with backend
    val colPtrIn = Decoupled(UInt(width = p.ptrWidth)).flip
    val rowIndIn = Decoupled(UInt(width = p.ptrWidth)).flip
    val nzDataIn = Decoupled(UInt(width = p.opWidth)).flip
    val InputVecIn = Decoupled(UInt(width = p.opWidth)).flip
    val OutputVecOut = Decoupled(UInt(width = p.opWidth))
  }

  // TODO instantiate multiply operator
  // TODO instantiate HazardGuard
  // TODO instantiate reduce operator
  // TODO instantiate context store
  // TODO emit statistics (hazards, etc)
  // TODO wire-up
}
