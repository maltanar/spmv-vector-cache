package SpMVAccel

import Chisel._
import TidbitsAXI._

class BaseWrapperParams() extends AXIAccelWrapperParams(
  addrWidth = 32,
  csrDataWidth = 32,
  memDataWidth = 64,
  idWidth = 6,
  numRegs = 18)

class SpMVAccelWrapperParams() extends BaseWrapperParams() {
  val opWidth: Int = 64
  val ptrWidth: Int = 32
  val colPtrFIFODepth: Int = 256
  val rowIndFIFODepth: Int = 512
  val nzDataFIFODepth: Int = 512
  // TODO add more params: caching type, op instantiators, OCM depth,
  // op latency information...
}
