scalaVersion := "2.10.4"

addSbtPlugin("com.github.scct" % "sbt-scct" % "0.2")

libraryDependencies += "edu.berkeley.cs" %% "chisel" % "latest.release"

unmanagedSourceDirectories in Compile <++= baseDirectory { base =>
  Seq(
    base / "fpga-tidbits/common",
    base / "fpga-tidbits/interfaces",
    base / "fpga-tidbits/profiler",
    base / "fpga-tidbits/streams",
    base / "fpga-tidbits/dma",
    base / "fpga-tidbits/on-chip-memory",
    base / "fpga-tidbits/sim-utils",
    base / "fpga-tidbits/regfile",
    base / "backend",
    base / "frontend",
    base / "spmv-common",
    base / "tests"
  )
}
