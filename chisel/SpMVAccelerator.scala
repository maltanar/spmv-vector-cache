package SpMVAccel

import Chisel._
import TidbitsDMA._
import TidbitsAXI._
import TidbitsStreams._

class SpMVAccelerator(p: SpMVAccelWrapperParams) extends AXIWrappableAccel(p) {
  override lazy val accelVersion: String = "alpha"

  // plug unused register file elems / set defaults
  plugRegOuts()
  // types for SpMV channels, handy for instantiating FIFOs etc.
  val tColPtr = UInt(width = p.ptrWidth)
  val tRowInd = UInt(width = p.ptrWidth)
  val tNZData = UInt(width = p.opWidth)
  val tInpVec = UInt(width = p.opWidth)

  val in = new Bundle {
    // control inputs
    val startInit = Bool()
    val startRegular = Bool()
    val startWrite = Bool()
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
    val statBackend = UInt(width = p.csrDataWidth)
    val statFrontend = UInt(width = p.csrDataWidth)
    // TODO profiling outputs & other outputs
  }
  override lazy val regMap = manageRegIO(in, out)

  // instantiate backend, connect memory port
  val backend = Module(new SpMVBackend(p, 0)).io
  // use partial interface fulfilment to connect backend interfaces
  // produces warnings, but should work fine
  backend <> in
  // memory ports
  backend <> io
  val hasDecErr = (backend.decodeErrors != UInt(0))
  val statBackendL = List(hasDecErr, backend.doneWrite, backend.doneRegular)
  out.statBackend := Cat(statBackendL)

  // instantiate frontend
  val frontend = Module(new SpMVFrontend(p)).io
  frontend <> in
  // TODO frontend stats
  val statFrontendL = List(frontend.doneInit, frontend.doneWrite, frontend.doneRegular)
  out.statFrontend := Cat(statFrontendL)

  // instantiate FIFOs for backend-frontend communication
  val colPtrFIFO = Module(new CustomQueue(tColPtr, p.colPtrFIFODepth)).io
  colPtrFIFO.enq <> backend.colPtrOut
  colPtrFIFO.deq <> frontend.colPtrIn
  val rowIndFIFO = Module(new CustomQueue(tRowInd, p.rowIndFIFODepth)).io
  rowIndFIFO.enq <> backend.rowIndOut
  rowIndFIFO.deq <> frontend.rowIndIn
  val nzDataFIFO = Module(new CustomQueue(tNZData, p.nzDataFIFODepth)).io
  nzDataFIFO.enq <> backend.nzDataOut
  nzDataFIFO.deq <> frontend.nzDataIn
  val inpVecFIFO = Module(new CustomQueue(tInpVec, p.inpVecFIFODepth)).io
  inpVecFIFO.enq <> backend.inputVecOut
  inpVecFIFO.deq <> frontend.inputVecIn

  // output vector FIFO is optional
  if(p.outVecFIFODepth == 0) {
    backend.outputVecIn <> frontend.outputVecOut
  } else {
    val outVecFIFO = Module(new CustomQueue(tInpVec, p.outVecFIFODepth)).io
    outVecFIFO.deq <> backend.outputVecIn
    outVecFIFO.enq <> frontend.outputVecOut
  }

  // wire-up FIFO levels to backend monitoring inputs
  // backend will throttle channels when FIFO level goes over threshold
  backend.fbColPtr := colPtrFIFO.count
  backend.fbRowInd := rowIndFIFO.count
  backend.fbNZData := nzDataFIFO.count
  backend.fbInputVec := inpVecFIFO.count

  // TODO add status/profiling signals
}
