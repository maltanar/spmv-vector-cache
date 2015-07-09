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
    val ctlFrontend = UInt(width = p.addrWidth)
    val ctlBackend = UInt(width = p.addrWidth)
    // val resetAll = Bool()
    // value inputs
    val numRows = UInt(width = p.addrWidth)
    val numCols = UInt(width = p.addrWidth)
    val numNZ = UInt(width = p.addrWidth)
    val baseColPtr = UInt(width = p.addrWidth)
    val baseRowInd = UInt(width = p.addrWidth)
    val baseNZData = UInt(width = p.addrWidth)
    val baseInputVec = UInt(width = p.addrWidth)
    val baseOutputVec = UInt(width = p.addrWidth)
    val thresColPtr = UInt(width = p.addrWidth)
    val thresRowInd = UInt(width = p.addrWidth)
    val thresNZData = UInt(width = p.addrWidth)
    val thresInputVec = UInt(width = p.addrWidth)
  }

  val out = new Bundle {
    val statFrontend = UInt(width = p.addrWidth)
    val statBackend = UInt(width = p.addrWidth)
    // TODO profiling outputs, other outputs
  }
  manageRegIO(in, out)

  // instantiate backend, connect memory port
  val backend = Module(new SpMVBackend(p, 0)).io
  backend.memRdReq <> io.memRdReq
  backend.memRdRsp <> io.memRdRsp
  backend.memWrReq <> io.memWrReq
  backend.memWrDat <> io.memWrDat
  backend.memWrRsp <> io.memWrRsp
  // TODO wire-up backend value inputs, FIFO levels, ctl and status

  // TODO instantiate frontend and wire-up
  val frontend = Module(new SpMVFrontend(p)).io
  // TODO connect via FIFOs, connect count outputs to backend
  // TODO connect control/status signals
}
