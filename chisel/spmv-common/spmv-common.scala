package SpMVAccel

import Chisel._
import TidbitsAXI._

class BaseWrapperParams() extends AXIAccelWrapperParams(
  addrWidth = 32,
  csrDataWidth = 32,
  memDataWidth = 64,
  idWidth = 6,
  numRegs = 32)

class SpMVAccelWrapperParams() extends BaseWrapperParams() {
  // bitwidths for SpMV
  val opWidth: Int = 64
  val ptrWidth: Int = 32
  // # elements for the backend-frontend FIFOs
  val colPtrFIFODepth: Int = 256
  val rowIndFIFODepth: Int = 512
  val nzDataFIFODepth: Int = 512
  val inpVecFIFODepth: Int = 256
  // burst beats for the backend channels
  val colPtrBurstBeats: Int = 1
  val rowIndBurstBeats: Int = 8
  val nzDataBurstBeats: Int = 8
  val inpVecBurstBeats: Int = 1
  // TODO add more params: caching type, OCM depth...
  val makeAdd: () => SemiringOp = {() => new OpAddCombinatorial(opWidth)}
  val makeMul: () => SemiringOp = {() => new OpMulCombinatorial(opWidth)}
}


// size alignment in hardware
// if lower bits are not zero (=not aligned), increment upper bits by one,
// concatenate zeroes as the lower bits and return
object alignTo {
  def apply(align: Int, x: UInt): UInt = {
    val numZeroAddrBits = log2Up(align)
    val numOtherBits = x.getWidth()-numZeroAddrBits
    val lower = x(numZeroAddrBits-1, 0)
    val upper = x(x.getWidth()-1, numZeroAddrBits)
    val isAligned = (lower === UInt(0))
    return Mux(isAligned, x, Cat(upper+UInt(1), UInt(0, width = numZeroAddrBits)))
  }
}
