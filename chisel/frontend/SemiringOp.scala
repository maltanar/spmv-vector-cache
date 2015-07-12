package SpMVAccel

import Chisel._

// base class for semiring operators
// exposes a Valid-wrapped (UInt, UInt) => UInt interface, and the op latency
abstract class SemiringOp(val w: Int) extends Module {
  val io = new Bundle {
    val first = Valid(UInt(width = w)).flip
    val second = Valid(UInt(width = w)).flip
    val result = Valid(UInt(width = w))
  }
  lazy val latency: Int = 0
}

// combinatorial variants of UInt add and multiply
class OpAddCombinatorial(w: Int) extends SemiringOp(w) {
  io.result.bits := io.first.bits + io.second.bits
  io.result.valid := io.first.valid & io.second.valid
}

class OpMulCombinatorial(w: Int) extends SemiringOp(w) {
  io.result.bits := io.first.bits + io.second.bits
  io.result.valid := io.first.valid & io.second.valid
}

// 1-stage variants of UInt add and multiply
class OpAddSingleStage(w: Int) extends SemiringOp(w) {
  override lazy val latency: Int = 1
  io.result.bits := Reg(next=(io.first.bits + io.second.bits))
  io.result.valid := Reg(next=io.first.valid & io.second.valid)
}

class OpMulSingleStage(w: Int) extends SemiringOp(w) {
  override lazy val latency: Int = 1
  io.result.bits := Reg(next=(io.first.bits * io.second.bits))
  io.result.valid := Reg(next=io.first.valid & io.second.valid)
}
