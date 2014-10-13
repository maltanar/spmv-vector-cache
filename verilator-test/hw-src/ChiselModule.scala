package ChiselModule
import Chisel._
import Literal._

class ChiselModule(w: Int) extends Module {
  val io = new Bundle {
    val streamInput = new DeqIO(UInt(width = w))
    val in = Bits(INPUT, w)
    val out = UInt(OUTPUT, w)
  }
  val regExample = Reg(init = UInt(0, w))
  
  io.streamInput.ready := Bool(false)
  io.out := regExample
  
  when (io.streamInput.valid)
  {
    regExample := regExample + UInt(io.streamInput.deq())
  }
}

class ChiselModuleTester(c: ChiselModule) extends Tester(c) {
  poke(c.io.streamInput.valid, 0)
  expect(c.io.streamInput.ready, 0)
}
