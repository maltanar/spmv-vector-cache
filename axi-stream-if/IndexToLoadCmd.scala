package AXIStreamTesting
import Chisel._
import Literal._

class IndexToLoadCmd(addrW: Int, bttW: Int, dataW: Int) extends Module {
  val io = new Bundle {
    // input: pure indices
    val indexIn = new DeqIO(UInt(width = addrW))
    // output: AXI DataMover read commands
    // width = 40 bits + address width
    val cmdOut = new EnqIO(UInt(width = addrW + 40))
  }
  
  io.indexIn.ready := Bool(false)
  io.cmdOut.valid := Bool(false)
  io.cmdOut.bits := Bits(0, addrW + 40)
  
  when (io.indexIn.valid & io.cmdOut.ready)
  {
    // dequeue from input
    val currentAddr = io.indexIn.deq()
    // upper bits: RSVD + TAG, both set to zero
    val cmdUpperBits = Bits(0, 8) 
    // lower bits: ignored(1byte=0) + type(bit=FIXED=0) + ignored(=0)
    val cmdLowerBits = Bits(0, 32-bttW)
    // transfer single byte
    val cmdBTT = UInt(dataW/8, bttW)
    // now assemble the command
    // upperBits + address + lowerBits + BTT
    val cmd = Cat(cmdUpperBits, Cat(currentAddr, Cat(cmdLowerBits, cmdBTT)))
    // enqueue on output
    io.cmdOut.enq(cmd)
  }
}

class IndexToLoadCmdTester(c: IndexToLoadCmd) extends Tester(c) {
  poke(c.io.indexIn.valid, 0)
  poke(c.io.cmdOut.ready, 0)
  expect(c.io.indexIn.ready, 0)
  expect(c.io.cmdOut.valid, 0)
  step(1)
  expect(c.io.indexIn.ready, 0)
  expect(c.io.cmdOut.valid, 0)
  poke(c.io.cmdOut.ready, 1)
  poke(c.io.indexIn.valid, 1)
  poke(c.io.indexIn.bits, 1)
  peek(c.io.cmdOut.bits)
  peek(c.io.cmdOut.valid)
  step(1)
  peek(c.io.cmdOut.bits)
  peek(c.io.cmdOut.valid)  
}
