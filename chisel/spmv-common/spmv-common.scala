package SpMVAccel

import Chisel._
import TidbitsAXI._
import TidbitsOCM._

class BaseWrapperParams() extends AXIAccelWrapperParams(
  addrWidth = 32,
  csrDataWidth = 32,
  memDataWidth = 64,
  idWidth = 6,
  numRegs = 32,
  numMemPorts = 1)

class SpMVAccelWrapperParams() extends BaseWrapperParams() {
  // bitwidths for SpMV
  val opWidth: Int = 64
  val ptrWidth: Int = 32
  // # elements for the backend-frontend FIFOs
  val colPtrFIFODepth: Int = 256
  val rowIndFIFODepth: Int = 512
  val nzDataFIFODepth: Int = 512
  val inpVecFIFODepth: Int = 256
  val outVecFIFODepth: Int = 0
  // burst beats for the backend channels
  val colPtrBurstBeats: Int = 1
  val rowIndBurstBeats: Int = 8
  val nzDataBurstBeats: Int = 8
  val inpVecBurstBeats: Int = 1
  // semiring op definitions
  //val makeAdd: () => SemiringOp = {() => new StagedUIntOp(opWidth, 1, (a,b)=>a+b)}
  //val makeMul: () => SemiringOp = {() => new StagedUIntOp(opWidth, 1, (a,b)=>a*b)}
  val makeAdd: () => SemiringOp = {() => new DPAdder(4)}
  val makeMul: () => SemiringOp = {() => new DPMultiplier(4)}
  // OCM parameters -- TODO separate into own trait/class?
  // number of contexts in context storage
  val ocmDepth = 1024
  // generate slightly different hardware depending on the backend:
  // - for Verilog, generate wrapper blackboxes for premade IP
  // - for everything else, generate Chisel-built components
  // TODO add directly to OCMParameters?
  lazy val ocmPrebuilt = if(isVerilog()) true else false
  val ocmReadLatency = 1
  val ocmName = "WrapperBRAM"+opWidth.toString+"x"+ocmDepth.toString
}

object isVerilog {
  def apply(): Boolean = {
    if(Driver.backend != null) {
      return (Driver.backend.getClass().getSimpleName() == "VerilogBackend")
    }
    else {return false}
  }
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

  // internally construct appropriate generic/prebuilt queue,
  // depending on parameters and availability
  val idString = gen.getWidth().toString + "x" + entries.toString
  val blackBoxSet = Set("32x256", "32x512", "64x256", "64x512")
  // generate prebuilt queues only when generating Verilog for UInt queues
  val enableBlackBox = isVerilog() && (gen.getClass().getSimpleName() == "UInt")

  if(blackBoxSet(idString) && enableBlackBox) {
    val bbm = Module(new BlackBox() {
      val io = new QueueIO(gen, entries)
      moduleName = "WrapperCustomQueue"+idString
      // add clock and reset
      this.addClock(Driver.implicitClock)
      this.addResetPin(Driver.implicitReset)
    }).io
    bbm <> io
  } else {
    // no blackbox for this instance, make regular queue
    val q = Module(new Queue(gen, entries)).io
    q <> io
  }
}

object alignedIncrement {
  def apply(base: Int, increment: Int, align: Int):Int = {
    var res: Int = base+increment
    val rem = res % align
    if (rem != 0) { res += align-rem}
    return res
  }
}
