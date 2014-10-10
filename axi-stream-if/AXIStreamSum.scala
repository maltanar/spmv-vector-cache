package AXIStreamTesting
import Chisel._
import Literal._

class AXIStreamSum(w: Int) extends Module {
  val io = new Bundle {
    // interface towards processing element
    val streamInput = new DeqIO(UInt(width = w))
    // init indicator and cache stat outputs
    val keep = Bits(INPUT, w/8)
    val accelID = UInt(OUTPUT, w)
    val streamSum = UInt(OUTPUT, w)
    val elementCnt = UInt(OUTPUT, w)
  }
  // register for summation
  val streamSum = Reg(init = UInt(0, w))
  val elementCnt = Reg(init = UInt(0, w))
  
  io.streamSum := streamSum
  io.elementCnt := elementCnt
  io.accelID := UInt("hdeadbeef")
  io.streamInput.ready := Bool(false)
  
  when (io.streamInput.valid)
  {
    streamSum := streamSum + UInt(io.streamInput.deq())
    elementCnt := elementCnt + UInt(1)
  }
}

class AXIStreamSumTester(c: AXIStreamSum) extends Tester(c) {
  poke(c.io.streamInput.valid, 0)
  expect(c.io.streamInput.ready, 0)
  expect(c.io.accelID, 0xdeadbeefL)
  var sum = 0
  for (i <- 1 to 10)
  {
    sum = sum + i
    poke(c.io.streamInput.valid, 1)
    poke(c.io.streamInput.bits, i)
    expect(c.io.streamInput.ready, 1)
    step(1)
    expect(c.io.elementCnt, i)
    expect(c.io.streamSum, sum)
  }
}
