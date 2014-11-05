package VectorCache
import Chisel._

class FPGASynResWrapper(lineSize: Int, depth: Int, addrBits: Int) extends Module {
  val io = new Bundle {
    val dataIn32 = UInt(INPUT, 32)
    val dataOut32 = UInt(OUTPUT, 32)
    val dataIn64 = UInt(INPUT, 64)
    val dataOut64 = UInt(OUTPUT, 64)
    val dataIn1 = UInt(INPUT, 1)
    val dataOut1 = UInt(OUTPUT, 1)
    val shiftEnable = Bits(INPUT, 1)
  }
  // device under test
  val cache = Module(new ColdMissSkipVectorCache(lineSize, depth, addrBits))
  
  // "scan chain" registers
  val regs64_toCache = Vec.fill(2) { Reg(init=UInt(0, width=64)) }
  val regs64_fromCache = Vec.fill(2) { Reg(init=UInt(0, width=64)) }
  
  val regs32_toCache = Vec.fill(2) { Reg(init=UInt(0, width=32)) }
  val regs32_fromCache = Vec.fill(8) { Reg(init=UInt(0, width=32)) }
  
  val regs1_toCache = Vec.fill(7) { Reg(init=UInt(0, width=1)) }
  val regs1_fromCache = Vec.fill(7) { Reg(init=UInt(0, width=1)) }
  
  
  // 64 bits, from tester to cache
  cache.io.writePort.writeData := regs64_toCache(0)
  cache.io.memRead.memResp.bits := regs64_toCache(1)
  
  // 64 bits, from cache to tester
  regs64_fromCache(0) := cache.io.readPort.readResp.bits
  regs64_fromCache(1) := cache.io.memWrite.memWriteData
  
  // 32 bits, from tester to cache
  cache.io.readPort.readReq.bits := regs32_toCache(0)
  cache.io.writePort.writeReq.bits := regs32_toCache(1)
  
  // 32 bits, from cache to tester
  regs32_fromCache(0) := cache.io.readPort.readRespInd
  regs32_fromCache(1) := cache.io.memRead.memReq.bits
  regs32_fromCache(2) := cache.io.memWrite.memWriteReq.bits
  /*regs32_fromCache(3) := cache.io.readCount
  regs32_fromCache(4) := cache.io.readMissCount
  regs32_fromCache(5) := cache.io.writeCount
  regs32_fromCache(6) := cache.io.writeMissCount
  regs32_fromCache(7) := cache.io.coldSkipCount*/
  
  // 1 bit, from tester to cache
  cache.io.readPort.readReq.valid := regs1_toCache(0)
  cache.io.readPort.readResp.ready := regs1_toCache(1)
  cache.io.writePort.writeReq.valid := regs1_toCache(2)
  cache.io.memRead.memReq.ready := regs1_toCache(3)
  cache.io.memRead.memResp.valid := regs1_toCache(4)
  cache.io.memWrite.memWriteReq.ready := regs1_toCache(5)
  cache.io.flushCache := regs1_toCache(6)
  
  // 1 bit, from cache to tester 
  regs1_fromCache(0) := cache.io.readPort.readReq.ready  
  regs1_fromCache(1) := cache.io.readPort.readResp.valid
  regs1_fromCache(2) := cache.io.writePort.writeReq.ready
  regs1_fromCache(3) := cache.io.memRead.memReq.valid
  regs1_fromCache(4) := cache.io.memRead.memResp.ready 
  regs1_fromCache(5) := cache.io.memWrite.memWriteReq.valid
  regs1_fromCache(6) := cache.io.cacheActive
  
  
  def makeInputShiftRegister[T <: Data](n: Int, chain: Vec[T], input: T, shiftEnable: Bits) = {
    when(shiftEnable === Bits(1)) { chain(0) := input }
    for(i <- 1 to n-1)
    {
      when(shiftEnable === Bits(1)) { chain(i) := chain(i-1) }
    }
  }
  
  def makeOutputShiftRegister[T <: Data](n: Int, chain: Vec[T], output: T, shiftEnable: Bits) = {
    for(i <- 1 to n-1)
    {
      when(shiftEnable === Bits(1)) { chain(i) := chain(i-1) }
    }
    output := chain(n-1)
  }
  
  // connect I/O regs as shift register
  makeInputShiftRegister[UInt](2, regs64_toCache, io.dataIn64, io.shiftEnable)
  makeOutputShiftRegister[UInt](2, regs64_fromCache, io.dataOut64, io.shiftEnable)

  makeInputShiftRegister[UInt](2, regs32_toCache, io.dataIn32, io.shiftEnable)
  makeOutputShiftRegister[UInt](8, regs32_fromCache, io.dataOut32, io.shiftEnable)
  
  makeInputShiftRegister[UInt](7, regs1_toCache, io.dataIn1, io.shiftEnable)
  makeOutputShiftRegister[UInt](7, regs1_fromCache, io.dataOut1, io.shiftEnable)
}


object MainObj {
  def main(args: Array[String]): Unit = {
    val cacheLineSize = 64
    val cacheDepth = 8192
    val addressBits = 24    
    //chiselMain(args, () => Module(new CacheDataMemory(cacheLineSize, cacheDepth, addressBits)))
    //chiselMain(args, () => Module(new FPGASynResWrapper(cacheLineSize, cacheDepth, addressBits)))
    chiselMainTest(args, () => Module(new ColdMissSkipVectorCache(cacheLineSize, cacheDepth, addressBits))) { c => new ColdMissSkipVectorCacheTester(c, cacheDepth) }
  }
}


