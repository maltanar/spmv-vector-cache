package VectorCache
import Chisel._

object MainObj {
  def main(args: Array[String]): Unit = {
    val cacheLineSize = 64
    val cacheDepth = 8192
    val addressBits = 24    
    //chiselMain(args, () => Module(new CacheDataMemory(cacheLineSize, cacheDepth, addressBits)))
    chiselMainTest(args, () => Module(new ColdMissSkipVectorCache(cacheLineSize, cacheDepth, addressBits))) { c => new ColdMissSkipVectorCacheTester(c, cacheDepth) }
  }
}


