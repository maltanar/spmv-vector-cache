package VectorCache
import Chisel._
import Literal._

// IMPORTANT:
// ensure FIFO queues at all input and output to avoid combinational loops

class SimpleDMVectorCache(lineSize: Int, depth: Int, addrBits: Int) extends Module {
  val io = new Bundle {
    // interface towards processing element
    val readReq = new DeqIO(UInt(width = addrBits))
    val readResp = new EnqIO(UInt(width = lineSize))
    // interface towards main memory
    val memReq = new EnqIO(UInt(width = addrBits))
    val memResp = new DeqIO(UInt(width = lineSize))
    // init indicator and cache stat outputs
    val cacheInitialized = Bool(OUTPUT)
    val readCount = UInt(OUTPUT, 32)
    val missCount = UInt(OUTPUT, 32)
  }
  // registers for read/miss counts
  val readCount = Reg(init = UInt(0, 32))
  val missCount = Reg(init = UInt(0, 32))
  
  // memory for keeping cache state
  val depthBitCount = log2Up(depth)
  val tagBitCount = addrBits - depthBitCount
  val cacheLineTotalSize = lineSize+tagBitCount+1
  // aggregate storage for cache data (to infer single RAM)
  // MSB -> data(lineSize) tag(tagBitCount)  valid(1)  <- LSB
  val cacheLines = Mem(UInt(width=cacheLineTotalSize), depth)
  
  val sInit :: sActive :: sCacheFill :: Nil = Enum(UInt(), 3)
  val state = Reg(init = UInt(sInit))
  val initCtr = Reg(init = UInt(0, depthBitCount))
  
  // shorthands for current request
  val currentReq = io.readReq.bits
  val currentReqInd = currentReq(depthBitCount-1, 0)
  val currentReqTag = currentReq(addrBits-1, depthBitCount)
  
  val currentReqLine = cacheLines(currentReqInd)
  val currentReqLineValid = currentReqLine(0)
  val currentReqLineTag = currentReqLine(tagBitCount, 1)
  val currentReqLineData = currentReqLine(cacheLineTotalSize-1, tagBitCount+1)
  
  // shorthands for current memory response
  val currentMemResp = io.memResp.bits
  
  // drive default outputs
  io.cacheInitialized := Bool(true) // whether cache has been initialized
  io.readReq.ready := Bool(false)   // whether cache can accept a read req
  io.memResp.ready := Bool(false)   // whether cache can accept a mem resp
  io.readCount := readCount         // total reads so far (hits = total - misses)
  io.missCount := missCount         // total misses so far
  
  when (state === sInit)
  {
    // unset all valid bit in cache at init time
    io.cacheInitialized := Bool(false)
    // TODO FPGA block mem can be initialized at config time, so this is
    // commented out
    // cacheLines(initCtr) := Fill(cacheLineTotalSize, Bits(0))
    
    initCtr := initCtr + UInt(1)
    
    // go to sActive when all blocks initialized
    when (initCtr === UInt(depth-1)) { state := sActive}
  }
  .elsewhen (state === sActive)
  {
    // cache ready to serve requests (no pending misses)
    
    // if something pending on the input queue
    when (io.readReq.valid)
    {
      // if valid and tag match and space on output queue
      when (currentReqTag === currentReqLineTag & currentReqLineValid & io.readResp.ready)
      {
        // cache hit!
        readCount := readCount + UInt(1)
        
        // read from input queue
        io.readReq.deq()
        
        // write to output queue
        io.readResp.enq(currentReqLineData)
      }
      // if not valid or no tag match, and can issue memory request
      .elsewhen ((currentReqTag != currentReqLineTag | ~currentReqLineValid) & io.memReq.ready)
      {
        // cache miss
        missCount := missCount + UInt(1)
        state := sCacheFill
        io.memReq.enq(currentReq)
      }
    }
  }
  .elsewhen (state === sCacheFill)
  {
    // cache is waiting for a pending miss to be served from main mem
    
    // check to see if any responses from memory are pending
    when (io.memResp.valid)
    {
      // write returned data to cache, fix tag and valid bits
      cacheLines(currentReqInd) := Cat(currentMemResp, Cat(currentReqTag, Bits(1, 1)))
      // pop response from queue
      io.memResp.deq()
      // go back to active state
      state := sActive
    }
  }
}

class SimpleDMVectorCacheTester(c: SimpleDMVectorCache, depth: Int) extends Tester(c) {
  // start with no cache reqs, no mem resps
  poke(c.io.readReq.valid, 0)
  poke(c.io.memResp.valid, 0)
  poke(c.io.memReq.ready, 1)  // memreq can always accept (infinite memory request FIFO)
  poke(c.io.readResp.ready, 1) // readresp can always accept (infinite datapath FIFO)
  // check that cacheInitialized starts low
  expect(c.io.cacheInitialized, 0)
  // expect cacheInitialized after "depth" cycles
  step(depth)
  expect(c.io.cacheInitialized, 1)
  for(i <- 1 to 5)
    expect(c.io.readResp.valid, 0)  // no read response appears
    expect(c.io.memReq.valid, 0)    // no mem request appears
    expect(c.io.readReq.ready, 0)   // nothing to pop
    step(1)
  // put in a request
  poke(c.io.readReq.bits, 7)
  poke(c.io.readReq.valid, 1)
	// this will be a cache miss (nothing in cache)
  // request should immediately appear at memReq
  // (since tag reads/miss checks are combinational)
  expect(c.io.memReq.bits, 7)
  expect(c.io.memReq.valid, 1)
  step(1)
  // wait a while
  for(i <- 1 to 5)
    expect(c.io.readReq.ready, 0)    // even though memReq.valid=1
    expect(c.io.readResp.valid, 0)  // no read response
    expect(c.io.memResp.ready, 0)   // memResp not arrived yet, can't read
    step(1)
  // issue memory response
  poke(c.io.memResp.bits, 0x1f)
  poke(c.io.memResp.valid, 1)
  expect(c.io.memResp.ready, 1) // pop reply
  expect(c.io.readResp.valid, 0)  // still no read response - just tag write
  expect(c.io.readReq.ready, 0)    // will pop in next cycle
  step(1)
  poke(c.io.memResp.valid, 0) // remove mem reply
  // expect cache read response now
  expect(c.io.readReq.ready, 1)
  expect(c.io.readResp.valid, 1)
  expect(c.io.readResp.bits, 0x1f)
  step(1)
  poke(c.io.readReq.valid, 0)
  for(i <- 1 to 5)
    expect(c.io.readResp.valid, 0)  // no read response appears
    expect(c.io.memReq.valid, 0)    // no mem request appears
    expect(c.io.readReq.ready, 0)   // nothing to pop
    step(1)
  // repeat the same request - this time it should hit
  poke(c.io.readReq.valid, 1)
  poke(c.io.readReq.bits, 7)
  expect(c.io.readResp.valid, 1)
  expect(c.io.readResp.bits, 0x1f)
  step(1)
  poke(c.io.readReq.valid, 0)  
  step(1)
}
