package SpMVAccel

import Chisel._
import TidbitsDMA._
import TidbitsAXI._
import TidbitsStreams._
import TidbitsSimUtils._
import java.nio.file.{Files, Paths}
import java.nio.ByteBuffer
import java.io.{FileInputStream, DataInputStream}


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
  val frontendM = Module(new SpMVFrontend(p))
  val frontend = frontendM.io
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

  // test
  override def defaultTest(t: WrappableAccelTester): Boolean = {
    t.reset(10)
    super.defaultTest(t)
    val ptrBytes = p.ptrWidth/8
    val dataBytes = p.opWidth/8
    def setThresholds() = {
      t.writeReg("in_thresColPtr", p.colPtrFIFODepth/2)
      t.writeReg("in_thresRowInd", p.rowIndFIFODepth/2)
      t.writeReg("in_thresNZData", p.nzDataFIFODepth/2)
      t.writeReg("in_thresInputVec", p.inpVecFIFODepth/2)
    }

    def loadMatrix(name: String): Int = {
      val baseDir = "/home/maltanar/sandbox/spmv-vector-cache/matrices/"
      val matrixDir = baseDir + name + "/"
      // load the metadata file
      val metaFile = matrixDir+name+"-meta.bin"
      var m = ByteBuffer.wrap(Files.readAllBytes(Paths.get(metaFile)))
      m.order(java.nio.ByteOrder.nativeOrder)
      // read matrix dimensions from metadata and set up in regs
      val rows = m.getInt()
      val cols = m.getInt()
      val nnz = m.getInt()
      t.writeReg("in_numRows", rows)
      t.writeReg("in_numCols", cols)
      t.writeReg("in_numNZ", nnz)
      // load col ptr data
      var baseAddr = 0
      t.writeReg("in_baseColPtr", baseAddr)
      t.fileToMem(matrixDir+name+"-indptr.bin", baseAddr)
      baseAddr = alignedIncrement(baseAddr, (cols+1)*ptrBytes, 64)
      // load row ind data
      t.writeReg("in_baseRowInd", baseAddr)
      t.fileToMem(matrixDir+name+"-inds.bin", baseAddr)
      baseAddr = alignedIncrement(baseAddr, nnz*ptrBytes, 64)
      // load nonzero data
      t.writeReg("in_baseNZData", baseAddr)
      t.fileToMem(matrixDir+name+"-data.bin", baseAddr)
      baseAddr = alignedIncrement(baseAddr, nnz*dataBytes, 64)
      // set up pointers for input&output vectors
      // will be used by vector cons. functions later
      t.writeReg("in_baseInputVec", baseAddr)
      baseAddr = alignedIncrement(baseAddr, cols*dataBytes, 64)
      t.writeReg("in_baseOutputVec", baseAddr)

      println("Loaded matrix " + name)
      return baseAddr
    }

    def makeInputVector(gen: Int => Int) = {
      val inpVecBase = t.readReg("in_baseInputVec")
      val cols = t.readReg("in_numCols").toInt

      if(dataBytes != 8) {
        println("FIXME makeInputVector with dataBytes != 8")
        System.exit(-1)
      }
      // TODO will only work if memory write width = vector width
      // should generate an array and write that instead
      for(j <- 0 until cols) {
        t.writeMem(inpVecBase+j*dataBytes, gen(j))
      }
      println("Initialized input vector")
    }

    def cleanOutputVector() = {
      val rows = t.readReg("in_numRows").toInt
      val base = t.readReg("in_baseOutputVec")
      for(i <- 0 until rows) {
        t.writeMem(base+i*dataBytes, 0)
      }
    }

    def doneInit(): Boolean = {t.readReg("out_statFrontend") == 4}
    def doneWrite(): Boolean = {t.readReg("out_statBackend") == 2}
    def doneRegular(): Boolean = {t.readReg("out_statFrontend") == 1}

    def spmvInit() = {
      println("Starting SpMV init operation @" + t.t.toString)
      t.expectReg("out_statFrontend", 0)
      t.expectReg("out_statBackend", 0)
      t.writeReg("in_startInit", 1)
      while(!doneInit()) {}
      t.writeReg("in_startInit", 0)
      println("Finished SpMV init operation @" + t.t.toString)
    }

    def spmvRegular() = {
      println("Starting regular SpMV operation @" + t.t.toString)
      t.expectReg("out_statFrontend", 0)
      t.expectReg("out_statBackend", 0)
      t.writeReg("in_startRegular", 1)
      while(!doneRegular()) {traceRegular()}
      t.writeReg("in_startRegular", 0)
      println("Finished regular SpMV operation @" + t.t.toString)
    }

    def spmvWrite() = {
      println("Starting SpMV write operation @" + t.t.toString)
      t.expectReg("out_statFrontend", 0)
      t.expectReg("out_statBackend", 0)
      t.writeReg("in_startWrite", 1)
      while(!doneWrite()) {}
      t.writeReg("in_startWrite", 0)
      println("Finished SpMV write operation @" + t.t.toString)
    }

    def printOutVec() = {
      val rows = t.readReg("in_numRows").toInt
      val base = t.readReg("in_baseOutputVec")
      for(i <- 0 until rows) {
        println("y["+i.toString+"] = " + t.readMem(base+i*dataBytes).toString)
      }
    }

    def printWhenTransaction(name: String, decif: DecoupledIO[UInt]) = {
      val v = t.peek(decif.valid)
      val r = t.peek(decif.ready)
      if(v == 1 && r == 1) {
        println(name +" : " + t.peek(decif.bits).toString)
      }
    }

    def printWhenTransactionAggr[T <: Aggregate](name: String, decif: DecoupledIO[T]) = {
      val v = t.peek(decif.valid)
      val r = t.peek(decif.ready)
      if(v == 1 && r == 1) {
        println(name + ":")
        t.isTrace=true
        t.peek(decif.bits)
        t.isTrace=false
      }
    }

    def traceRegular() = {
      // uncomment to monitor FIFO data flows during regular exec
      //printWhenTransaction("ColPtr", colPtrFIFO.deq)
      //printWhenTransaction("RowInd", rowIndFIFO.deq)
      //printWhenTransaction("NZData", nzDataFIFO.deq)
      //printWhenTransaction("InpVec", inpVecFIFO.deq)
      //printWhenTransactionAggr("ReducerOperands", frontendM.reducer.operands)
    }

    t.isTrace = false
    setThresholds()
    loadMatrix("circuit204-uint64")
    makeInputVector(i => 1)
    cleanOutputVector()

    spmvInit()
    spmvRegular()
    spmvWrite()
    printOutVec()

    t.isTrace = true

    return true
  }
}
