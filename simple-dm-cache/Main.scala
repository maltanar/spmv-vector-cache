package VectorCache
import Chisel._

object MainObj {
  def main(args: Array[String]): Unit = {
    val cacheLineSize = 64
    val cacheDepth = 1024*128
    val addressBits = 24    
    //chiselMain(args, () => Module(new CacheDataMemory(cacheLineSize, cacheDepth, addressBits)))
    chiselMainTest(args, () => Module(new SimpleDMVectorCache(cacheLineSize, cacheDepth, addressBits))) { c => new SimpleDMVectorCacheTester(c, cacheDepth) }
  }
}


