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
  numMemPorts = 2)

class SpMVAccelWrapperParams(arg: List[String] = List()) extends BaseWrapperParams() {
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
  val makeMul: () => SemiringOp = {() => new DPMultiplier(8)}

  lazy val suffix: String = {
    (if(enableCMS) "cms-" else "") +
    (if(enableNB) "nb-" else "") +
    ocmDepth.toString + "-" + issueWindow.toString
  }

  // try to find given key in args, return defVal otherwise
  def matchInt(key: String, defVal: Int): Int = {
    val keyStr: String = "--" + key
    for(p <- arg.sliding(2)) {
      if (p(0) == keyStr) { return p(1).toInt}
    }
    return defVal
  }

  // try to match key (present / not present)
  def matchKey(key: String, defVal: Boolean): Boolean = {
    val keyStr: String = "--" + key
    for(p <- arg) {
      if (p == keyStr) { return true}
    }
    return false
  }

  // how many simultaneously threads in flight to allow
  val issueWindow = matchInt("issueWindow", 6)
  // OCM parameters -- TODO separate into own trait/class?
  // number of contexts in context storage
  val ocmDepth = matchInt("ocmDepth", 1024)
  // generate slightly different hardware depending on the backend:
  // - for Verilog, generate wrapper blackboxes for premade IP
  // - for everything else, generate Chisel-built components
  // TODO add directly to OCMParameters?
  lazy val ocmPrebuilt = if(isVerilog()) true else false
  val ocmReadLatency = 1
  val ocmWriteLatency = 1
  val ocmName = "WrapperBRAM"+opWidth.toString+"x"+ocmDepth.toString
  val enableCMS: Boolean = matchKey("enableCMS", false)
  val enableNB: Boolean = matchKey("enableNB", false) // nonblocking cache
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


object Counter32Bit {
  def apply(cond: Bool) = {Counter(cond, scala.math.pow(2,32).toInt)._1}
}
