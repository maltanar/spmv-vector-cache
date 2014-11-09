package VectorCache
import Chisel._

object MainObj {
  def main(args: Array[String]): Unit = {
  def getCacheDepthParam(list: List[String]) : Int = {
      list match {
        case "--depth" :: value :: tail => value.toInt
        case _ => 1024
      }
    }
    
    val cacheLineSize = 64
    val cacheDepth = getCacheDepthParam(args.toList)
    val addressBits = 24
    
    println(cacheDepth)
    //chiselMain(args, () => Module(new CacheDataMemory(cacheLineSize, cacheDepth, addressBits)))
    chiselMainTest(args, () => Module(new SimpleDMVectorCache(cacheLineSize, cacheDepth, addressBits))) { c => new SimpleDMVectorCacheTester(c, cacheDepth) }
  }
}


