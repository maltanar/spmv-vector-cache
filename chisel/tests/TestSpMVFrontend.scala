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

  object SeqParams{
    def apply(w: Int): Bundle = {
      new Bundle {
        val init = UInt(width = w)
        val step = UInt(width = w)
      }
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

  val frontend = Module(new SpMVFrontend(TestSpMVFrontend.p)).io

  // sequence generators on all backend inputs
  val colPtrGen = Module(new SequenceGenerator(ptrWidth)).io
  colPtrGen.seq <> frontend.colPtrIn
  colPtrGen <> in.colPtrP
  colPtrGen.count := in.numCols+UInt(1)
  val rowIndGen = Module(new SequenceGenerator(ptrWidth)).io
  rowIndGen.seq <> frontend.rowIndIn
  rowIndGen <> in.rowIndP
  rowIndGen.count := in.numNZ
  val nzDataGen = Module(new SequenceGenerator(opWidth)).io
  nzDataGen.seq <> frontend.nzDataIn
  nzDataGen <> in.nzDataP
  nzDataGen.count := in.numNZ
  val inpVecGen = Module(new SequenceGenerator(opWidth)).io
  inpVecGen.seq <> frontend.inputVecIn
  inpVecGen <> in.inputVecP
  inpVecGen.count := in.numCols
  val gens = List(colPtrGen, rowIndGen, nzDataGen, inpVecGen)
  for(gen <- gens) {
    gen.start := in.startRegular
  }

  // reducer on the result vector output
  val redFxn = {(a:UInt, b:UInt) => a+b}
  val outVecRed = Module(new StreamReducer(opWidth, 0, redFxn)).io
  outVecRed.streamIn <> frontend.outputVecOut
  outVecRed.byteCount := in.numRows*UInt(opWidth)
  out.redOutputVec := outVecRed.reduced

  out.doneWrite := outVecRed.finished
  in <> frontend
  out <> frontend

}
