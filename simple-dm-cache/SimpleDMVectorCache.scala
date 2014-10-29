package VectorCache
import Chisel._
import Literal._

// IMPORTANT:
// ensure FIFO queues at all input and output to avoid combinational loops

class SimpleDMVectorCache(lineSize: Int, depth: Int, addrBits: Int) extends Module {
  val io = new Bundle {
    // interface towards processing element:
    // read port
    val readReq = new DeqIO(UInt(width = addrBits))
    val readResp = new EnqIO(UInt(width = lineSize))
    // readRespInd should be part of readResp, but Verilator sizes...
    val readRespInd = UInt(OUTPUT, width = addrBits)
    // interface towards processing element:
    // write port
    val writeReq = new DeqIO(UInt(width = addrBits))
    // writeData should be part of writeReq, but Verilator
    // best handles 1-, 32- and 64-bit widths, so it is separated
    val writeData = UInt(INPUT, lineSize)
    // interface towards main memory, read requests
    val memReq = new EnqIO(UInt(width = addrBits))
    val memResp = new DeqIO(UInt(width = lineSize))
    // interface towards main memory, write requests
    val memWriteReq = new EnqIO(UInt(width = addrBits))
    val memWriteData = UInt(OUTPUT, lineSize)
    // flush cache
    val flushCache = Bool(INPUT)
    // init indicator and cache stat outputs
    val cacheActive = Bool(OUTPUT)
    val readCount = UInt(OUTPUT, 32)
    val readMissCount = UInt(OUTPUT, 32)
    val writeCount = UInt(OUTPUT, 32)
    val writeMissCount = UInt(OUTPUT, 32)
  }
  
  val controller = Module(new CacheController(lineSize, depth, addrBits))
  
  io <> controller.io
}

class SimpleDMVectorCacheTester(c: SimpleDMVectorCache, depth: Int) extends Tester(c) {
  // TODO add some proper write testing
  // these tests should include:
  // - read cold miss
  // - read hit
  // - read conflict miss
  // - write hit
  // - write miss
  
  // start with no cache reqs, no mem resps
  poke(c.io.readReq.valid, 0)
  poke(c.io.memResp.valid, 0)
  poke(c.io.writeReq.valid, 0)
  poke(c.io.flushCache, 0)
  
  poke(c.io.memReq.ready, 1)  // memreq can always accept (infinite memory request FIFO)
  poke(c.io.memWriteReq.ready, 1) // infinite mem req FIFO for writes as well
  poke(c.io.readResp.ready, 1) // readresp can always accept (infinite datapath FIFO)
  expect(c.io.cacheActive, 0)
  // expect cacheActive after "depth" cycles
  step(depth)
  expect(c.io.cacheActive, 1)
  for(i <- 1 to 3)
  {
    expect(c.io.readResp.valid, 0)    // no read response appears
    expect(c.io.memReq.valid, 0)      // no mem request appears
    expect(c.io.memWriteReq.valid, 0) // no mem write req
    expect(c.io.readReq.ready, 0)     // nothing to pop from reads
    expect(c.io.writeReq.ready, 1)    // since memWriteReq is ready
    step(1)
  }
  /*
  // put in a write request
  poke(c.io.writeReq.bits, 9)
  poke(c.io.writeReq.valid, 1)
  poke(c.io.writeData, 111)
  
  // put in a read request
  poke(c.io.readReq.bits, 7)
  poke(c.io.readReq.valid, 1)
	// this will be a cache miss (nothing in cache)
  // request should immediately appear at memReq
  // (since tag reads/miss checks are combinational)
  expect(c.io.memReq.bits, 7)
  expect(c.io.memReq.valid, 1)
  // similarly, the write req should also miss and cause
  // a memory request
  expect(c.io.memWriteReq.valid, 1)
  expect(c.io.memWriteReq.bits, 9)
  expect(c.io.memWriteData, 111)
  step(1)
  // expect a cache miss to appear in the counters
  expect(c.io.readMissCount, 1)
  expect(c.io.readCount, 0)
  expect(c.io.writeCount, 1)
  expect(c.io.writeMissCount, 1)
  // cache shouldn't be in sActive now
  expect(c.io.cacheActive, 0)
  // wait a while
  for(i <- 1 to 3)
  {
    expect(c.io.readReq.ready, 0)    // even though memReq.valid=1
    expect(c.io.readResp.valid, 0)  // no read response
    expect(c.io.memResp.ready, 0)   // memResp not arrived yet, can't read
    step(1)
  }
  // issue memory response
  poke(c.io.memResp.bits, 0x1f)
  poke(c.io.memResp.valid, 1)
  expect(c.io.memResp.ready, 1) // pop reply
  expect(c.io.readResp.valid, 0)  // still no read response - just tag write
  expect(c.io.readReq.ready, 0) 
  step(1)
  poke(c.io.memResp.valid, 0) // remove mem reply
  // the tag read should now succeed (back to sActive)
  expect(c.io.cacheActive, 1)
  // this hit should also pop from the request queue
  expect(c.io.readReq.ready, 1)
  step(1)
  // remove request
  poke(c.io.readReq.valid, 0)
  expect(c.io.readReq.ready, 0)
  // expect cache read response and counters
  expect(c.io.readResp.valid, 1)
  expect(c.io.readResp.bits, 0x1f)  // returned data
  expect(c.io.readRespInd, 7)   // returned index
  step(1)
  // make sure nothing happens while no request
  for(i <- 1 to 3)
  {
    expect(c.io.readResp.valid, 0)  // no read response appears
    expect(c.io.memReq.valid, 0)    // no mem request appears
    expect(c.io.readReq.ready, 0)   // nothing to pop
    step(1)
  }
  // repeat the same request - this time it should hit
  poke(c.io.readReq.valid, 1)
  poke(c.io.readReq.bits, 7)
  expect(c.io.cacheActive, 1)
  expect(c.io.readReq.ready, 1)
  step(1)
  for(i <- 1 to 3)
  {
    // keep repeating the same request
    // should stay in active state & keep hitting
    expect(c.io.cacheActive, 1)
    expect(c.io.memReq.valid, 0) // no mem request
    // check read/miss counters
    expect(c.io.readCount, i+1) // read count should go up
    expect(c.io.readMissCount, 1)   // miss count shouldn't go up
    // check read response
    expect(c.io.readResp.valid, 1)
    expect(c.io.readResp.bits, 0x1f)
    step(1)     
  }
  */
}
