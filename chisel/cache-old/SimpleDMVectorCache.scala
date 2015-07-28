package SpMVAccel

import Chisel._
import TidbitsOCM._
import TidbitsDMA._

class SimpleDMVectorCache(val p: SpMVAccelWrapperParams) extends Module {
  val io = new SinglePortCacheIF(p)
  val tagMem = Module(new CacheTagMemory(p)).io

  val pOCM = new OCMParameters( p.ocmDepth*p.opWidth, p.opWidth, p.opWidth, 2,
                                p.ocmReadLatency)
  val dataMem = Module(if (p.ocmPrebuilt) new OnChipMemory(pOCM, p.ocmName) else
                  new AsymDualPortRAM(pOCM)).io
  val controller = Module(new CacheController(p, pOCM)).io

  io <> controller.externalIF
  controller.dataPortA <> dataMem.ports(0)
  controller.dataPortB <> dataMem.ports(1)
  controller.tagPortA <> tagMem.portA
  controller.tagPortB <> tagMem.portB
}
