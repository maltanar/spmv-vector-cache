package SpMVAccel

import Chisel._

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

  def main(args: Array[String]): Unit = {
    val cmpName = args(0)
    println("Executing task: " + cmpName)

    val instFxnMap: Map[String, () => Module] = Map(
      "SpMVAccel" -> {() => new SpMVAccelerator(new SpMVAccelWrapperParams()) }
    )

    val instFxn = instFxnMap(cmpName)
    makeVerilog(cmpName, instFxn)
  }

  def makeVerilog(cmpName: String, instFxn: () => Module) {
    val vargs = makeVerilogBuildArgs(cmpName)
    chiselMain(vargs, () => Module(instFxn()))
  }
}
