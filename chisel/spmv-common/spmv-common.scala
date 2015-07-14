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

class CustomQueue[T <: Data](gen: T, entries: Int) extends Module {
  val io = new QueueIO(gen, entries)

  // TODO internally construct appropriate generic/prebuilt queue,
  // depending on parameters
  val idString = gen.getWidth().toString + "x" + entries.toString
  val blackBoxSet = Set("32x256", "32x512", "64x256", "64x512")

  if(blackBoxSet(idString)) {
    val bbm = Module(new BlackBox() {
      val io = new QueueIO(gen, entries)
      // TODO rename signals to match Xilinx template
      moduleName = "CustomQueue"+idString
    }).io
    bbm <> io
  } else {
    // no blackbox for this instance, make regular queue
    val q = Module(new Queue(gen, entries)).io
    q <> io
  }
}
