package SpMVAccel

import Chisel._
import TidbitsAXI._
import TidbitsStreams._
import TidbitsOCM._
import TidbitsDMA._

// frontend using the revised vector cache

class SpMVFrontendNewCache(val p: SpMVAccelWrapperParams) extends Module {
  val opType = UInt(width=p.opWidth)
  val idType = UInt(width=p.ptrWidth)
  val opWidthIdType = new OperandWithID(p.opWidth, p.ptrWidth)
  val opsAndId = new OperandWithID(2*p.opWidth, p.ptrWidth)
  val shadowType = new OperandWithID(1, p.ptrWidth)
  val syncOpType = new SemiringOperands(p.opWidth)

  val io = new Bundle {
    // control
    val startInit = Bool(INPUT)
    val startRegular = Bool(INPUT)
    val startWrite = Bool(INPUT)

    // status
    val doneInit = Bool(OUTPUT)
    val doneRegular = Bool(OUTPUT)
    val doneWrite = Bool(OUTPUT)

    // TODO debug+profiling outputs
    val readMissCount = UInt(OUTPUT, 32)
    val writeMissCount = UInt(OUTPUT, 32)

    // value inputs
    val numNZ = UInt(INPUT, width = 32)
    val numRows = UInt(INPUT, width = 32)
    val baseOutputVec = UInt(INPUT, width = 32)
    // data exchange with backend
    val colPtrIn = Decoupled(UInt(width = p.ptrWidth)).flip
    val rowIndIn = Decoupled(UInt(width = p.ptrWidth)).flip
    val nzDataIn = Decoupled(UInt(width = p.opWidth)).flip
    val inputVecIn = Decoupled(UInt(width = p.opWidth)).flip

    // port for res.vec reads/writes
    val mp = new GenericMemoryMasterPort(p.toMRP())
  }

  // useful functions for working with stream forks and joins
  val routeFxn = {x: OperandWithID => Mux(x.id < UInt(p.ocmDepth), UInt(0), UInt(1))}
  val forkAll = {x: OperandWithID => x}
  val forkShadow = {x: OperandWithID => OperandWithID(UInt(0, width=1), x.id)}
  val forkId = {x: OperandWithID => x.id}
  val forkOp = {x: OperandWithID => x.data}
  val readFilter = {x: GenericMemoryResponse => x.readData}
  def forkOps(x: OperandWithID) : SemiringOperands = {
    val opA = x.data(2*p.opWidth-1, p.opWidth)
    val opB = x.data(p.opWidth-1, 0)
    return SemiringOperands(p.opWidth, opA, opB)
  }
  val joinOpIdOp= {
    (a: OperandWithID, b: UInt) => OperandWithID(Cat(a.data, b), a.id)
  }
  val joinOpId = {(op: UInt, id: UInt) => OperandWithID(op, id)}
  val joinOpOp = {(a: UInt, b: UInt) => SemiringOperands(p.opWidth, a, b)}

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
  val mulOpJoin = Module(new StreamJoin(opType, opType, syncOpType, joinOpOp)).io
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
  val redJoin = Module(new StreamJoin(opType, idType, opWidthIdType, joinOpId)).io
  redJoin.inA <> productQ.deq
  redJoin.inB <> io.rowIndIn
  // TODO make own module
  val cacheM = Module(new NoWMVectorCache(p))
  val cache = cacheM.io
  cache.mem.memRdReq <> io.mp.memRdReq
  cache.mem.memRdRsp <> io.mp.memRdRsp
  cache.mem.memWrReq <> io.mp.memWrReq
  cache.mem.memWrRsp <> io.mp.memWrRsp
  // add write data queue to avoid deadlocks (cache wants req&data ready
  // at the same time)
  io.mp.memWrDat <> Queue(cache.mem.memWrDat, 16)
  cache.base := io.baseOutputVec
  cache.startInit := io.startInit
  cache.startWrite := io.startWrite
  io.doneInit := cache.done & io.startInit
  io.doneWrite := cache.done & io.startWrite
  io.readMissCount := cache.readMissCount
  io.writeMissCount := cache.writeMissCount

  // CATCH: shadow queue is narrow for NoWMVectorCache
  val shadow = Module(new UniqueQueue(1, log2Up(p.ocmDepth), p.issueWindow)).io
  val cacheEntry = Module(new StreamFork(opWidthIdType, opWidthIdType, shadowType,
                      forkAll, forkShadow)).io
  cacheEntry.in <> redJoin.out
  cacheEntry.outB <> shadow.enq

  val cacheReadFork = Module(new StreamFork(opWidthIdType, opWidthIdType, idType, forkAll, forkId)).io
  cacheEntry.outA <> cacheReadFork.in

  val cacheWaitReadQ = Module(new Queue(opWidthIdType, p.issueWindow+1)).io
  cacheReadFork.outA <> cacheWaitReadQ.enq
  cache.read.req <> Queue(cacheReadFork.outB, 1)

  val cacheReadJoin = Module(new StreamJoin(opWidthIdType, opType, opsAndId, joinOpIdOp)).io
  cacheReadJoin.inA <> cacheWaitReadQ.deq
  cacheReadJoin.inB <> cache.read.rsp

  val addFork = Module(new StreamFork(opsAndId, syncOpType, idType, forkOps, forkId)).io
  val adder = Module(p.makeAdd())
  val adderIdQ = Module(new Queue(idType, adder.latency)).io
  val addJoin = Module(new StreamJoin(opType, idType, opWidthIdType, joinOpId)).io

  cacheReadJoin.out <> addFork.in
  addFork.outA <> adder.io.in
  addFork.outB <> adderIdQ.enq
  addJoin.inA <> adder.io.out
  addJoin.inB <> adderIdQ.deq

  cache.write.req <> addJoin.out

  // pop from UniqueQueue with writeComplete and count ops
  shadow.deq.ready := cache.writeComplete

  // register for counting completed operations
  val regOpCount = Reg(init = UInt(0, 32))

  val completedOp = shadow.deq.ready & shadow.deq.valid

  when (!io.startRegular) {regOpCount := UInt(0)}
  .otherwise {when (completedOp) {regOpCount := regOpCount + UInt(1)}}

  // use op count to drive the doneRegular signal
  io.doneRegular := (regOpCount === io.numNZ)

  // TODO expose cache counters to top level
  // TODO emit statistics (hazards, etc)
}
