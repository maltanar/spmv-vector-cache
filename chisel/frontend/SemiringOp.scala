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

// systolic reg to parametrize op stages flexibly
// this essentially behaves like a single-element queue with no
// fallthrough, so it can be used to add forced latency to an op
// the ready signal is still combinatorially linked to allow fast
// handshakes, like a Chisel queue with pipe=true flow=false
class SystolicReg(w: Int) extends Module {
  val io = new Bundle {
    val in = Decoupled(UInt(width = w)).flip
    val out = Decoupled(UInt(width = w))
  }
  val regValid = Reg(init = Bool(false))
  val regData = Reg(init = UInt(0, w))
  val allowNewData = (!regValid || io.out.ready)

  io.out.bits := regData
  io.out.valid := regValid
  io.in.ready := allowNewData

  when(allowNewData) {
    regData := io.in.bits
    regValid := io.in.valid
  }
}

// generate operator (defined by fxn) with n-cycle latency
// mostly intended to simulate high-latency ops, won't make much sense
// in synthesis since all "useful work" is carried out before entering
// the delay pipe anyway
class StagedUIntOp(w: Int, n: Int, fxn: (UInt, UInt) => UInt)
extends SemiringOp(w) {
  override lazy val latency: Int = n
  if(latency == 0) {
    println("StagedUIntOp needs at least 1 stage")
    System.exit(-1)
  }
  // connect transformed input to first stage
  val delayPipe = Vec.fill(n) {Module(new SystolicReg(w)).io}
  delayPipe(0).in.valid := io.in.valid
  delayPipe(0).in.bits := fxn(io.in.bits.first, io.in.bits.second)
  io.in.ready := delayPipe(0).in.ready
  // connect stages
  for(i <- 0 until n-1) {
    delayPipe(i+1).in <> delayPipe(i).out
  }
  // connect last stage to output
  io.out <> delayPipe(n-1).out
}

class DPAdder(stages: Int) extends SemiringOp(64) {
  val enableBlackBox = isVerilog()

  if(enableBlackBox) {
    // TODO generate blackbox for dbl-precision floating pt add
  } else {
    val fxn : (UInt,UInt)=>UInt = {
      // interpret input as doubles, add, then interpret as UInt
      (a,b) => chiselCast(
                          chiselCast(a)(Dbl()) + chiselCast(b)(Dbl())
                         )(UInt())
    }
    val op = Module(new StagedUIntOp(64, stages, fxn)).io
    io <> op
  }
}


class DPMultiplier(stages: Int) extends SemiringOp(64) {
  val enableBlackBox = isVerilog()

  if(enableBlackBox) {
    // TODO generate blackbox for dbl-precision floating pt mul
  } else {
    val fxn : (UInt,UInt)=>UInt = {
      // interpret input as doubles, mul, then interpret as UInt
      (a,b) => chiselCast(
                          chiselCast(a)(Dbl()) * chiselCast(b)(Dbl())
                         )(UInt())
    }
    val op = Module(new StagedUIntOp(64, stages, fxn)).io
    io <> op
  }
}
