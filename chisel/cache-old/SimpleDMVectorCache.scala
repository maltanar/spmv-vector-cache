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
  val ctlM = Module(new CacheController(p, pOCM))
  val ctl = ctlM.io

  io <> ctl.externalIF
  ctl.dataPortA <> dataMem.ports(0)
  ctl.dataPortB <> dataMem.ports(1)
  ctl.tagPortA <> tagMem.portA
  ctl.tagPortB <> tagMem.portB
}
