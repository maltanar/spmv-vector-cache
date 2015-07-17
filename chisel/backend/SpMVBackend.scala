package SpMVAccel

import Chisel._
import TidbitsDMA._
import TidbitsAXI._
import TidbitsStreams._

class SpMVBackend(val p: SpMVAccelWrapperParams, val idBase: Int) extends Module {
  val pMem = p.toMRP()
  val io = new Bundle {
    // control
    val startRegular = Bool(INPUT)
    val startWrite = Bool(INPUT)
    // status
    val doneRegular = Bool(OUTPUT)
    val doneWrite = Bool(OUTPUT)
    val decodeErrors = UInt(OUTPUT, width = 32)
    val backendDebug = UInt(OUTPUT, width = 32)
    // value inputs
    val numRows = UInt(INPUT, width = 32)
    val numCols = UInt(INPUT, width = 32)
    val numNZ = UInt(INPUT, width = 32)
    val baseColPtr = UInt(INPUT, width = 32)
    val baseRowInd = UInt(INPUT, width = 32)
    val baseNZData = UInt(INPUT, width = 32)
    val baseInputVec = UInt(INPUT, width = 32)
    val baseOutputVec = UInt(INPUT, width = 32)
    val thresColPtr = UInt(INPUT, width = 10)
    val thresRowInd = UInt(INPUT, width = 10)
    val thresNZData = UInt(INPUT, width = 10)
    val thresInputVec = UInt(INPUT, width = 10)
    // FIFO level feedback for throttling
    val fbColPtr = UInt(INPUT, width = 10)
    val fbRowInd = UInt(INPUT, width = 10)
    val fbNZData = UInt(INPUT, width = 10)
    val fbInputVec = UInt(INPUT, width = 10)
    // data exchange with frontend
    val colPtrOut = Decoupled(UInt(width = p.ptrWidth))
    val rowIndOut = Decoupled(UInt(width = p.ptrWidth))
    val nzDataOut = Decoupled(UInt(width = p.opWidth))
    val inputVecOut = Decoupled(UInt(width = p.opWidth))
    val outputVecIn = Decoupled(UInt(width = p.opWidth)).flip
    // memory read-write channels
    val memRdReq = Decoupled(new GenericMemoryRequest(pMem))
    val memRdRsp = Decoupled(new GenericMemoryResponse(pMem)).flip
    val memWrReq = Decoupled(new GenericMemoryRequest(pMem))
    val memWrDat = Decoupled(UInt(width = p.memDataWidth))
    val memWrRsp = Decoupled(new GenericMemoryResponse(pMem)).flip
  }
  val oprBytes = UInt(p.opWidth/8)
  val ptrBytes = UInt(p.ptrWidth/8)
  val colPtrBytes = ptrBytes*(io.numCols+UInt(1)) // +1 comes from CSR/CSC
  val rowIndBytes = ptrBytes*io.numNZ
  val nzBytes = oprBytes*io.numNZ
  val inputVecBytes = oprBytes*io.numCols
  val outputVecBytes = oprBytes*io.numRows

  // instantiate request interleaver for mixing reqs from read chans
  val intl = Module(new ReqInterleaver(4, pMem)).io
  // read request port driven by interleaver
  intl.reqOut <> io.memRdReq

  // TODO make in-hw size alignment optional?
  def alignToMemWidth(x: UInt): UInt = {return alignTo(p.memDataWidth/8, x)}

  // instantiate 4xread request generators, one for each SpMV data channel
  val rqColPtr = Module(new ReadReqGen(pMem, idBase, p.colPtrBurstBeats)).io
  rqColPtr.ctrl.baseAddr := io.baseColPtr
  rqColPtr.ctrl.byteCount := alignToMemWidth(colPtrBytes)
  val rqRowInd = Module(new ReadReqGen(pMem, idBase+1, p.rowIndBurstBeats)).io
  rqRowInd.ctrl.baseAddr := io.baseRowInd
  rqRowInd.ctrl.byteCount := alignToMemWidth(rowIndBytes)
  val rqNZData = Module(new ReadReqGen(pMem, idBase+2, p.nzDataBurstBeats)).io
  rqNZData.ctrl.baseAddr := io.baseNZData
  rqNZData.ctrl.byteCount := alignToMemWidth(nzBytes)
  val rqInputVec = Module(new ReadReqGen(pMem, idBase+3, p.inpVecBurstBeats)).io
  rqInputVec.ctrl.baseAddr := io.baseInputVec
  rqInputVec.ctrl.byteCount := alignToMemWidth(inputVecBytes)
  // connect read req generators to interleaver
  rqColPtr.reqs <> intl.reqIn(0)
  rqRowInd.reqs <> intl.reqIn(1)
  rqNZData.reqs <> intl.reqIn(2)
  rqInputVec.reqs <> intl.reqIn(3)
  // read request threshold controls -- if FIFO level exceeds threshold,
  // throttle this read request generator to prevent clogging
  rqColPtr.ctrl.throttle := (io.fbColPtr > io.thresColPtr)
  rqRowInd.ctrl.throttle := (io.fbRowInd > io.thresRowInd)
  rqNZData.ctrl.throttle := (io.fbNZData > io.thresNZData)
  rqInputVec.ctrl.throttle := (io.fbInputVec > io.thresInputVec)

  // define a 64-bit UInt type and filterFxn, useful for handling responses
  val filterFxn = {x: GenericMemoryResponse => x.readData}
  val genO = UInt(width=64)

  // instantiate deinterleaver
  val deintl = Module(new QueuedDeinterleaver(4, pMem, 4) {
    // adjust deinterleaver routing function -- depends on baseId
    override lazy val routeFxn = {x:UInt => x-UInt(idBase)}
  })
  io.decodeErrors := deintl.io.decodeErrors
  deintl.io.rspIn <> io.memRdRsp
  // connect deinterleaver to outputs to frontend,
  // use only actual read data from read response channel (no error checking etc.)
  val readData = deintl.io.rspOut.map(StreamFilter(_, genO, filterFxn))
  // - use downsizers to adjust stream width where appropriate
  // - use limiters on all channels to get rid of superflous (alignment) bytes
  io.colPtrOut <> StreamLimiter(StreamDownsizer(readData(0), p.ptrWidth), io.startRegular, colPtrBytes)
  io.rowIndOut <> StreamLimiter(StreamDownsizer(readData(1), p.ptrWidth), io.startRegular, rowIndBytes)
  io.nzDataOut <> StreamLimiter(readData(2), io.startRegular, nzBytes)
  io.inputVecOut <> StreamLimiter(readData(3), io.startRegular, inputVecBytes)

  // instantiate write req gen
  val rqWrite = Module(new WriteReqGen(pMem, idBase+4)).io
  rqWrite.ctrl.baseAddr := io.baseOutputVec
  rqWrite.ctrl.byteCount := alignToMemWidth(outputVecBytes)
  rqWrite.ctrl.throttle := Bool(false)
  rqWrite.reqs <> io.memWrReq   // only write channel, no interleaver needed
  io.memWrDat <> io.outputVecIn // write data directly from frontend
  // use StreamReducer to count write responses
  val wrCompl = Module(new StreamReducer(64, 0, (x,y)=>x+y)).io
  wrCompl.streamIn <> StreamFilter(io.memWrRsp, genO, filterFxn)
  wrCompl.start := io.startWrite
  wrCompl.byteCount := outputVecBytes

  // wire control + status
  val regularComps = List(rqColPtr, rqRowInd, rqNZData, rqInputVec)
  val writeComps = List(rqWrite)

  io.doneRegular := regularComps.map(x => x.stat.finished).reduce(_ & _)
  // write completion is the "most downstream" operation, use only that
  // to determine whether the whole write op is finished
  io.doneWrite := wrCompl.finished

  for(rc <- regularComps) { rc.ctrl.start := io.startRegular }
  for(wc <- writeComps) { wc.ctrl.start := io.startWrite }

  io.backendDebug := Cat(regularComps.map(x=>x.stat.finished) ++ List(wrCompl.finished))
}
