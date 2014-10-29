package VectorCache

import Chisel._
import Literal._
import Node._

import CacheInterface._


class CacheDataMemory(lineSize: Int, depth: Int, addrBits: Int) extends Module {
  val io = new Bundle {
    val portA = new CacheDataReadWritePort(lineSize, addrBits)
    val portB = new CacheDataReadWritePort(lineSize, addrBits)
  }
  
  // enable sequential reads for data storage (to infer BRAM)
  val cacheLines = Mem(UInt(width=lineSize), depth, true)
  
  // default read outputs
  io.portA.dataOut := Reg(next=cacheLines(io.portA.addr))
  io.portB.dataOut := Reg(next=cacheLines(io.portB.addr))
  
  when(io.portA.writeEn) { cacheLines(io.portA.addr) := io.portA.dataIn }
  
  when(io.portB.writeEn) { cacheLines(io.portB.addr) := io.portB.dataIn }
}
