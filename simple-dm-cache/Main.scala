package VectorCache
import Chisel._

object MainObj {
  def main(args: Array[String]): Unit = {
    val cacheLineSize = 8
    val cacheDepth = 8192
    val addressBits = 24    
    chiselMainTest(args, () => Module(new SimpleDMVectorCache(cacheLineSize, cacheDepth, addressBits))) {
      c => new SimpleDMVectorCacheTester(c, cacheDepth)
    }
  }
}
