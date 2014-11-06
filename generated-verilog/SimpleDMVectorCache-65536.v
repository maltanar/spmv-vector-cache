`define SYNTHESIS
module CacheController(input clk, input reset,
    output io_externalIF_readPort_readReq_ready,
    input  io_externalIF_readPort_readReq_valid,
    input [23:0] io_externalIF_readPort_readReq_bits,
    input  io_externalIF_readPort_readResp_ready,
    output io_externalIF_readPort_readResp_valid,
    output[63:0] io_externalIF_readPort_readResp_bits,
    output[23:0] io_externalIF_readPort_readRespInd,
    output io_externalIF_writePort_writeReq_ready,
    input  io_externalIF_writePort_writeReq_valid,
    input [23:0] io_externalIF_writePort_writeReq_bits,
    input [63:0] io_externalIF_writePort_writeData,
    input  io_externalIF_memRead_memReq_ready,
    output io_externalIF_memRead_memReq_valid,
    output[23:0] io_externalIF_memRead_memReq_bits,
    output io_externalIF_memRead_memResp_ready,
    input  io_externalIF_memRead_memResp_valid,
    input [63:0] io_externalIF_memRead_memResp_bits,
    input  io_externalIF_memWrite_memWriteReq_ready,
    output io_externalIF_memWrite_memWriteReq_valid,
    output[23:0] io_externalIF_memWrite_memWriteReq_bits,
    output[63:0] io_externalIF_memWrite_memWriteData,
    input  io_externalIF_flushCache,
    output io_externalIF_cacheActive,
    output[31:0] io_externalIF_readCount,
    output[31:0] io_externalIF_readMissCount,
    output[31:0] io_externalIF_writeCount,
    output[31:0] io_externalIF_writeMissCount,
    output[23:0] io_dataPortA_addr,
    output[63:0] io_dataPortA_dataIn,
    output io_dataPortA_writeEn,
    input [63:0] io_dataPortA_dataOut,
    output[23:0] io_dataPortB_addr,
    output[63:0] io_dataPortB_dataIn,
    output io_dataPortB_writeEn,
    input [63:0] io_dataPortB_dataOut,
    output[23:0] io_tagPortA_addr,
    output[63:0] io_tagPortA_dataIn,
    output io_tagPortA_writeEn,
    input [63:0] io_tagPortA_dataOut,
    output[23:0] io_tagPortB_addr,
    input [63:0] io_tagPortB_dataOut
);

  wire[23:0] T119;
  wire[15:0] currentWriteReqInd;
  wire T0;
  wire T1;
  reg [2:0] state;
  wire[2:0] T120;
  wire[2:0] T2;
  wire[2:0] T3;
  wire[2:0] T4;
  wire[2:0] T5;
  wire[2:0] T6;
  wire[2:0] T7;
  wire[2:0] T8;
  wire[2:0] T9;
  wire[2:0] T10;
  wire T11;
  wire T12;
  reg [15:0] initCtr;
  wire[15:0] T121;
  wire[15:0] T13;
  wire[15:0] T14;
  wire[15:0] T15;
  wire[15:0] T16;
  wire[15:0] T17;
  wire[15:0] T18;
  wire[15:0] T19;
  wire T20;
  wire T21;
  wire T22;
  wire T23;
  wire T24;
  wire T25;
  wire T26;
  wire T27;
  wire T28;
  wire T29;
  wire T30;
  wire T31;
  wire T32;
  wire T33;
  wire T34;
  wire T35;
  wire T36;
  wire T37;
  wire T38;
  wire currentReqLineValid;
  wire T39;
  wire[7:0] currentReqLineTag;
  wire[7:0] currentReqTag;
  wire T40;
  wire T41;
  wire T42;
  wire T43;
  wire T44;
  wire T45;
  wire T46;
  wire T47;
  wire T48;
  wire T49;
  wire T50;
  wire T51;
  wire T52;
  wire T53;
  wire T54;
  wire T55;
  wire T56;
  wire T57;
  wire T58;
  wire T59;
  wire T60;
  wire T61;
  wire T62;
  wire T63;
  wire T64;
  wire T65;
  wire T66;
  wire[63:0] T122;
  wire[8:0] T67;
  wire[8:0] T68;
  wire[23:0] T123;
  wire[15:0] T69;
  wire[15:0] T70;
  wire[15:0] currentReqInd;
  wire[15:0] T71;
  wire T72;
  wire T73;
  wire T74;
  wire currentWriteReqLineValid;
  wire T75;
  wire[7:0] currentWriteReqLineTag;
  wire[7:0] currentWriteReqTag;
  wire T76;
  wire T77;
  wire T78;
  wire T79;
  wire T80;
  wire[23:0] T124;
  wire[23:0] T125;
  wire[15:0] T81;
  wire[15:0] T82;
  reg [31:0] writeMissCount;
  wire[31:0] T126;
  wire[31:0] T83;
  wire[31:0] T84;
  wire[31:0] T85;
  wire T86;
  wire T87;
  wire T88;
  wire[31:0] T89;
  wire T90;
  wire T91;
  wire T92;
  reg [31:0] writeCount;
  wire[31:0] T127;
  wire[31:0] T93;
  wire[31:0] T94;
  wire[31:0] T95;
  wire[31:0] T96;
  wire[31:0] T97;
  wire[31:0] T98;
  wire[31:0] T99;
  wire[31:0] T100;
  reg [31:0] readMissCount;
  wire[31:0] T128;
  wire[31:0] T101;
  wire[31:0] T102;
  reg [31:0] readCount;
  wire[31:0] T129;
  wire[31:0] T103;
  wire[31:0] T104;
  wire T105;
  wire T106;
  wire[63:0] T107;
  wire[63:0] T108;
  wire[23:0] T109;
  wire[23:0] T110;
  wire[23:0] T111;
  wire[23:0] T112;
  wire T113;
  wire T114;
  wire T115;
  wire T116;
  wire T117;
  wire T118;
  reg [23:0] prevReadRequestReg;
  reg  enableReadRespReg;
  wire T130;

`ifndef SYNTHESIS
  integer initvar;
  initial begin
    #0.002;
    state = {1{$random}};
    initCtr = {1{$random}};
    writeMissCount = {1{$random}};
    writeCount = {1{$random}};
    readMissCount = {1{$random}};
    readCount = {1{$random}};
    prevReadRequestReg = {1{$random}};
    enableReadRespReg = {1{$random}};
  end
`endif

  assign io_tagPortB_addr = T119;
  assign T119 = {8'h0, currentWriteReqInd};
  assign currentWriteReqInd = io_externalIF_writePort_writeReq_bits[4'hf:1'h0];
  assign io_tagPortA_writeEn = T0;
  assign T0 = T62 ? 1'h1 : T1;
  assign T1 = state == 3'h0;
  assign T120 = reset ? 3'h0 : T2;
  assign T2 = T62 ? 3'h1 : T3;
  assign T3 = T57 ? 3'h5 : T4;
  assign T4 = T53 ? 3'h4 : T5;
  assign T5 = T47 ? 3'h3 : T6;
  assign T6 = T35 ? 3'h2 : T7;
  assign T7 = T30 ? 3'h6 : T8;
  assign T8 = T28 ? 3'h1 : T9;
  assign T9 = T26 ? 3'h7 : T10;
  assign T10 = T11 ? 3'h1 : state;
  assign T11 = T1 & T12;
  assign T12 = initCtr == 16'hffff;
  assign T121 = reset ? 16'h0 : T13;
  assign T13 = T30 ? 16'h0 : T14;
  assign T14 = T20 ? T19 : T15;
  assign T15 = T26 ? T18 : T16;
  assign T16 = T1 ? T17 : initCtr;
  assign T17 = initCtr + 16'h1;
  assign T18 = initCtr + 16'h1;
  assign T19 = initCtr + 16'h1;
  assign T20 = T21 & io_externalIF_memWrite_memWriteReq_ready;
  assign T21 = T23 & T22;
  assign T22 = state == 3'h7;
  assign T23 = T24 ^ 1'h1;
  assign T24 = T1 | T25;
  assign T25 = state == 3'h6;
  assign T26 = T27 & T25;
  assign T27 = T1 ^ 1'h1;
  assign T28 = T20 & T29;
  assign T29 = initCtr == 16'h0;
  assign T30 = T31 & io_externalIF_flushCache;
  assign T31 = T33 & T32;
  assign T32 = state == 3'h1;
  assign T33 = T34 ^ 1'h1;
  assign T34 = T24 | T22;
  assign T35 = T44 & T36;
  assign T36 = T40 & T37;
  assign T37 = T39 | T38;
  assign T38 = ~ currentReqLineValid;
  assign currentReqLineValid = io_tagPortA_dataOut[1'h0:1'h0];
  assign T39 = currentReqTag != currentReqLineTag;
  assign currentReqLineTag = io_tagPortA_dataOut[4'h8:1'h1];
  assign currentReqTag = io_externalIF_readPort_readReq_bits[5'h17:5'h10];
  assign T40 = T41 ^ 1'h1;
  assign T41 = T42 & io_externalIF_readPort_readResp_ready;
  assign T42 = T43 & currentReqLineValid;
  assign T43 = currentReqTag == currentReqLineTag;
  assign T44 = T45 & io_externalIF_readPort_readReq_valid;
  assign T45 = T31 & T46;
  assign T46 = io_externalIF_flushCache ^ 1'h1;
  assign T47 = T49 & T48;
  assign T48 = io_externalIF_writePort_writeReq_valid ^ 1'h1;
  assign T49 = T51 & T50;
  assign T50 = state == 3'h2;
  assign T51 = T52 ^ 1'h1;
  assign T52 = T34 | T32;
  assign T53 = T55 & T54;
  assign T54 = state == 3'h3;
  assign T55 = T56 ^ 1'h1;
  assign T56 = T52 | T50;
  assign T57 = T58 & io_externalIF_memWrite_memWriteReq_ready;
  assign T58 = T60 & T59;
  assign T59 = state == 3'h4;
  assign T60 = T61 ^ 1'h1;
  assign T61 = T56 | T54;
  assign T62 = T63 & io_externalIF_memRead_memResp_valid;
  assign T63 = T65 & T64;
  assign T64 = state == 3'h5;
  assign T65 = T66 ^ 1'h1;
  assign T66 = T61 | T59;
  assign io_tagPortA_dataIn = T122;
  assign T122 = {55'h0, T67};
  assign T67 = T1 ? 9'h0 : T68;
  assign T68 = {currentReqTag, 1'h1};
  assign io_tagPortA_addr = T123;
  assign T123 = {8'h0, T69};
  assign T69 = T20 ? T71 : T70;
  assign T70 = T1 ? initCtr : currentReqInd;
  assign currentReqInd = io_externalIF_readPort_readReq_bits[4'hf:1'h0];
  assign T71 = initCtr - 16'h1;
  assign io_dataPortB_writeEn = T72;
  assign T72 = T77 ? 1'h1 : T73;
  assign T73 = T76 & T74;
  assign T74 = T75 & currentWriteReqLineValid;
  assign currentWriteReqLineValid = io_tagPortB_dataOut[1'h0:1'h0];
  assign T75 = currentWriteReqTag == currentWriteReqLineTag;
  assign currentWriteReqLineTag = io_tagPortB_dataOut[4'h8:1'h1];
  assign currentWriteReqTag = io_externalIF_writePort_writeReq_bits[5'h17:5'h10];
  assign T76 = T45 & io_externalIF_writePort_writeReq_valid;
  assign T77 = T80 & T78;
  assign T78 = T79 & currentWriteReqLineValid;
  assign T79 = currentWriteReqTag == currentWriteReqLineTag;
  assign T80 = T49 & io_externalIF_writePort_writeReq_valid;
  assign io_dataPortB_dataIn = io_externalIF_writePort_writeData;
  assign io_dataPortB_addr = T124;
  assign T124 = {8'h0, currentWriteReqInd};
  assign io_dataPortA_writeEn = T62;
  assign io_dataPortA_dataIn = io_externalIF_memRead_memResp_bits;
  assign io_dataPortA_addr = T125;
  assign T125 = {8'h0, T81};
  assign T81 = T21 ? initCtr : T82;
  assign T82 = T26 ? initCtr : currentReqInd;
  assign io_externalIF_writeMissCount = writeMissCount;
  assign T126 = reset ? 32'h0 : T83;
  assign T83 = T90 ? T89 : T84;
  assign T84 = T86 ? T85 : writeMissCount;
  assign T85 = writeMissCount + 32'h1;
  assign T86 = T76 & T87;
  assign T87 = T88 & io_externalIF_memWrite_memWriteReq_ready;
  assign T88 = T74 ^ 1'h1;
  assign T89 = writeMissCount + 32'h1;
  assign T90 = T80 & T91;
  assign T91 = T92 & io_externalIF_memWrite_memWriteReq_ready;
  assign T92 = T78 ^ 1'h1;
  assign io_externalIF_writeCount = writeCount;
  assign T127 = reset ? 32'h0 : T93;
  assign T93 = T90 ? T100 : T94;
  assign T94 = T77 ? T99 : T95;
  assign T95 = T86 ? T98 : T96;
  assign T96 = T73 ? T97 : writeCount;
  assign T97 = writeCount + 32'h1;
  assign T98 = writeCount + 32'h1;
  assign T99 = writeCount + 32'h1;
  assign T100 = writeCount + 32'h1;
  assign io_externalIF_readMissCount = readMissCount;
  assign T128 = reset ? 32'h0 : T101;
  assign T101 = T35 ? T102 : readMissCount;
  assign T102 = readMissCount + 32'h1;
  assign io_externalIF_readCount = readCount;
  assign T129 = reset ? 32'h0 : T103;
  assign T103 = T105 ? T104 : readCount;
  assign T104 = readCount + 32'h1;
  assign T105 = T44 & T41;
  assign io_externalIF_cacheActive = T106;
  assign T106 = state == 3'h1;
  assign io_externalIF_memWrite_memWriteData = T107;
  assign T107 = T57 ? io_dataPortA_dataOut : T108;
  assign T108 = T20 ? io_dataPortA_dataOut : io_externalIF_writePort_writeData;
  assign io_externalIF_memWrite_memWriteReq_bits = T109;
  assign T109 = T57 ? T112 : T110;
  assign T110 = T20 ? T111 : io_externalIF_writePort_writeReq_bits;
  assign T111 = {currentReqLineTag, T71};
  assign T112 = {currentReqLineTag, currentReqInd};
  assign io_externalIF_memWrite_memWriteReq_valid = T113;
  assign T113 = T57 ? currentReqLineValid : T114;
  assign T114 = T90 ? 1'h1 : T115;
  assign T115 = T86 ? 1'h1 : T116;
  assign T116 = T20 ? currentReqLineValid : 1'h0;
  assign io_externalIF_memRead_memResp_ready = T63;
  assign io_externalIF_memRead_memReq_bits = io_externalIF_readPort_readReq_bits;
  assign io_externalIF_memRead_memReq_valid = T53;
  assign io_externalIF_writePort_writeReq_ready = T117;
  assign T117 = T49 ? io_externalIF_memWrite_memWriteReq_ready : T118;
  assign T118 = T45 ? io_externalIF_memWrite_memWriteReq_ready : 1'h0;
  assign io_externalIF_readPort_readRespInd = prevReadRequestReg;
  assign io_externalIF_readPort_readResp_bits = io_dataPortA_dataOut;
  assign io_externalIF_readPort_readResp_valid = enableReadRespReg;
  assign T130 = reset ? 1'h0 : T105;
  assign io_externalIF_readPort_readReq_ready = T105;

  always @(posedge clk) begin
    if(reset) begin
      state <= 3'h0;
    end else if(T62) begin
      state <= 3'h1;
    end else if(T57) begin
      state <= 3'h5;
    end else if(T53) begin
      state <= 3'h4;
    end else if(T47) begin
      state <= 3'h3;
    end else if(T35) begin
      state <= 3'h2;
    end else if(T30) begin
      state <= 3'h6;
    end else if(T28) begin
      state <= 3'h1;
    end else if(T26) begin
      state <= 3'h7;
    end else if(T11) begin
      state <= 3'h1;
    end
    if(reset) begin
      initCtr <= 16'h0;
    end else if(T30) begin
      initCtr <= 16'h0;
    end else if(T20) begin
      initCtr <= T19;
    end else if(T26) begin
      initCtr <= T18;
    end else if(T1) begin
      initCtr <= T17;
    end
    if(reset) begin
      writeMissCount <= 32'h0;
    end else if(T90) begin
      writeMissCount <= T89;
    end else if(T86) begin
      writeMissCount <= T85;
    end
    if(reset) begin
      writeCount <= 32'h0;
    end else if(T90) begin
      writeCount <= T100;
    end else if(T77) begin
      writeCount <= T99;
    end else if(T86) begin
      writeCount <= T98;
    end else if(T73) begin
      writeCount <= T97;
    end
    if(reset) begin
      readMissCount <= 32'h0;
    end else if(T35) begin
      readMissCount <= T102;
    end
    if(reset) begin
      readCount <= 32'h0;
    end else if(T105) begin
      readCount <= T104;
    end
    prevReadRequestReg <= io_externalIF_readPort_readReq_bits;
    if(reset) begin
      enableReadRespReg <= 1'h0;
    end else begin
      enableReadRespReg <= T105;
    end
  end
endmodule

module CacheDataMemory(input clk,
    input [23:0] io_portA_addr,
    input [63:0] io_portA_dataIn,
    input  io_portA_writeEn,
    output[63:0] io_portA_dataOut,
    input [23:0] io_portB_addr,
    input [63:0] io_portB_dataIn,
    input  io_portB_writeEn,
    output[63:0] io_portB_dataOut
);

  reg [63:0] R0;
  wire[63:0] T1;
  reg [63:0] cacheLines [65535:0];
  wire[63:0] T2;
  wire[15:0] T6;
  wire[63:0] T3;
  wire[15:0] T7;
  wire[15:0] T8;
  reg [63:0] R4;
  wire[63:0] T5;
  wire[15:0] T9;

`ifndef SYNTHESIS
  integer initvar;
  initial begin
    #0.002;
    R0 = {2{$random}};
    for (initvar = 0; initvar < 65536; initvar = initvar+1)
      cacheLines[initvar] = {2{$random}};
    R4 = {2{$random}};
  end
`endif

  assign io_portB_dataOut = R0;
  assign T1 = cacheLines[T8];
  assign T6 = io_portB_addr[4'hf:1'h0];
  assign T7 = io_portA_addr[4'hf:1'h0];
  assign T8 = io_portB_addr[4'hf:1'h0];
  assign io_portA_dataOut = R4;
  assign T5 = cacheLines[T9];
  assign T9 = io_portA_addr[4'hf:1'h0];

  always @(posedge clk) begin
    R4 <= T5;
    if (io_portA_writeEn)
      cacheLines[T7] <= io_portA_dataIn;
  end

  always @(posedge clk) begin
    R0 <= T1;
    if (io_portB_writeEn)
      cacheLines[T6] <= io_portB_dataIn;
  end
endmodule

module CacheTagMemory(input clk,
    input [23:0] io_portA_addr,
    input [63:0] io_portA_dataIn,
    input  io_portA_writeEn,
    output[63:0] io_portA_dataOut,
    input [23:0] io_portB_addr,
    output[63:0] io_portB_dataOut
);

  wire[63:0] T3;
  wire[8:0] T0;
  reg [8:0] tagStorage [65535:0];
  wire[8:0] T1;
  wire[8:0] T4;
  wire[15:0] T5;
  wire[15:0] T6;
  wire[63:0] T7;
  wire[8:0] T2;
  wire[15:0] T8;

`ifndef SYNTHESIS
  integer initvar;
  initial begin
    #0.002;
    for (initvar = 0; initvar < 65536; initvar = initvar+1)
      tagStorage[initvar] = {1{$random}};
  end
`endif

  assign io_portB_dataOut = T3;
  assign T3 = {55'h0, T0};
  assign T0 = tagStorage[T6];
  assign T4 = io_portA_dataIn[4'h8:1'h0];
  assign T5 = io_portA_addr[4'hf:1'h0];
  assign T6 = io_portB_addr[4'hf:1'h0];
  assign io_portA_dataOut = T7;
  assign T7 = {55'h0, T2};
  assign T2 = tagStorage[T8];
  assign T8 = io_portA_addr[4'hf:1'h0];

  always @(posedge clk) begin
    if (io_portA_writeEn)
      tagStorage[T5] <= T4;
  end
endmodule

module SimpleDMVectorCache(input clk, input reset,
    output io_readPort_readReq_ready,
    input  io_readPort_readReq_valid,
    input [23:0] io_readPort_readReq_bits,
    input  io_readPort_readResp_ready,
    output io_readPort_readResp_valid,
    output[63:0] io_readPort_readResp_bits,
    output[23:0] io_readPort_readRespInd,
    output io_writePort_writeReq_ready,
    input  io_writePort_writeReq_valid,
    input [23:0] io_writePort_writeReq_bits,
    input [63:0] io_writePort_writeData,
    input  io_memRead_memReq_ready,
    output io_memRead_memReq_valid,
    output[23:0] io_memRead_memReq_bits,
    output io_memRead_memResp_ready,
    input  io_memRead_memResp_valid,
    input [63:0] io_memRead_memResp_bits,
    input  io_memWrite_memWriteReq_ready,
    output io_memWrite_memWriteReq_valid,
    output[23:0] io_memWrite_memWriteReq_bits,
    output[63:0] io_memWrite_memWriteData,
    input  io_flushCache,
    output io_cacheActive,
    output[31:0] io_readCount,
    output[31:0] io_readMissCount,
    output[31:0] io_writeCount,
    output[31:0] io_writeMissCount
);

  wire controller_io_externalIF_readPort_readReq_ready;
  wire controller_io_externalIF_readPort_readResp_valid;
  wire[63:0] controller_io_externalIF_readPort_readResp_bits;
  wire[23:0] controller_io_externalIF_readPort_readRespInd;
  wire controller_io_externalIF_writePort_writeReq_ready;
  wire controller_io_externalIF_memRead_memReq_valid;
  wire[23:0] controller_io_externalIF_memRead_memReq_bits;
  wire controller_io_externalIF_memRead_memResp_ready;
  wire controller_io_externalIF_memWrite_memWriteReq_valid;
  wire[23:0] controller_io_externalIF_memWrite_memWriteReq_bits;
  wire[63:0] controller_io_externalIF_memWrite_memWriteData;
  wire controller_io_externalIF_cacheActive;
  wire[31:0] controller_io_externalIF_readCount;
  wire[31:0] controller_io_externalIF_readMissCount;
  wire[31:0] controller_io_externalIF_writeCount;
  wire[31:0] controller_io_externalIF_writeMissCount;
  wire[23:0] controller_io_dataPortA_addr;
  wire[63:0] controller_io_dataPortA_dataIn;
  wire controller_io_dataPortA_writeEn;
  wire[23:0] controller_io_dataPortB_addr;
  wire[63:0] controller_io_dataPortB_dataIn;
  wire controller_io_dataPortB_writeEn;
  wire[23:0] controller_io_tagPortA_addr;
  wire[63:0] controller_io_tagPortA_dataIn;
  wire controller_io_tagPortA_writeEn;
  wire[23:0] controller_io_tagPortB_addr;
  wire[63:0] dataMem_io_portA_dataOut;
  wire[63:0] dataMem_io_portB_dataOut;
  wire[63:0] tagMem_io_portA_dataOut;
  wire[63:0] tagMem_io_portB_dataOut;


  assign io_writeMissCount = controller_io_externalIF_writeMissCount;
  assign io_writeCount = controller_io_externalIF_writeCount;
  assign io_readMissCount = controller_io_externalIF_readMissCount;
  assign io_readCount = controller_io_externalIF_readCount;
  assign io_cacheActive = controller_io_externalIF_cacheActive;
  assign io_memWrite_memWriteData = controller_io_externalIF_memWrite_memWriteData;
  assign io_memWrite_memWriteReq_bits = controller_io_externalIF_memWrite_memWriteReq_bits;
  assign io_memWrite_memWriteReq_valid = controller_io_externalIF_memWrite_memWriteReq_valid;
  assign io_memRead_memResp_ready = controller_io_externalIF_memRead_memResp_ready;
  assign io_memRead_memReq_bits = controller_io_externalIF_memRead_memReq_bits;
  assign io_memRead_memReq_valid = controller_io_externalIF_memRead_memReq_valid;
  assign io_writePort_writeReq_ready = controller_io_externalIF_writePort_writeReq_ready;
  assign io_readPort_readRespInd = controller_io_externalIF_readPort_readRespInd;
  assign io_readPort_readResp_bits = controller_io_externalIF_readPort_readResp_bits;
  assign io_readPort_readResp_valid = controller_io_externalIF_readPort_readResp_valid;
  assign io_readPort_readReq_ready = controller_io_externalIF_readPort_readReq_ready;
  CacheController controller(.clk(clk), .reset(reset),
       .io_externalIF_readPort_readReq_ready( controller_io_externalIF_readPort_readReq_ready ),
       .io_externalIF_readPort_readReq_valid( io_readPort_readReq_valid ),
       .io_externalIF_readPort_readReq_bits( io_readPort_readReq_bits ),
       .io_externalIF_readPort_readResp_ready( io_readPort_readResp_ready ),
       .io_externalIF_readPort_readResp_valid( controller_io_externalIF_readPort_readResp_valid ),
       .io_externalIF_readPort_readResp_bits( controller_io_externalIF_readPort_readResp_bits ),
       .io_externalIF_readPort_readRespInd( controller_io_externalIF_readPort_readRespInd ),
       .io_externalIF_writePort_writeReq_ready( controller_io_externalIF_writePort_writeReq_ready ),
       .io_externalIF_writePort_writeReq_valid( io_writePort_writeReq_valid ),
       .io_externalIF_writePort_writeReq_bits( io_writePort_writeReq_bits ),
       .io_externalIF_writePort_writeData( io_writePort_writeData ),
       .io_externalIF_memRead_memReq_ready( io_memRead_memReq_ready ),
       .io_externalIF_memRead_memReq_valid( controller_io_externalIF_memRead_memReq_valid ),
       .io_externalIF_memRead_memReq_bits( controller_io_externalIF_memRead_memReq_bits ),
       .io_externalIF_memRead_memResp_ready( controller_io_externalIF_memRead_memResp_ready ),
       .io_externalIF_memRead_memResp_valid( io_memRead_memResp_valid ),
       .io_externalIF_memRead_memResp_bits( io_memRead_memResp_bits ),
       .io_externalIF_memWrite_memWriteReq_ready( io_memWrite_memWriteReq_ready ),
       .io_externalIF_memWrite_memWriteReq_valid( controller_io_externalIF_memWrite_memWriteReq_valid ),
       .io_externalIF_memWrite_memWriteReq_bits( controller_io_externalIF_memWrite_memWriteReq_bits ),
       .io_externalIF_memWrite_memWriteData( controller_io_externalIF_memWrite_memWriteData ),
       .io_externalIF_flushCache( io_flushCache ),
       .io_externalIF_cacheActive( controller_io_externalIF_cacheActive ),
       .io_externalIF_readCount( controller_io_externalIF_readCount ),
       .io_externalIF_readMissCount( controller_io_externalIF_readMissCount ),
       .io_externalIF_writeCount( controller_io_externalIF_writeCount ),
       .io_externalIF_writeMissCount( controller_io_externalIF_writeMissCount ),
       .io_dataPortA_addr( controller_io_dataPortA_addr ),
       .io_dataPortA_dataIn( controller_io_dataPortA_dataIn ),
       .io_dataPortA_writeEn( controller_io_dataPortA_writeEn ),
       .io_dataPortA_dataOut( dataMem_io_portA_dataOut ),
       .io_dataPortB_addr( controller_io_dataPortB_addr ),
       .io_dataPortB_dataIn( controller_io_dataPortB_dataIn ),
       .io_dataPortB_writeEn( controller_io_dataPortB_writeEn ),
       .io_dataPortB_dataOut( dataMem_io_portB_dataOut ),
       .io_tagPortA_addr( controller_io_tagPortA_addr ),
       .io_tagPortA_dataIn( controller_io_tagPortA_dataIn ),
       .io_tagPortA_writeEn( controller_io_tagPortA_writeEn ),
       .io_tagPortA_dataOut( tagMem_io_portA_dataOut ),
       .io_tagPortB_addr( controller_io_tagPortB_addr ),
       .io_tagPortB_dataOut( tagMem_io_portB_dataOut )
  );
  CacheDataMemory dataMem(.clk(clk),
       .io_portA_addr( controller_io_dataPortA_addr ),
       .io_portA_dataIn( controller_io_dataPortA_dataIn ),
       .io_portA_writeEn( controller_io_dataPortA_writeEn ),
       .io_portA_dataOut( dataMem_io_portA_dataOut ),
       .io_portB_addr( controller_io_dataPortB_addr ),
       .io_portB_dataIn( controller_io_dataPortB_dataIn ),
       .io_portB_writeEn( controller_io_dataPortB_writeEn ),
       .io_portB_dataOut( dataMem_io_portB_dataOut )
  );
  CacheTagMemory tagMem(.clk(clk),
       .io_portA_addr( controller_io_tagPortA_addr ),
       .io_portA_dataIn( controller_io_tagPortA_dataIn ),
       .io_portA_writeEn( controller_io_tagPortA_writeEn ),
       .io_portA_dataOut( tagMem_io_portA_dataOut ),
       .io_portB_addr( controller_io_tagPortB_addr ),
       .io_portB_dataOut( tagMem_io_portB_dataOut )
  );
endmodule

