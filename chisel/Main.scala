package SpMVAccel

import Chisel._
import TidbitsAXI._
import TidbitsSimUtils._

object MainObj {
  val testOutputDir = "testOutput/"
  val verilogOutputDir = "verilogOutput/"
  def makeTestArgs(cmpName: String): Array[String] = {
    return Array( "--targetDir", testOutputDir+cmpName,
                  "--compile", "--test", "--genHarness")
  }

  def makeVerilogBuildArgs(cmpName: String): Array[String] = {
    return Array( "--targetDir", verilogOutputDir+cmpName, "--v")
  }

  val p = new SpMVAccelWrapperParams()

  def printUsageAndExit() {
    println("Usage: sbt \"run <op> <comp>\"")
    println("where <op> = {inst | test}")
    System.exit(0)
  }

  val instFxnMap: Map[String, () => Module] = Map(
    "SpMVAccel" -> {() => new SpMVAccelerator(p)}
  )

  val testFxnMap: Map[String, () => AXIWrappableAccel] = Map {
    "TestSpMVBackend" -> {() => new TestSpMVBackend()}
  }

  def main(args: Array[String]): Unit = {
    if(args.size != 2) { printUsageAndExit() }

    val op = args(0)
    val harnessMemDepth = 8*1024*1024

    val cmpName = args(1)
    if(op == "inst") {
      val instFxn = instFxnMap(cmpName)
      makeVerilog(cmpName, instFxn)
    } else if(op == "test") {
      val testFxn = testFxnMap(cmpName)
      makeHarnessTest(cmpName, harnessMemDepth, testFxn)
    } else {
      printUsageAndExit()
    }
  }

  def makeVerilog(cmpName: String, instFxn: () => Module) {
    val vargs = makeVerilogBuildArgs(cmpName)
    chiselMain(vargs, () => Module(instFxn()))
  }

  def makeHarnessTest(cmpName: String,
                      memDepth: Int,
                      fxn: () => AXIWrappableAccel) {
    val targs = makeTestArgs(cmpName)
    val instModule = {() => Module(new WrappableAccelHarness(fxn, memDepth))}
    val instTest = {c => new WrappableAccelTester(c)}

    val aT = makeTestArgs(cmpName)
    chiselMainTest(aT, instModule)(instTest)
  }
}
