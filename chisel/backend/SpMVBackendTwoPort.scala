package SpMVAccel

import Chisel._
import TidbitsDMA._
import TidbitsAXI._
import TidbitsStreams._

class SpMVBackendTwoPort(val p: SpMVAccelWrapperParams, val idBase: Int) extends Module {
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
    // memory port access for the backend
    val mp0 = new GenericMemoryMasterPort(pMem)
    val mp1 = new GenericMemoryMasterPort(pMem)
    // random access port for the frontend
    val randAcc = new GenericMemorySlavePort(pMem)
  }
  val oprBytes = UInt(p.opWidth/8)
  val ptrBytes = UInt(p.ptrWidth/8)
  val colPtrBytes = ptrBytes*(io.numCols+UInt(1)) // +1 comes from CSR/CSC
  val rowIndBytes = ptrBytes*io.numNZ
  val nzBytes = oprBytes*io.numNZ
  val inputVecBytes = oprBytes*io.numCols
  lazy val outputVecBytes = oprBytes*io.numRows

  // instantiate request interleaver for mixing reqs from read chans
  val intl = Module(new ReqInterleaver(3, pMem)).io
  // read request port 0 driven by interleaver
  intl.reqOut <> io.mp0.memRdReq

  // TODO make in-hw size alignment optional?
  def alignToMemWidth(x: UInt): UInt = {return alignTo(p.memDataWidth/8, x)}

  // instantiate 4xread request generators, one for each SpMV data channel
  val rqColPtr = Module(new ReadReqGen(pMem, idBase, p.colPtrBurstBeats)).io
  rqColPtr.ctrl.baseAddr := io.baseColPtr
  rqColPtr.ctrl.byteCount := alignToMemWidth(Reg(init=UInt(0,32), next=colPtrBytes))
  val rqRowInd = Module(new ReadReqGen(pMem, idBase+1, p.rowIndBurstBeats)).io
  rqRowInd.ctrl.baseAddr := io.baseRowInd
  rqRowInd.ctrl.byteCount := alignToMemWidth(Reg(init=UInt(0,32), next=rowIndBytes))
  val rqInputVec = Module(new ReadReqGen(pMem, idBase+2, p.inpVecBurstBeats)).io
  rqInputVec.ctrl.baseAddr := io.baseInputVec
  rqInputVec.ctrl.byteCount := alignToMemWidth(Reg(init=UInt(0,32), next=inputVecBytes))
  // connect read req generators to interleaver
  rqColPtr.reqs <> intl.reqIn(0)
  rqRowInd.reqs <> intl.reqIn(1)
  rqInputVec.reqs <> intl.reqIn(2)

  // define a 64-bit UInt type and filterFxn, useful for handling responses
  val filterFxn = {x: GenericMemoryResponse => x.readData}
  val genO = UInt(width=64)

  // instantiate deinterleaver
  val deintl = Module(new QueuedDeinterleaver(3, pMem, 4) {
    // adjust deinterleaver routing function -- depends on baseId
    override lazy val routeFxn = {x:UInt => x-UInt(idBase)}
  })
  io.decodeErrors := deintl.io.decodeErrors
  deintl.io.rspIn <> io.mp0.memRdRsp
  // connect deinterleaver to outputs to frontend,
  // use only actual read data from read response channel (no error checking etc.)
  val readData = deintl.io.rspOut.map(StreamFilter(_, genO, filterFxn))
  // - use downsizers to adjust stream width where appropriate
  // - use limiters on all channels to get rid of superflous (alignment) bytes
  io.colPtrOut <> StreamLimiter(StreamDownsizer(readData(0), p.ptrWidth), io.startRegular, colPtrBytes)
  io.rowIndOut <> StreamLimiter(StreamDownsizer(readData(1), p.ptrWidth), io.startRegular, rowIndBytes)
  io.inputVecOut <> StreamLimiter(readData(2), io.startRegular, inputVecBytes)

  // instantiate write req gen
  val rqWrite = Module(new WriteReqGen(pMem, idBase)).io
  rqWrite.ctrl.baseAddr := io.baseOutputVec
  rqWrite.ctrl.byteCount := alignToMemWidth(outputVecBytes)
  rqWrite.ctrl.throttle := Bool(false)
  rqWrite.reqs <> io.mp0.memWrReq   // only write channel, no interleaver needed
  io.mp0.memWrDat <> io.outputVecIn // write data directly from frontend
  // use StreamReducer to count write responses
  val wrCompl = Module(new StreamReducer(64, 0, (x,y)=>x+y)).io
  wrCompl.streamIn <> StreamFilter(io.mp0.memWrRsp, genO, filterFxn)
  wrCompl.start := io.startWrite
  wrCompl.byteCount := outputVecBytes

  // ======================== nz data and rand.acc. logic ======================
  // randAcc gets the entire write part of memory port 1
  io.mp1.memWrReq <> io.randAcc.memWrReq
  io.mp1.memWrDat <> io.randAcc.memWrDat
  io.mp1.memWrRsp <> io.randAcc.memWrRsp

  // request interleaver for mixing read reqs from nz and randAcc
  val randAccIntl = Module(new ReqInterleaver(2, pMem)).io
  randAccIntl.reqOut <> io.mp1.memRdReq
  // TODO use baseId for nz data req and randAcc too
  val rqNZData = Module(new ReadReqGen(pMem, 1, p.nzDataBurstBeats)).io
  rqNZData.ctrl.baseAddr := io.baseNZData
  rqNZData.ctrl.byteCount := alignToMemWidth(Reg(init=UInt(0,32), next=nzBytes))
  // TODO this assumes randAcc always uses read req id 0
  randAccIntl.reqIn(0) <> Queue(io.randAcc.memRdReq, 2)
  randAccIntl.reqIn(1) <> rqNZData.reqs

  // deinterleaver for nzdata and randAcc
  val randAccDeintl = Module(new QueuedDeinterleaver(2, pMem, 4) {
    override lazy val routeFxn = {x:UInt => x}
  })
  randAccDeintl.io.rspIn <> io.mp1.memRdRsp
  randAccDeintl.io.rspOut(0) <> io.randAcc.memRdRsp
  io.nzDataOut <> StreamLimiter(StreamFilter(randAccDeintl.io.rspOut(1), genO, filterFxn), io.startRegular, nzBytes)

  // ========================= completion/status logic =========================

  // wire control + status
  val regularComps = List(rqColPtr, rqRowInd, rqInputVec, rqNZData)
  val writeComps = List(rqWrite)

  io.doneRegular := regularComps.map(x => x.stat.finished).reduce(_ & _)
  // write completion is the "most downstream" operation, use only that
  // to determine whether the whole write op is finished
  io.doneWrite := wrCompl.finished

  for(rc <- regularComps) { rc.ctrl.start := io.startRegular }
  for(wc <- writeComps) { wc.ctrl.start := io.startWrite }

  io.backendDebug := Cat(regularComps.map(x=>x.stat.finished) ++ List(wrCompl.finished))


  // ======================= backend throttling logic ==========================
  // TODO parametrize this as bytes instead
  // keep track of in-flight requests (each request = 8 bytes here)
  val channelReqsInFlight = Vec.fill(4){ Reg(init = UInt(0, 32)) }
  // transaction indicators for requests/responses on channel i
  def newReq(i: Int): Bool = { regularComps(i).reqs.valid & regularComps(i).reqs.ready }
  def newRsp(i: Int): Bool = {
    if(i == 3) { io.nzDataOut.ready & io.nzDataOut.valid }
    else { deintl.io.rspOut(i).valid & deintl.io.rspOut(i).ready }
  }
  // update in-flight req. count for each channel
  for(i <- 0 until 4) {
    // new requests (derived from burst length of the request)
    val reqB = Mux(newReq(i), regularComps(i).reqs.bits.numBytes >> UInt(3), UInt(0))
    // new responses (max 1 response at a time)
    val rspB = Mux(newRsp(i), UInt(1), UInt(0) )
    // update in-flight requests
    channelReqsInFlight(i) := channelReqsInFlight(i) + reqB - rspB
  }

  // read request threshold controls -- if requests exceeds FIFO capacity,
  // throttle this read request generator to prevent clogging
  // TODO parametrize this 2 as the memWidth/ptr ratio
  val colPtrFree = Mux(io.fbColPtr < io.thresColPtr, UInt(p.colPtrFIFODepth)-io.fbColPtr, UInt(0))
  val rowIndFree = Mux(io.fbRowInd < io.thresRowInd, UInt(p.rowIndFIFODepth)-io.fbRowInd, UInt(0))
  val nzDataFree = Mux(io.fbNZData < io.thresNZData, UInt(p.nzDataFIFODepth)-io.fbNZData, UInt(0))
  val inputVecFree = Mux(io.fbInputVec < io.thresInputVec, UInt(p.inpVecFIFODepth)-io.fbInputVec, UInt(0))

  rqColPtr.ctrl.throttle := Reg(next=UInt(2)*channelReqsInFlight(0) >= colPtrFree)
  rqRowInd.ctrl.throttle := Reg(next=UInt(2)*channelReqsInFlight(1) >= rowIndFree)
  rqInputVec.ctrl.throttle := Reg(next=channelReqsInFlight(2) >= inputVecFree)
  rqNZData.ctrl.throttle := Reg(next=channelReqsInFlight(3) >= nzDataFree)
}
