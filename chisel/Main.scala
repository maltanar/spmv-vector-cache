package SpMVAccel

import Chisel._
import TidbitsAXI._
import TidbitsSimUtils._

object MainObj {
  val testOutputDir = "testOutput/"
  val verilogOutputDir = "verilogOutput/"
  val driverOutputDir = "driverOutput/"
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
    println("where <op> = {inst | test | driver}")
    println("<comp> = {" + instFxnMap.keys.reduce(_ +" | "+ _) + "}")
    System.exit(0)
  }

  val instFxnMap: Map[String, () => AXIWrappableAccel] = Map(
    "SpMVAccel-BufferAll" -> {() => new SpMVAccelerator(p)},
    "SpMVAccel-BufferNone" -> {() => new SpMVAcceleratorBufferNone(p)},
    "SpMVAccel-BufferSel" -> {() => new SpMVAcceleratorBufferSel(p)},
    "SpMVAccel-OldCache" -> {() => new SpMVAcceleratorOldCache(p)},
    "TestSpMVBackend" -> {() => new TestSpMVBackend()},
    "TestSpMVFrontend" -> {() => new TestSpMVFrontend()}
  )

  def main(args: Array[String]): Unit = {
    if(args.size != 2) { printUsageAndExit() }

    val op = args(0)
    val harnessMemDepth = 8*1024*1024

    val cmpName = args(1)
    val instFxn = instFxnMap(cmpName)

    if(op == "inst") {
      makeHarnessVerilog(cmpName, instFxn)
    } else if(op == "test") {
      makeHarnessTest(cmpName, harnessMemDepth, instFxn)
    } else if(op == "driver") {
      makeHarnessDriver(cmpName, instFxn)
    } else {
      printUsageAndExit()
    }
  }

  def makeHarnessDriver(cmpName: String, fxn: () => AXIWrappableAccel) {
    val outDir = new java.io.File(driverOutputDir)
    outDir.mkdir()
    fxn().buildDriver(driverOutputDir)
  }

  def makeHarnessVerilog(cmpName: String, fxn: () => AXIWrappableAccel) {
    val vargs = makeVerilogBuildArgs(cmpName)
    val instModule = {() => Module(new AXIAccelWrapper(fxn))}
    chiselMain(vargs, instModule)
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
