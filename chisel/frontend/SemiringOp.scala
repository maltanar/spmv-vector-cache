package SpMVAccel

import Chisel._

class SemiringOperands(val w: Int) extends Bundle {
  val first = UInt(width = w)
  val second = UInt(width = w)

  override def clone = {
    new SemiringOperands(w).asInstanceOf[this.type]
  }
}

object SemiringOperands {
  def apply(w: Int, first: UInt, second: UInt) = {
    val sop = new SemiringOperands(w)
    sop.first := first
    sop.second := second
    sop
  }
}

// base class for semiring operators
// exposes a Valid-wrapped (UInt, UInt) => UInt interface, and the op latency
abstract class SemiringOp(val w: Int) extends Module {
  val io = new Bundle {
    val in = Decoupled(new SemiringOperands(w)).flip
    val out = Decoupled(UInt(width = w))
  }
  lazy val latency: Int = 0
}

// combinatorial variants of UInt add and multiply
class OpAddCombinatorial(w: Int) extends SemiringOp(w) {
  io.out.bits := io.in.bits.first + io.in.bits.second
  io.out.valid := io.in.valid
  io.in.ready := io.out.ready
}

class OpMulCombinatorial(w: Int) extends SemiringOp(w) {
  io.out.bits := io.in.bits.first * io.in.bits.second
  io.out.valid := io.in.valid
  io.in.ready := io.out.ready
}

// 1-stage variants of UInt add and multiply
class OpAddSingleStage(w: Int) extends SemiringOp(w) {
  override lazy val latency: Int = 1
  val regValid = Reg(init = Bool(false))
  val regData = Reg(init = UInt(0, w))
  val allowNewData = (!regValid || io.out.ready)

  io.out.bits := regData
  io.out.valid := regValid
  io.in.ready := allowNewData

  when(allowNewData) {
    regData := io.in.bits.first + io.in.bits.second
    regValid := io.in.valid
  }
}

class OpMulSingleStage(w: Int) extends SemiringOp(w) {
  override lazy val latency: Int = 1
  val regValid = Reg(init = Bool(false))
  val regData = Reg(init = UInt(0, w))
  val allowNewData = (!regValid || io.out.ready)

  io.out.bits := regData
  io.out.valid := regValid
  io.in.ready := allowNewData

  when(allowNewData) {
    regData := io.in.bits.first * io.in.bits.second
    regValid := io.in.valid
  }
}
