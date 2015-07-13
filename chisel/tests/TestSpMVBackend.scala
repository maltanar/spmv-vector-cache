package SpMVAccel

import Chisel._
import TidbitsAXI._
import TidbitsStreams._
import TidbitsSimUtils._

object Test {
  val p = new SpMVAccelWrapperParams()
}

class TestSpMVBackend() extends AXIWrappableAccel(Test.p) {
  override lazy val accelVersion: String = "sim-test-only"

  plugRegOuts()

  val in = new Bundle {
    // control inputs
    val startRegular = Bool()
    val startWrite = Bool()
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
  }

  val out = new Bundle {
    val redColPtr = UInt(width = p.csrDataWidth)
    val redRowInd = UInt(width = p.csrDataWidth)
    val redNZData0 = UInt(width = p.csrDataWidth)
    val redNZData1 = UInt(width = p.csrDataWidth)
    val redInputVec0 = UInt(width = p.csrDataWidth)
    val redInputVec1 = UInt(width = p.csrDataWidth)
    //
    val colPtrMonFinished = Bool()
    val rowIndMonFinished = Bool()
    val nzDataMonFinished = Bool()
    val inpVecMonFinished = Bool()
    val outVecGenFinished = Bool()
    //
    val doneRegular = Bool()
    val doneWrite = Bool()

  }
  override lazy val regMap = manageRegIO(in, out)

  // instantiate backend, connect memory port
  val backend = Module(new SpMVBackend(Test.p, 0)).io
  // use partial interface fulfilment to connect backend interfaces
  // produces warnings, but should work fine
  backend <> in
  out <> backend
  backend <> io

  // plug rate control I/Os for backend -- unused
  backend.thresColPtr := UInt(1)
  backend.thresRowInd := UInt(1)
  backend.thresNZData := UInt(1)
  backend.thresInputVec := UInt(1)
  backend.fbColPtr := UInt(0)
  backend.fbRowInd := UInt(0)
  backend.fbNZData := UInt(0)
  backend.fbInputVec := UInt(0)

  // monitors on all backend outputs
  val ptrWidth = Test.p.ptrWidth
  val opWidth = Test.p.opWidth
  val ptrBytes = UInt(ptrWidth/8)
  val opBytes = UInt(opWidth/8)
  val redFxn = {(a:UInt, b:UInt) => a+b}
  val colPtrMon = Module(new StreamReducer(ptrWidth, 0, redFxn)).io
  colPtrMon.streamIn <> backend.colPtrOut
  colPtrMon.start := in.startRegular
  colPtrMon.byteCount := (in.numCols+UInt(1)) * ptrBytes
  out.redColPtr := colPtrMon.reduced
  out.colPtrMonFinished := colPtrMon.finished

  val rowIndMon = Module(new StreamReducer(ptrWidth, 0, redFxn)).io
  rowIndMon.streamIn <> backend.rowIndOut
  rowIndMon.start := in.startRegular
  rowIndMon.byteCount := in.numNZ * ptrBytes
  out.redRowInd := rowIndMon.reduced
  out.rowIndMonFinished := rowIndMon.finished

  val nzDataMon = Module(new StreamReducer(opWidth, 0, redFxn)).io
  nzDataMon.streamIn <> backend.nzDataOut
  nzDataMon.start := in.startRegular
  nzDataMon.byteCount := in.numNZ * opBytes
  out.redNZData0 := nzDataMon.reduced(31,0)
  out.redNZData1 := nzDataMon.reduced(63,32)
  out.nzDataMonFinished := nzDataMon.finished

  val inpVecMon = Module(new StreamReducer(opWidth, 0, redFxn)).io
  inpVecMon.streamIn <> backend.inputVecOut
  inpVecMon.start := in.startRegular
  inpVecMon.byteCount := in.numRows * ptrBytes
  out.redInputVec0 := inpVecMon.reduced(31,0)
  out.redInputVec1 := inpVecMon.reduced(63,32)
  out.inpVecMonFinished := inpVecMon.finished

  val outVecGen = Module(new SequenceGenerator(opWidth)).io
  outVecGen.seq <> backend.outputVecIn
  outVecGen.start := in.startWrite
  outVecGen.init := UInt(0)
  outVecGen.count := in.numRows
  outVecGen.step := UInt(1)
  out.outVecGenFinished := outVecGen.finished

  // test
  override def defaultTest(t: WrappableAccelTester): Boolean = {
    super.defaultTest(t)
    // test backend writeout mode
    t.expectReg("out_outVecGenFinished", 0)
    t.expectReg("out_doneWrite", 0)
    t.writeReg("in_numRows", 64)
    t.writeReg("in_baseOutputVec", 0)
    t.writeReg("in_startWrite", 1)
    while(t.readReg("out_outVecGenFinished") != 1) { t.step(1)}
    while(t.readReg("out_doneWrite") != 1) { t.step(1)}
    for(i <- 0 until 64) {
      t.expectMem(i*8, i)
    }
    t.writeReg("in_startWrite", 0)
    t.expectReg("out_outVecGenFinished", 0)
    t.expectReg("out_doneWrite", 0)

    return true
  }
}
