package SpMVAccel

import Chisel._
import TidbitsOCM._

// single-port, synchronous read/write readMem
// should be close enough to infer block RAM for the cache tags;
// at least Vivado seems to be happy.

class WMTagRAM(w: Int, depth: Int) extends Module {
  val addrBits = log2Up(depth)
  val io = new OCMSlaveIF(w, w, addrBits)

  val mem = Mem(UInt(width = w), depth, true)

  when(io.req.writeEn) {
    mem(io.req.addr) := io.req.writeData
  }

  io.rsp.readData := Reg(next=mem(io.req.addr))
}
