package SpMVAccel

import Chisel._
import TidbitsAXI._
import TidbitsStreams._
import TidbitsSimUtils._

object TestSpMVFrontend {
  val p = new SpMVAccelWrapperParams()
}


class TestSpMVFrontend() extends AXIWrappableAccel(TestSpMVFrontend.p) {
  plugRegOuts()
  plugMemReadPort()
  plugMemWritePort()

  val opWidth = TestSpMVFrontend.p.opWidth
  val ptrWidth = TestSpMVFrontend.p.ptrWidth

  class SeqParams(w: Int) extends Bundle {
    val init = UInt(width = w)
    val step = UInt(width = w)
    override def clone = {new SeqParams(w).asInstanceOf[this.type]}
  }

  object SeqParams{
    def apply(w: Int): SeqParams = {
      new SeqParams(w)
    }
  }

  val in = new Bundle {
    // control
    val startInit = Bool()
    val startRegular = Bool()
    val startWrite = Bool()
    // value inputs
    val numRows = UInt(width = p.csrDataWidth)
    val numCols = UInt(width = p.csrDataWidth)
    val numNZ = UInt(width = p.csrDataWidth)
    // seq generator controls
    val colPtrP = SeqParams(32)
    val rowIndP = SeqParams(32)
    val nzDataP = SeqParams(32)
    val inputVecP = SeqParams(32)
  }

  val out = new Bundle {
    // status
    val doneInit = Bool()
    val doneRegular = Bool()
    val doneWrite = Bool()
    // TODO debug+profiling outputs
    val redOutputVec = UInt(width = p.csrDataWidth)

  }

  override lazy val regMap = manageRegIO(in, out)


  val frontendM = Module(new SpMVFrontend(TestSpMVFrontend.p))
  val frontend = frontendM.io

  // sequence generators on all backend inputs
  val colPtrGen = Module(new SequenceGenerator(ptrWidth)).io
  colPtrGen.seq <> frontend.colPtrIn
  in.colPtrP <> colPtrGen
  colPtrGen.count := in.numCols+UInt(1)
  val rowIndGen = Module(new SequenceGenerator(ptrWidth)).io
  rowIndGen.seq <> frontend.rowIndIn
  in.rowIndP <> rowIndGen
  rowIndGen.count := in.numNZ
  val nzDataGen = Module(new SequenceGenerator(opWidth)).io
  nzDataGen.seq <> frontend.nzDataIn
  in.nzDataP <> nzDataGen
  nzDataGen.count := in.numNZ
  val inpVecGen = Module(new SequenceGenerator(opWidth)).io
  inpVecGen.seq <> frontend.inputVecIn
  in.inputVecP <> inpVecGen
  inpVecGen.count := in.numCols
  val gens = List(colPtrGen, rowIndGen, nzDataGen, inpVecGen)
  for(gen <- gens) {
    gen.start := in.startRegular
  }

  // reducer on the result vector output
  val redFxn = {(a:UInt, b:UInt) => a+b}
  val outVecRed = Module(new StreamReducer(opWidth, 0, redFxn)).io
  outVecRed.streamIn <> frontend.outputVecOut
  outVecRed.byteCount := in.numRows*UInt(opWidth/8)
  outVecRed.start := in.startWrite
  out.redOutputVec := outVecRed.reduced

  out.doneWrite := outVecRed.finished
  in <> frontend
  out <> frontend

  // test
  override def defaultTest(t: WrappableAccelTester): Boolean = {
    def sumUpTo(x: Int): Int = {return (x*(x+1))/2}
    super.defaultTest(t)
    var numRows = 64
    var numCols = 64
    var numNZ = 64
    t.writeReg("in_numRows", numRows)
    t.writeReg("in_numCols", numCols)
    t.writeReg("in_numNZ", numNZ)
    // initialize frontend and check
    t.writeReg("in_startInit", 1)
    while(t.readReg("out_doneInit") != 1) {}
    t.writeReg("in_startInit", 0)
    t.expectReg("out_doneInit", 0)
    t.expect(frontendM.reducer.mcif.busy, 0)
    t.writeReg("in_startWrite", 1)
    while(t.readReg("out_doneWrite") != 1) {}
    t.expectReg("out_redOutputVec", 0)
    t.writeReg("in_startWrite", 0)
    t.expectReg("out_doneWrite", 0)
    t.expect(frontendM.reducer.mcif.busy, 0)
    // identity matrix (no hazards)
    // colptr: 1 elem per column
    t.writeReg("in_colPtrP_init", 0)
    t.writeReg("in_colPtrP_step", 1)
    // rowind: linearly increasing
    t.writeReg("in_rowIndP_init", 0)
    t.writeReg("in_rowIndP_step", 1)
    // nz data: all ones
    t.writeReg("in_nzDataP_init", 1)
    t.writeReg("in_nzDataP_step", 0)
    // input vector: 1...n
    t.writeReg("in_inputVecP_init", 1)
    t.writeReg("in_inputVecP_step", 1)
    // start regular operation
    t.writeReg("in_startRegular", 1)
    while(t.readReg("out_doneRegular") != 1) {}
    t.expect(frontendM.reducer.hazardStalls, 0)
    t.expect(frontendM.reducer.opCount, numNZ)
    t.writeReg("in_startRegular", 0)
    // write result
    t.writeReg("in_startWrite", 1)
    while(t.readReg("out_doneWrite") != 1) {}
    t.expectReg("out_redOutputVec", sumUpTo(numRows))
    t.writeReg("in_startWrite", 0)

    // TODO should not be necessary! fix StreamDeltaGen
    t.reset(10)
    // ************************************************************************
    // change matrix: row vector (1x64), full of hazards
    numRows = 1
    t.writeReg("in_numRows", numRows)
    t.writeReg("in_numCols", numCols)
    t.writeReg("in_numNZ", numNZ)

    // re-initialize result mem
    t.writeReg("in_startInit", 1)
    while(t.readReg("out_doneInit") != 1) {}
    t.writeReg("in_startInit", 0)
    // setup sequence gens

    // colptr: 1 elem per column
    t.writeReg("in_colPtrP_init", 0)
    t.writeReg("in_colPtrP_step", 1)
    // rowind: constant 0
    t.writeReg("in_rowIndP_init", 0)
    t.writeReg("in_rowIndP_step", 0)
    // nz data: all ones
    t.writeReg("in_nzDataP_init", 1)
    t.writeReg("in_nzDataP_step", 0)
    // input vector: 1...n
    t.writeReg("in_inputVecP_init", 1)
    t.writeReg("in_inputVecP_step", 1)

    // start operation
    t.writeReg("in_startRegular", 1)
    while(t.readReg("out_doneRegular") != 1) {}
    t.peek(frontendM.reducer.hazardStalls)
    t.expect(frontendM.reducer.opCount, numNZ)
    t.writeReg("in_startRegular", 0)
    // write result
    t.writeReg("in_startWrite", 1)
    while(t.readReg("out_doneWrite") != 1) {}
    t.expectReg("out_redOutputVec", sumUpTo(numNZ))
    t.writeReg("in_startWrite", 0)
    
    println("All tests finished")
    return true
  }

}
