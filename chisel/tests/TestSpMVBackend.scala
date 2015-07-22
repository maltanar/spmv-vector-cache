package SpMVAccel

import Chisel._
import TidbitsAXI._
import TidbitsStreams._
import TidbitsSimUtils._

object TestSpMVBackend {
  val p = new SpMVAccelWrapperParams()
}

class TestSpMVBackend() extends AXIWrappableAccel(TestSpMVBackend.p) {
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
    val allMonsFinished = Bool()
    val monDebug = UInt(width = p.csrDataWidth)
    //
    val doneRegular = Bool()
    val doneWrite = Bool()
    //
    val backendDebug = UInt(width = p.csrDataWidth)
    //
    val rdMon = new StreamMonitorOutIF()
  }
  override lazy val regMap = manageRegIO(in, out)

  // instantiate backend, connect memory port
  val backend = Module(new SpMVBackend(TestSpMVBackend.p, 0)).io
  // use partial interface fulfilment to connect backend interfaces
  // produces warnings, but should work fine
  backend <> in
  out <> backend
  backend <> io.mp(0)

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
  val ptrWidth = TestSpMVBackend.p.ptrWidth
  val opWidth = TestSpMVBackend.p.opWidth
  val ptrBytes = UInt(ptrWidth/8)
  val opBytes = UInt(opWidth/8)
  val redFxn = {(a:UInt, b:UInt) => a+b}
  val colPtrMon = Module(new StreamReducer(ptrWidth, 0, redFxn)).io
  colPtrMon.streamIn <> backend.colPtrOut
  colPtrMon.start := in.startRegular
  colPtrMon.byteCount := (in.numCols+UInt(1)) * ptrBytes
  out.redColPtr := colPtrMon.reduced

  val rowIndMon = Module(new StreamReducer(ptrWidth, 0, redFxn)).io
  rowIndMon.streamIn <> backend.rowIndOut
  rowIndMon.start := in.startRegular
  rowIndMon.byteCount := in.numNZ * ptrBytes
  out.redRowInd := rowIndMon.reduced

  val nzDataMon = Module(new StreamReducer(opWidth, 0, redFxn)).io
  nzDataMon.streamIn <> backend.nzDataOut
  nzDataMon.start := in.startRegular
  nzDataMon.byteCount := in.numNZ * opBytes
  out.redNZData0 := nzDataMon.reduced(31,0)
  out.redNZData1 := nzDataMon.reduced(63,32)

  val inpVecMon = Module(new StreamReducer(opWidth, 0, redFxn)).io
  inpVecMon.streamIn <> backend.inputVecOut
  inpVecMon.start := in.startRegular
  inpVecMon.byteCount := in.numCols * opBytes
  out.redInputVec0 := inpVecMon.reduced(31,0)
  out.redInputVec1 := inpVecMon.reduced(63,32)

  val outVecGen = Module(new SequenceGenerator(opWidth)).io
  outVecGen.seq <> backend.outputVecIn
  outVecGen.start := in.startWrite
  outVecGen.init := UInt(0)
  outVecGen.count := in.numRows
  outVecGen.step := UInt(1)

  val monFin = List(colPtrMon.finished, rowIndMon.finished,
                    nzDataMon.finished, inpVecMon.finished)

  out.allMonsFinished := monFin.reduce(_ & _)
  out.monDebug := Cat(monFin)


  // stream monitors -- way at the end, so that the stream we want to monitor
  // has already been wired up (if this is a problem at all)
  out.rdMon := StreamMonitor(io.mp(0).memRdRsp, in.startRegular & !out.allMonsFinished)

  // test
  override def defaultTest(t: WrappableAccelTester): Boolean = {
    super.defaultTest(t)
    // ================================================================
    // initialize test values
    val numRows = 64
    val numCols = 64
    val numNZ = 64
    val colPtrStart = 0
    val rowIndStart = alignedIncrement(colPtrStart, 4*(numCols+1), 64)
    val nzDataStart = alignedIncrement(rowIndStart, 4*numNZ, 64)
    val inpVecStart = alignedIncrement(nzDataStart, 8*numNZ, 64)
    val outVecStart = alignedIncrement(inpVecStart, 8*numCols, 64)
    // initialize buffers
    for(i <- 0 until (numCols+1)/2) {t.writeMem(colPtrStart+i*8, i+1)}
    for(i <- 0 until numNZ/2) {t.writeMem(rowIndStart+i*8, i+1)}
    for(i <- 0 until numNZ) {t.writeMem(nzDataStart+i*8, i+1)}
    for(i <- 0 until numCols) {t.writeMem(inpVecStart+i*8, i+1)}
    for(i <- 0 until numRows) {t.writeMem(outVecStart+i*8, 0)}
    t.writeReg("in_numRows", numRows)
    t.writeReg("in_numCols", numCols)
    t.writeReg("in_numNZ", numNZ)
    t.writeReg("in_baseColPtr", colPtrStart)
    t.writeReg("in_baseRowInd", rowIndStart)
    t.writeReg("in_baseNZData", nzDataStart)
    t.writeReg("in_baseInputVec", inpVecStart)
    t.writeReg("in_baseOutputVec", outVecStart)
    t.printAllRegs()
    // ================================================================
    // test backend writeout mode
    t.expectReg("out_doneWrite", 0)
    t.writeReg("in_startWrite", 1)
    while(t.readReg("out_doneWrite") != 1) { t.step(1)}
    for(i <- 0 until numRows) {t.expectMem(outVecStart+i*8, i)}
    // done should go back to low after start=0
    t.writeReg("in_startWrite", 0)
    t.expectReg("out_doneWrite", 0)
    // ================================================================
    // test regular operation mode
    t.expectReg("out_doneRegular", 0)
    t.writeReg("in_startRegular", 1)
    while(t.readReg("out_doneRegular") != 1) { t.step(1) }
    while(t.readReg("out_allMonsFinished") != 1) {t.step(1) }
    // check the sums from reducers
    def sumUpTo(x: Int): Int = {return (x*(x+1))/2}
    t.expectReg("out_redColPtr", sumUpTo((numCols+1)/2))
    t.expectReg("out_redRowInd", sumUpTo(numNZ/2))
    t.expectReg("out_redNZData0", sumUpTo(numNZ))
    t.expectReg("out_redNZData1", 0)
    t.expectReg("out_redInputVec0", sumUpTo(numCols))
    t.expectReg("out_redInputVec1", 0)
    // done should go back to low after start=0
    t.writeReg("in_startRegular", 0)
    t.expectReg("out_doneRegular", 0)
    t.printAllRegs()

    return true
  }
}
