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

  def printUsageAndExit() {
    println("Usage: sbt \"run <op> <comp> <options>\"")
    println("where <op> = {inst | test | driver}")
    println("<comp> = {" + instFxnMap.keys.reduce(_ +" | "+ _) + "}")
    System.exit(0)
  }

  val instFxnMap: Map[String, SpMVAccelWrapperParams => AXIWrappableAccel] = Map(
    "BufferAll" -> {p => new SpMVAcceleratorBufferAll(p)},
    "BufferNone" -> {p => new SpMVAcceleratorBufferNone(p)},
    "BufferSel" -> {p => new SpMVAcceleratorBufferSel(p)},
    "OldCache" -> {p => new SpMVAcceleratorOldCache(p)},
    "NewCache" -> {p => new SpMVAcceleratorNewCache(p)}
    //"TestBackend" -> {p => new TestSpMVBackend()},
    //"TestFrontend" -> {p => new TestSpMVFrontend()
  )

  def main(args: Array[String]): Unit = {
    if(args.size < 2) { printUsageAndExit() }

    val op = args(0)
    val harnessMemDepth = 64*1024*1024

    val cmpName = args(1)
    val instFxn = instFxnMap(cmpName)
    val opts = args.toList.takeRight(args.size-2)
    val p = new SpMVAccelWrapperParams(opts)

    if(op == "inst") {
      makeHarnessVerilog(cmpName, p, instFxn)
    } else if(op == "test") {
      makeHarnessTest(cmpName, harnessMemDepth, p, instFxn)
    } else if(op == "driver") {
      makeHarnessDriver(cmpName, p, instFxn)
    } else {
      printUsageAndExit()
    }
  }

  def makeHarnessDriver(cmpName: String, p: SpMVAccelWrapperParams,
                        fxn: SpMVAccelWrapperParams => AXIWrappableAccel) {
    val outDir = new java.io.File(driverOutputDir)
    outDir.mkdir()
    fxn(p).buildDriver(driverOutputDir)
  }

  def makeHarnessVerilog(cmpName: String, p: SpMVAccelWrapperParams,
                        fxn: SpMVAccelWrapperParams => AXIWrappableAccel) {
    val vargs = makeVerilogBuildArgs(cmpName+"-"+p.suffix)
    val instModule = {() => Module(new AXIAccelWrapper(() => fxn(p)))}
    chiselMain(vargs, instModule)
  }

  def makeHarnessTest(cmpName: String,
                      memDepth: Int,
                      p: SpMVAccelWrapperParams,
                      fxn: SpMVAccelWrapperParams => AXIWrappableAccel) {
    val targs = makeTestArgs(cmpName+"-"+p.suffix)
    val instModule = {() => Module(new WrappableAccelHarness(() => fxn(p), memDepth))}
    val instTest = {c => new WrappableAccelTester(c)}

    val aT = makeTestArgs(cmpName)
    chiselMainTest(aT, instModule)(instTest)
  }
}
