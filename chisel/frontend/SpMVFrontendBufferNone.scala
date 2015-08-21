package SpMVAccel

import Chisel._
import TidbitsAXI._
import TidbitsStreams._
import TidbitsOCM._
import TidbitsDMA._

class SpMVFrontendBufferNone(val p: SpMVAccelWrapperParams) extends Module {
  val opType = UInt(width=p.opWidth)
  val idType = UInt(width=p.ptrWidth)
  val opWidthIdType = new OperandWithID(p.opWidth, p.ptrWidth)
  val shadowType = new OperandWithID(1, p.ptrWidth)
  val syncOpType = new SemiringOperands(p.opWidth)

  val io = new Bundle {
    // control
    val startRegular = Bool(INPUT)

    // status
    val doneRegular = Bool(OUTPUT)

    // TODO debug+profiling outputs
    val issueWindow = UInt(OUTPUT, width = 32)
    val hazardStalls = UInt(OUTPUT, width = 32)
    val capacityStalls = UInt(OUTPUT, width = 32)

    // value inputs
    val numNZ = UInt(INPUT, width = 32)
    val numRows = UInt(INPUT, width = 32)
    // data exchange with backend
    val colPtrIn = Decoupled(UInt(width = p.ptrWidth)).flip
    val rowIndIn = Decoupled(UInt(width = p.ptrWidth)).flip
    val nzDataIn = Decoupled(UInt(width = p.opWidth)).flip
    val inputVecIn = Decoupled(UInt(width = p.opWidth)).flip

    // BufferNone-specific frontend ports:
    // port for res.vec reads/writes
    val mp = new GenericMemoryMasterPort(p.toMRP())
    val baseOutputVec = UInt(INPUT, width = 32)
  }
  // instantiate multiply operator
  val mul = Module(p.makeMul())
  // instantiate StreamDelta and StreamRepeatElem
  val deltaGenM = Module(new StreamDelta(p.ptrWidth))
  val deltaGen = deltaGenM.io
  deltaGen.samples <> io.colPtrIn
  val rptGenM = Module(new StreamRepeatElem(p.opWidth, p.ptrWidth))
  val rptGen = rptGenM.io
  rptGen.inElem <> io.inputVecIn
  rptGen.inRepCnt <> deltaGen.deltas

  // wire up multiplier inputs with StreamJoin
  val fxnM = {(a: UInt, b: UInt) => SemiringOperands(p.opWidth, a, b)}
  val mulOpJoin = Module(new StreamJoin(opType, opType, syncOpType, fxnM)).io
  mulOpJoin.inA <> rptGen.out
  mulOpJoin.inB <> io.nzDataIn
  mul.io.in <> mulOpJoin.out
  // uncomment to debug multiplier inputs
  /*
  when(mulOpJoin.inA.valid & mulOpJoin.inB.valid) {
    printf("MUL %x * %x\n", mulOpJoin.inA.bits, mulOpJoin.inB.bits)
  }
  */

  // add a queue to buffer multiplier results, in case of stalls
  // this will let the pipes continue a while longer
  // TODO parametrize queue size
  val productQ = Module(new Queue(UInt(width=p.opWidth), 16)).io
  productQ.enq <> mul.io.out
  // join products and rowind data to pass to reducer
  val fxnR = {(op: UInt, id: UInt) => OperandWithID(op, id)}
  val redJoin = Module(new StreamJoin(opType, idType, opWidthIdType, fxnR)).io
  redJoin.inA <> productQ.deq
  redJoin.inB <> io.rowIndIn

  io.issueWindow := UInt(p.issueWindow)
  // TODO package this bit into own module (InterleavedReduceDRAM)
  val adder = Module(p.makeAdd())

  val forkAll = {x: OperandWithID => x}
  val forkShadow = {x: OperandWithID => OperandWithID(UInt(0, width=1), x.id)}
  val forkId = {x: OperandWithID => x.id}
  val forkOp = {x: OperandWithID => x.data}

  val forkGuard = Module(new StreamFork(opWidthIdType, opWidthIdType, shadowType,
                      forkAll, forkShadow)).io
  val shadowQ = Module(new UniqueQueue(1, p.ptrWidth, p.issueWindow)).io
  forkGuard.in <> redJoin.out
  forkGuard.outB <> shadowQ.enq

  val forkSplit = Module(new StreamFork(opWidthIdType, opWidthIdType, idType,
                    forkAll, forkId)).io
  // TODO parametrize! must be big enough to accommodate issueWindow
  // (or a bit smaller, since there are queues at the end of this)
  val waitReadQ = Module(new Queue(opWidthIdType, 2+p.issueWindow)).io
  forkSplit.in <> forkGuard.outA
  forkSplit.outA <> waitReadQ.enq

  val opBytes = UInt(p.opWidth/8)
  val reqType = new GenericMemoryRequest(p.toMRP())

  def idsToReqs(ids: DecoupledIO[UInt], wr: Boolean): DecoupledIO[GenericMemoryRequest] = {
    val reqs = Decoupled(new GenericMemoryRequest(p.toMRP())).asDirectionless
    val req = reqs.bits
    req.channelID := UInt(0)  // TODO parametrize!
    req.isWrite := Bool(wr)
    req.addr := io.baseOutputVec + ids.bits * opBytes
    req.numBytes := opBytes
    req.metaData := UInt(0)

    reqs.valid := ids.valid
    ids.ready := reqs.ready

    return reqs
  }
  // make read request stream from id stream
  io.mp.memRdReq <> Queue(idsToReqs(forkSplit.outB, false), 2)

  // TODO parametrize depths
  val opQ = Module(new Queue(opType, 4)).io
  val idQ = Module(new Queue(idType, 4)).io

  val forkOpId = Module(new StreamFork(opWidthIdType, opType, idType,
                      forkOp, forkId)).io
  // fork op-id stream into op and id streams
  forkOpId.in <> waitReadQ.deq
  opQ.enq <> forkOpId.outA
  idQ.enq <> forkOpId.outB

  // join operands for addition
  // op A: loaded "old sum" from memory (filtered read response)
  // op B: new contributions (opQ)
  val filterFxn = {x: GenericMemoryResponse => x.readData}
  val fxn = {(a: UInt, b: UInt) => SemiringOperands(p.opWidth, a, b)}
  val addOpJoin = Module(new StreamJoin(opType, opType, syncOpType, fxn)).io
  addOpJoin.inA <> StreamFilter(io.mp.memRdRsp, opType, filterFxn)
  addOpJoin.inB <> opQ.deq

  addOpJoin.out <> adder.io.in
  // uncomment to debug adder inputs
  //when(adder.io.in.valid) {printf("ADD %x + %x\n", adder.io.in.bits.first, adder.io.in.bits.second)}

  // make write requests and connect write data
  io.mp.memWrReq <> idsToReqs(idQ.deq, true)

  io.mp.memWrDat <> adder.io.out

  io.mp.memWrRsp.ready := io.startRegular
  shadowQ.deq.ready := io.mp.memWrRsp.valid

  // register for counting completed operations
  val regOpCount = Reg(init = UInt(0, 32))
  //io.opCount := regOpCount

  val completedOp = shadowQ.deq.ready & shadowQ.deq.valid

  when (!io.startRegular) {regOpCount := UInt(0)}
  .otherwise {
    when (completedOp) {regOpCount := regOpCount + UInt(1)}
  }

  // use op count to drive the doneRegular signal
  io.doneRegular := (regOpCount === io.numNZ)

  // emit some statistics
  io.hazardStalls := Counter(shadowQ.hazard, scala.math.pow(2,32).toInt)._1
  val capStallIndicator = !shadowQ.hazard & !shadowQ.enq.ready & shadowQ.enq.valid
  io.capacityStalls := Counter(capStallIndicator, scala.math.pow(2,32).toInt)._1
}
