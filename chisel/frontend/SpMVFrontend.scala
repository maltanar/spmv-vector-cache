package SpMVAccel

import Chisel._
import TidbitsAXI._
import TidbitsStreams._
import TidbitsOCM._

class SpMVFrontend(val p: SpMVAccelWrapperParams) extends Module {
  val io = new Bundle {
    // TODO mode setting input (init/regular/dump)
    // data exchange with backend
    val colPtrIn = Decoupled(UInt(width = p.ptrWidth)).flip
    val rowIndIn = Decoupled(UInt(width = p.ptrWidth)).flip
    val nzDataIn = Decoupled(UInt(width = p.opWidth)).flip
    val inputVecIn = Decoupled(UInt(width = p.opWidth)).flip
    val outputVecOut = Decoupled(UInt(width = p.opWidth))
  }
  // TODO connect backpressure signals!
  // instantiate multiply and add operators
  val mul = Module(p.makeMul())
  val add = Module(p.makeAdd())

  // instantiate StreamDelta and StreamRepeatElem
  val deltaGen = Module(new StreamDelta(p.ptrWidth)).io
  deltaGen.samples <> io.colPtrIn
  val rptGen = Module(new StreamRepeatElem(p.opWidth, p.ptrWidth)).io
  rptGen.inElem <> io.nzDataIn
  rptGen.inRepCnt <> deltaGen.deltas

  // multiply operators on nz data and (filtered) input vector
  mul.io.first.bits := rptGen.out.bits
  mul.io.first.valid := rptGen.out.valid
  mul.io.second.bits := io.nzDataIn.bits
  mul.io.second.valid := io.nzDataIn.valid

  // add a queue to buffer multiplier results, in case of hazard stalls
  // this will let the pipes continue a while longer
  // TODO parametrize queue size
  val partialProductQ = Module(new Queue(UInt(width=p.opWidth), 16)).io
  partialProductQ.enq.valid := mul.io.result.valid
  partialProductQ.enq.bits := mul.io.result.bits

  // TODO instantiate context store with proper parametrization
  // for now we just instantiate a fixed-depth OCM+controller
  val csp = new OCMParameters(64*1024, 64, 64, 2, 1)
  val contextStore = Module(new OCMAndController(csp, "WrapperBRAM64x1024", true)).io
  // TODO pass up controller interface to frontend io
  // TODO connect fill+dump ports

  // instantiate HazardGuard
  // TODO add context store min latencies to this instead of +1?
  // (how should this work for the cache?)
  val latencyRAW = add.latency + 1
  val guard = Module(new HazardGuard(p.ptrWidth, latencyRAW)).io
  guard.streamIn <> io.rowIndIn

  // use context store port 0 for reads
  contextStore.ocmUser(0).req.addr := guard.streamOut.bits
  contextStore.ocmUser(0).req.writeEn := Bool(false)
  contextStore.ocmUser(0).req.writeData := UInt(0)

  add.io.first.bits := contextStore.ocmUser(0).rsp.readData
  // TODO parametrize context read latency
  add.io.first.valid := ShiftRegister(guard.streamOut.valid, 1)
  add.io.second.bits := partialProductQ.deq.bits
  add.io.second.valid := partialProductQ.deq.valid

  // TODO parametrize context read latency
  val delayedInds = ShiftRegister(guard.streamOut.bits, 1+add.latency)

  // use context store port 1 for writes
  contextStore.ocmUser(1).req.addr := delayedInds
  contextStore.ocmUser(1).req.writeEn := add.io.result.valid
  contextStore.ocmUser(1).req.writeData := add.io.result.bits


  // TODO emit statistics (hazards, etc)
  // TODO finish wire-up + test
}
