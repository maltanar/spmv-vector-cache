package VectorCache

import Chisel._
import Literal._
import Node._
import CacheInterface._

// IMPORTANT:
// ensure FIFO queues at all input and output to avoid combinational loops

class ColdMissSkipVectorCache(lineSize: Int, depth: Int, addrBits: Int) extends Module {
  val io = new SinglePortCache_ColdMissSkip_IF(addrBits, lineSize)
  val controller = Module(new CacheController(lineSize, depth, addrBits))
  val dataMem = Module(new CacheDataMemory(lineSize, depth, addrBits))
  val tagMem = Module(new CacheTagMemory(lineSize, depth, addrBits))
  
  io <> controller.io.externalIF
  controller.io.dataPortA <> dataMem.io.portA
  controller.io.dataPortB <> dataMem.io.portB
  controller.io.tagPortA <> tagMem.io.portA
  controller.io.tagPortB <> tagMem.io.portB
}

class ColdMissSkipVectorCacheTester(c: ColdMissSkipVectorCache, depth: Int) extends Tester(c) {
  // TODO add some proper write testing
  // these tests should include:
  // - read cold miss
  // - read hit
  // - read conflict miss
  // - write hit
  // - write miss
  
  // start with no cache reqs, no mem resps
  poke(c.io.readPort.readReq.valid, 0)
  poke(c.io.memRead.memResp.valid, 0)
  poke(c.io.writePort.writeReq.valid, 0)
  poke(c.io.flushCache, 0)
  
  poke(c.io.memRead.memReq.ready, 1)  // memreq can always accept (infinite memory request FIFO)
  poke(c.io.memWrite.memWriteReq.ready, 1) // infinite mem req FIFO for writes as well
  poke(c.io.readPort.readResp.ready, 1) // readresp can always accept (infinite datapath FIFO)
  expect(c.io.cacheActive, 0)
  // expect cacheActive after "depth" cycles
  step(depth)
  expect(c.io.cacheActive, 1)
  for(i <- 1 to 3)
  {
    expect(c.io.readPort.readResp.valid, 0)    // no read response appears
    expect(c.io.memRead.memReq.valid, 0)      // no mem request appears
    expect(c.io.memWrite.memWriteReq.valid, 0) // no mem write req
    expect(c.io.readPort.readReq.ready, 0)     // nothing to pop from reads
    expect(c.io.writePort.writeReq.ready, 1)    // since memWriteReq is ready
    step(1)
  }
}
