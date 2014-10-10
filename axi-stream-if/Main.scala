package AXIStreamTesting
import Chisel._

object MainObj {
  def main(args: Array[String]): Unit = {
    chiselMainTest(args, () => Module(new AXIStreamSum(64))) {
      c => new AXIStreamSumTester(c)
    }
  }
}
