package SpMVAccel

import Chisel._
import TidbitsDMA._
import TidbitsAXI._
import TidbitsStreams._

class SpMVAccelerator(p: SpMVAccelWrapperParams) extends AXIWrappableAccel(p) {
  override lazy val accelVersion: String = "alpha"

  // plug unused register file elems / set defaults
  plugRegOuts()

  val in = new Bundle {
    // control inputs
    val ctlFrontend = UInt(width = p.csrDataWidth)
    val ctlBackend = UInt(width = p.csrDataWidth)
    // val resetAll = Bool()
    // value inputs
    val numRows = UInt(width = p.csrDataWidth)
    val numCols = UInt(width = p.csrDataWidth)
    val numNZ = UInt(width = p.csrDataWidth)
    val baseColPtr = UInt(width = p.csrDataWidth)
    val baseRowInd = UInt(width = p.csrDataWidth)
    val baseNZData = UInt(width = p.csrDataWidth)
    val baseInputVec = UInt(width = p.csrDataWidth)
    val baseOutputVec = UInt(width = p.csrDataWidth)
    val thresColPtr = UInt(width = p.csrDataWidth)
    val thresRowInd = UInt(width = p.csrDataWidth)
    val thresNZData = UInt(width = p.csrDataWidth)
    val thresInputVec = UInt(width = p.csrDataWidth)
  }

  val out = new Bundle {
    val statFrontend = UInt(width = p.csrDataWidth)
    val statBackend = UInt(width = p.csrDataWidth)
    // TODO profiling outputs, other outputs
  }
  manageRegIO(in, out)

  // instantiate backend, connect memory port
  val backend = Module(new SpMVBackend(p, 0)).io
  // use partial interface fulfilment to connect backend interfaces
  // produces warnings, but should work fine
  backend <> in
  backend <> io
  backend.startRegular := (in.ctlBackend(1, 0) === UInt(1))
  backend.startWrite := (in.ctlBackend(1, 0) === UInt(2))
  val hasDecErr = (backend.decodeErrors != UInt(0))
  out.statBackend := Cat(List(hasDecErr, backend.doneWrite, backend.doneRegular))
  // TODO wire-up backend FIFO levels

  // TODO instantiate frontend and wire-up
  val frontend = Module(new SpMVFrontend(p)).io
  // TODO connect via FIFOs, connect count outputs to backend
  // TODO connect control/status signals
}
