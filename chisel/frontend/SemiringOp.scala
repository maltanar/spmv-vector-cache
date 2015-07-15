package SpMVAccel

import Chisel._

// base class for semiring operators
// exposes a Valid-wrapped (UInt, UInt) => UInt interface, and the op latency
abstract class SemiringOp(val w: Int) extends Module {
  val io = new Bundle {
    val first = Decoupled(UInt(width = w)).flip
    val second = Decoupled(UInt(width = w)).flip
    val result = Decoupled(UInt(width = w))
  }
  lazy val latency: Int = 0
}

// combinatorial variants of UInt add and multiply
class OpAddCombinatorial(w: Int) extends SemiringOp(w) {
  io.result.bits := io.first.bits + io.second.bits
  io.result.valid := io.first.valid & io.second.valid
  io.first.ready := io.result.ready
  io.second.ready := io.result.ready
}

class OpMulCombinatorial(w: Int) extends SemiringOp(w) {
  io.result.bits := io.first.bits + io.second.bits
  io.result.valid := io.first.valid & io.second.valid
  io.first.ready := io.result.ready
  io.second.ready := io.result.ready
}

// 1-stage variants of UInt add and multiply
class OpAddSingleStage(w: Int) extends SemiringOp(w) {
  override lazy val latency: Int = 1
  val regValid = Reg(init = Bool(false))
  val regData = Reg(init = UInt(0, w))
  val allowNewData = (!regValid || io.result.ready)

  io.result.bits := regData
  io.result.valid := regValid
  io.first.ready := allowNewData
  io.second.ready := allowNewData

  when(allowNewData) {
    regData := io.first.bits + io.second.bits
    regValid := io.first.valid & io.second.valid
  }
}

class OpMulSingleStage(w: Int) extends SemiringOp(w) {
  override lazy val latency: Int = 1
  val regValid = Reg(init = Bool(false))
  val regData = Reg(init = UInt(0, w))
  val allowNewData = (!regValid || io.result.ready)

  io.result.bits := regData
  io.result.valid := regValid
  io.first.ready := allowNewData
  io.second.ready := allowNewData

  when(allowNewData) {
    regData := io.first.bits * io.second.bits
    regValid := io.first.valid & io.second.valid
  }
}
