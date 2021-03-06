package SpMVAccel

import Chisel._
import TidbitsAXI._
import TidbitsStreams._
import TidbitsOCM._
import TidbitsDMA._

// frontend for selective buffering:
// - first <ocmDepth> elements are stored in OCM for fast access
// - all other elements are stored in DRAM

// TODO add counters for profiling

class SpMVFrontendBufferSel(val p: SpMVAccelWrapperParams) extends Module {
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

    // debug+profiling outputs
    val hazardStallsOCM = UInt(OUTPUT, width = 32)
    val capacityStallsOCM = UInt(OUTPUT, width = 32)
    val hazardStallsDDR = UInt(OUTPUT, width = 32)
    val capacityStallsDDR = UInt(OUTPUT, width = 32)

    // value inputs
    val numNZ = UInt(INPUT, width = 32)
    val numRows = UInt(INPUT, width = 32)
    val baseOutputVec = UInt(INPUT, width = 32)
    // data exchange with backend
    val colPtrIn = Decoupled(UInt(width = p.ptrWidth)).flip
    val rowIndIn = Decoupled(UInt(width = p.ptrWidth)).flip
    val nzDataIn = Decoupled(UInt(width = p.opWidth)).flip
    val inputVecIn = Decoupled(UInt(width = p.opWidth)).flip
    // only the cached portion of the result vec. will be output
    // through the outputVecOut channel
    val outputVecOut = Decoupled(UInt(width = p.opWidth))

    // BufferSel-specific frontend ports:
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


  // split incoming (id, contr) pairs based on the id
  // * id < ocmDepth is directed to OCM pipe
  // * id >= ocmDepth is directed to DRAM pipe
  val pipeSw = Module(new StreamDeinterleaverQueued(2, opWidthIdType, routeFxn, 2)).io
  pipeSw.in <> redJoin.out
  val opsOCM = pipeSw.out(0)
  val opsDDR = pipeSw.out(1)

  // =========================================================================
  // =========================================================================
  // =========================================================================
  // components for the shared adder

  val addInterleave = Module(new StreamInterleaver(2, opsAndId)).io
  val addInOCM = addInterleave.in(0)
  val addInDDR = addInterleave.in(1)

  val addFork = Module(new StreamFork(opsAndId, syncOpType, idType, forkOps, forkId)).io
  val adder = Module(p.makeAdd())
  val adderIdQ = Module(new Queue(idType, adder.latency)).io
  val addJoin = Module(new StreamJoin(opType, idType, opWidthIdType, joinOpId)).io
  val addDeinterleave = Module(new StreamDeinterleaverQueued(2, opWidthIdType, routeFxn,2)).io

  addInterleave.out <> addFork.in
  // uncomment to debug adder inputs
  //when(adder.io.in.valid) {printf("ADD %x + %x\n", adder.io.in.bits.first, adder.io.in.bits.second)}
  addFork.outA <> adder.io.in
  addFork.outB <> adderIdQ.enq
  addJoin.inA <> adder.io.out
  addJoin.inB <> adderIdQ.deq
  addDeinterleave.in <> addJoin.out

  val addOutOCM = addDeinterleave.out(0)
  val addOutDDR = addDeinterleave.out(1)

  // =========================================================================
  // =========================================================================
  // =========================================================================
  // components for the OCM pipe
  val pOCM = new OCMParameters( p.ocmDepth*p.opWidth, p.opWidth, p.opWidth, 2,
                                p.ocmReadLatency)
  val ocm = Module(new OCMAndController(pOCM, p.ocmName, p.ocmPrebuilt)).io
  // ocm init/dump signal connections
  ocm.mcif.start := io.startInit | io.startWrite
  ocm.mcif.mode := Mux(io.startWrite, UInt(1), UInt(0))
  // set fill port to an always-zero stream
  // TODO make result vec. initial value customizable?
  ocm.mcif.fillPort.valid := Bool(true)
  ocm.mcif.fillPort.bits := UInt(0)
  // set up fill/dump start+range
  ocm.mcif.fillDumpStart := UInt(0)
  // use min(numRows, ocmDepth) as the fill/dump count for the OCM
  ocm.mcif.fillDumpCount := Mux(io.numRows < UInt(p.ocmDepth), io.numRows, UInt(p.ocmDepth))
  // connect OCMC dump port to result vec. output
  io.outputVecOut <> ocm.mcif.dumpPort
  // init and write done signals driven directly by the OCMC
  io.doneInit := ocm.mcif.done & io.startInit
  io.doneWrite := ocm.mcif.done & io.startWrite

  val loadPort = ocm.ocmUser(0)
  val savePort = ocm.ocmUser(1)
  loadPort.req.writeEn := Bool(false)
  loadPort.req.writeData := UInt(0)
  // TODO explicitly parametrize OCM issue window?
  lazy val ocmIssue = adder.latency + p.ocmReadLatency + p.ocmWriteLatency
  val ocmShadow = Module(new UniqueQueue(1, p.ptrWidth, ocmIssue)).io
  val ocmWaitReadQ = Module(new Queue(opWidthIdType, p.ocmReadLatency+1)).io
  val forkOCMGuard = Module(new StreamFork(opWidthIdType, opWidthIdType, shadowType,
                      forkAll, forkShadow)).io
  forkOCMGuard.in <> opsOCM
  forkOCMGuard.outB <> ocmShadow.enq
  forkOCMGuard.outA <> ocmWaitReadQ.enq
  // piggyback OCM read port
  loadPort.req.addr := ocmShadow.enq.bits.id
  val doOCMRead = ocmShadow.enq.valid & ocmShadow.enq.ready
  // generate read valid signal
  val ocmReadValid = ShiftRegister(doOCMRead, p.ocmReadLatency)
  // there may be backpressure since we are sharing the adder with the
  // DRAM pipe --
  // ocm cannot respond to backpressure, so queue resulting read data
  // TODO is 8 elements too big? max stalls?
  val ocmReadData = Module(new Queue(opType, 8)).io
  ocmReadData.enq.bits := loadPort.rsp.readData
  ocmReadData.enq.valid := ocmReadValid

  val ocmAddJoin = Module(new StreamJoin(opWidthIdType, opType, opsAndId, joinOpIdOp)).io
  ocmAddJoin.inA <> ocmWaitReadQ.deq
  ocmAddJoin.inB <> ocmReadData.deq

  // connect to interleaver
  ocmAddJoin.out <> addInOCM

  // write results to OCM
  addOutOCM.ready := io.startRegular
  savePort.req.writeEn := addOutOCM.valid
  savePort.req.addr := addOutOCM.bits.id
  savePort.req.writeData := addOutOCM.bits.data

  // remove from shadow queue upon write completion
  // generate OCM write completion with shift register
  ocmShadow.deq.ready := ShiftRegister(savePort.req.writeEn, p.ocmWriteLatency)

  // register for counting completed OCM operations
  val regOCMOpCount = Reg(init = UInt(0, 32))

  val completedOCMOp = ocmShadow.deq.ready & ocmShadow.deq.valid

  when (!io.startRegular) {regOCMOpCount := UInt(0)}
  .otherwise {when (completedOCMOp) {regOCMOpCount := regOCMOpCount + UInt(1)}}

  // =========================================================================
  // =========================================================================
  // =========================================================================
  // components for the DDR pipe

  val ddrGuard = Module(new StreamFork(opWidthIdType, opWidthIdType, shadowType,
                      forkAll, forkShadow)).io
  val ddrShadow = Module(new UniqueQueue(1, p.ptrWidth, p.issueWindow)).io
  ddrGuard.in <> opsDDR
  ddrGuard.outB <> ddrShadow.enq

  val ddrIdSplit = Module(new StreamFork(opWidthIdType, opWidthIdType, idType,
                    forkAll, forkId)).io
  // TODO parametrize! must be big enough to accommodate issueWindow
  val ddrWaitRead = Module(new Queue(opWidthIdType, 2+p.issueWindow)).io
  ddrIdSplit.in <> ddrGuard.outA
  ddrIdSplit.outA <> ddrWaitRead.enq

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
  io.mp.memRdReq <> idsToReqs(ddrIdSplit.outB, false)

  val ddrAddJoin = Module(new StreamJoin(opWidthIdType, opType, opsAndId, joinOpIdOp)).io
  ddrAddJoin.inA <> ddrWaitRead.deq
  ddrAddJoin.inB <> StreamFilter(io.mp.memRdRsp, opType, readFilter)

  // connect to interleaver
  ddrAddJoin.out <> addInDDR

  // add DDR write logic for result + remove from shadow queue
  val ddrWriteFork = Module(new StreamFork(opWidthIdType, opType, idType, forkOp, forkId)).io
  ddrWriteFork.in <> addOutDDR

  // make write requests and connect write data
  // note the queue on the write data: this is to avoid deadlock situations
  // due to the ddrWriteFork forcing the waddr/wdata streams to be both ready
  io.mp.memWrDat <> Queue(ddrWriteFork.outA, 2)
  io.mp.memWrReq <> idsToReqs(ddrWriteFork.outB, true)

  // remove completed ops from shadow queue
  io.mp.memWrRsp.ready := io.startRegular
  ddrShadow.deq.ready := io.mp.memWrRsp.valid

  // register for counting completed DDR operations
  val regDDROpCount = Reg(init = UInt(0, 32))

  val completedDDROp = ddrShadow.deq.ready & ddrShadow.deq.valid

  when (!io.startRegular) {regDDROpCount := UInt(0)}
  .otherwise {when (completedDDROp) {regDDROpCount := regDDROpCount + UInt(1)}}

  // use op counts to drive the doneRegular signal
  val totalOps = regDDROpCount + regOCMOpCount
  io.doneRegular := (totalOps === io.numNZ)

  // emit statistics (hazards, etc)
  io.hazardStallsOCM := Counter32Bit(ocmShadow.hazard)
  io.hazardStallsDDR := Counter32Bit(ddrShadow.hazard)
  val ocmCapStall = !ocmShadow.hazard & ocmShadow.enq.valid & !ocmShadow.enq.ready
  val ddrCapStall = !ddrShadow.hazard & ddrShadow.enq.valid & !ddrShadow.enq.ready
  io.capacityStallsOCM := Counter32Bit(ocmCapStall)
  io.capacityStallsDDR := Counter32Bit(ddrCapStall)
}
