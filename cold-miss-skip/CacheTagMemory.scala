package VectorCache

import Chisel._
import Literal._
import Node._

import CacheInterface._


class CacheTagMemory(lineSize: Int, depth: Int, addrBits: Int) extends Module {
  val io = new Bundle {
    val portA = new CacheDataReadWritePort(lineSize, addrBits)
    val portB = new CacheDataReadPort(lineSize, addrBits)
  }
  
  val depthBitCount = log2Up(depth)
  val tagBitCount = addrBits - depthBitCount
  // combinational-read memory for tag and valid (LUTRAM)
  val tagStorage = Mem(UInt(width=tagBitCount+1), depth, false)
  
  // writes from port A
  when(io.portA.writeEn) { tagStorage(io.portA.addr) := io.portA.dataIn }
  
  // reads from ports A and B
  io.portA.dataOut := tagStorage(io.portA.addr)
  io.portB.dataOut := tagStorage(io.portB.addr)
}
