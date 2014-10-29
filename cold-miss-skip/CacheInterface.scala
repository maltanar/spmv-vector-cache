package CacheInterface
{

import Chisel._
import Literal._
import Node._

class CacheDataReadWritePort(lineSize: Int, addrBits: Int) extends Bundle {
  val addr = UInt(INPUT, width=addrBits)
  val dataIn = UInt(INPUT, width=lineSize)
  val writeEn = Bool(INPUT)
  val dataOut = UInt(OUTPUT, width=lineSize)
  override def clone = { new CacheDataReadWritePort(lineSize,addrBits).asInstanceOf[this.type] }
}

class CacheDataReadPort(lineSize: Int, addrBits: Int) extends Bundle {
  val addr = UInt(INPUT, width=addrBits)
  val dataOut = UInt(OUTPUT, width=lineSize)
  override def clone = { new CacheDataReadPort(lineSize,addrBits).asInstanceOf[this.type] }
}

class CacheReadPortIF(addrBits: Int, lineSize: Int) extends Bundle {
  // read request port
  val readReq = Decoupled(UInt(width = addrBits)).flip
  // read response data
  val readResp = Decoupled(UInt(width = lineSize))
  // read response addr (follows readResp)
  val readRespInd = UInt(OUTPUT, width = addrBits) 
  override def clone = { new CacheReadPortIF(addrBits,lineSize).asInstanceOf[this.type] }
}

class CacheWritePortIF(addrBits: Int, lineSize: Int) extends Bundle {
  // write request port
  val writeReq = Decoupled(UInt(width = addrBits)).flip
  // write data port (follows writeReq)
  // writeData should be part of writeReq, but Verilator
  // best handles 1-, 32- and 64-bit widths, so it is separated
  val writeData = UInt(INPUT, lineSize)
  override def clone = { new CacheWritePortIF(addrBits,lineSize).asInstanceOf[this.type] }
}

class MainMemReadPortIF(addrBits: Int, lineSize: Int) extends Bundle {
  // interface towards main memory, read requests
  val memReq = Decoupled(UInt(width = addrBits))
  // interface towards main memory, read responses
  val memResp = Decoupled(UInt(width = lineSize)).flip
  override def clone = { new MainMemReadPortIF(addrBits,lineSize).asInstanceOf[this.type] }
}

class MainMemWritePortIF(addrBits: Int, lineSize: Int) extends Bundle {
  // interface towards main memory, write requests
  val memWriteReq = Decoupled(UInt(width = addrBits))
  // write data associated with each request (follows memWriteReq)
  val memWriteData = UInt(OUTPUT, lineSize)
  override def clone = { new MainMemWritePortIF(addrBits,lineSize).asInstanceOf[this.type] }
}

class SinglePortCacheIF(addrBits: Int, lineSize: Int) extends Bundle {
  // interface towards processing element:
  val readPort = new CacheReadPortIF(addrBits, lineSize)
  val writePort = new CacheWritePortIF(addrBits, lineSize)
  
  // interface towards main memory, read requests
  val memRead = new MainMemReadPortIF(addrBits, lineSize)
  val memWrite = new MainMemWritePortIF(addrBits, lineSize)

  // flush cache input
  val flushCache = Bool(INPUT)
  
  // init indicator and cache stat outputs
  val cacheActive = Bool(OUTPUT)
  val readCount = UInt(OUTPUT, 32)
  val readMissCount = UInt(OUTPUT, 32)
  val writeCount = UInt(OUTPUT, 32)
  val writeMissCount = UInt(OUTPUT, 32)
  
  override def clone = { new SinglePortCacheIF(addrBits,lineSize).asInstanceOf[this.type] }
}

}
