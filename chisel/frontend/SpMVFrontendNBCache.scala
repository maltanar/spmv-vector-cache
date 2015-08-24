package SpMVAccel

import Chisel._
import TidbitsAXI._
import TidbitsStreams._
import TidbitsOCM._
import TidbitsDMA._

// frontend using a nonblocking vector cache

// adapted from J. Bachrach's "Advanced Chisel" slides
class CAMIO(entries: Int, addr_bits: Int, tag_bits: Int) extends Bundle {
  val clear_hit = Bool(INPUT)
  val is_clear_hit = Bool(OUTPUT)
  val clear_tag = Bits(INPUT, tag_bits)

  val tag = Bits(INPUT, tag_bits)
  val hit = Bool(OUTPUT)
  val hits = UInt(OUTPUT, entries)
  val valid_bits = Bits(OUTPUT, entries)
  val write = Bool(INPUT)
  val write_tag = Bits(INPUT, tag_bits)
  val hasFree = Bool(OUTPUT)
}

class CAM(entries: Int, tag_bits: Int) extends Module {
  val addr_bits = log2Up(entries)
  val io = new CAMIO(entries, addr_bits, tag_bits)
  val cam_tags = Mem(Bits(width = tag_bits), entries)
  // valid (fullness) of each slot in the CAM
  val vb_array = Reg(init = Bits(0, entries))
  // hit status for clearing
  val clearHits = Vec((0 until entries).map(i => vb_array(i) && cam_tags(i) === io.clear_tag))
  io.is_clear_hit := clearHits.toBits.orR

  // index of first free slot in the CAM (least significant first)
  val freeLocation = PriorityEncoder(~vb_array)
  // whether there are any free slots at all
  io.hasFree := orR(~vb_array)

  // produce masks to allow simultaneous write+clear
  val writeMask = Mux(io.write, UIntToOH(freeLocation), Bits(0, entries))
  val clearMask = Mux(io.clear_hit, ~(clearHits.toBits), ~Bits(0, entries))

  vb_array := (vb_array | writeMask) & clearMask

  when (io.write) { cam_tags(freeLocation) := io.write_tag }

  val hits = (0 until entries).map(i => vb_array(i) && cam_tags(i) === io.tag)
  io.valid_bits := vb_array
  io.hits := Vec(hits).toBits
  io.hit := io.hits.orR
}

class IssueWindow(entries: Int, tag_bits: Int) extends Module {
  val io = new Bundle {
    val in = Decoupled(UInt(width=tag_bits)).flip
    val rm = Decoupled(UInt(width=tag_bits)).flip
  }

  val cam = Module(new CAM(entries, tag_bits)).io
  // removal logic
  cam.clear_hit := io.rm.valid
  cam.clear_tag := io.rm.bits
  io.rm.ready := cam.is_clear_hit

  // insertion logic
  cam.tag := io.in.bits
  val canInsert = cam.hasFree & !cam.hit
  io.in.ready := canInsert
  cam.write_tag := io.in.bits
  cam.write := canInsert & io.in.valid
}

class SpMVFrontendNBCache(val p: SpMVAccelWrapperParams) extends Module {
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
    val conflictMissCount = UInt(OUTPUT, 32)
    val hazardStalls = UInt(OUTPUT, 32)
    val cacheState = UInt(OUTPUT, 32)
    val bwMon = new StreamMonitorOutIF()

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
  val cacheM = Module(new NBVectorCache(p))
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
  io.conflictMissCount := cache.conflictMissCount
  io.cacheState := cache.cacheState

  val iwTagBits = log2Up(p.ocmDepth)
  val iw = Module(new IssueWindow(p.issueWindow, iwTagBits)).io

  val cacheEntry = Module(new StreamFork(opWidthIdType, opWidthIdType, idType,
                      forkAll, forkId)).io
  cacheEntry.in <> redJoin.out
  cacheEntry.outA <> cache.read.req
  cacheEntry.outB <> iw.in

  val addFork = Module(new StreamFork(opsAndId, syncOpType, idType, forkOps, forkId)).io
  val adder = Module(p.makeAdd())
  val adderIdQ = Module(new Queue(idType, adder.latency)).io
  val addJoin = Module(new StreamJoin(opType, idType, opWidthIdType, joinOpId)).io

  cache.read.rsp <> addFork.in
  addFork.outA <> adder.io.in
  addFork.outB <> adderIdQ.enq
  addJoin.inA <> adder.io.out
  addJoin.inB <> adderIdQ.deq

  cache.write.req <> addJoin.out

  iw.rm.valid := Reg(next=cache.write.req.valid & cache.write.req.ready)
  iw.rm.bits := Reg(next=cache.write.req.bits.id(iwTagBits-1,0))

  // register for counting completed operations
  val regOpCount = Reg(init = UInt(0, 32))

  val completedOp = iw.rm.valid & iw.rm.ready

  when (!io.startRegular) {regOpCount := UInt(0)}
  .otherwise {when (completedOp) {regOpCount := regOpCount + UInt(1)}}

  // use op count to drive the doneRegular signal
  io.doneRegular := (regOpCount === io.numNZ)

  // TODO report the actual #hazard stalls
  io.hazardStalls := UInt(255)

  io.bwMon := StreamMonitor(redJoin.out, io.startRegular & !io.doneRegular)
}
