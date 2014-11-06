`define SYNTHESIS
module CacheController(input clk, input reset,
    output io_externalIF_readPort_readReq_ready,
    input  io_externalIF_readPort_readReq_valid,
    input [24:0] io_externalIF_readPort_readReq_bits,
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
    output[31:0] io_externalIF_coldSkipCount,
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

  wire[23:0] T130;
  wire[14:0] currentWriteReqInd;
  wire T0;
  wire T1;
  wire T2;
  reg [3:0] state;
  wire[3:0] T131;
  wire[3:0] T3;
  wire[3:0] T4;
  wire[3:0] T5;
  wire[3:0] T6;
  wire[3:0] T7;
  wire[3:0] T8;
  wire[3:0] T9;
  wire[3:0] T10;
  wire[3:0] T11;
  wire[3:0] T12;
  wire T13;
  wire T14;
  reg [14:0] initCtr;
  wire[14:0] T132;
  wire[14:0] T15;
  wire[14:0] T16;
  wire[14:0] T17;
  wire[14:0] T18;
  wire[14:0] T19;
  wire[14:0] T20;
  wire[14:0] T21;
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
  wire T39;
  wire T40;
  wire currentReqLineValid;
  wire T41;
  wire[8:0] currentReqLineTag;
  wire[8:0] currentReqTag;
  wire[23:0] currentReq;
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
  wire[3:0] T55;
  wire currentReqCold;
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
  wire T67;
  wire T68;
  wire T69;
  wire T70;
  wire T71;
  wire T72;
  wire T73;
  wire[63:0] T133;
  wire[9:0] T74;
  wire[9:0] T75;
  wire[23:0] T134;
  wire[14:0] T76;
  wire[14:0] T77;
  wire[14:0] currentReqInd;
  wire[14:0] T78;
  wire T79;
  wire T80;
  wire T81;
  wire currentWriteReqLineValid;
  wire T82;
  wire[8:0] currentWriteReqLineTag;
  wire[8:0] currentWriteReqTag;
  wire T83;
  wire T84;
  wire T85;
  wire T86;
  wire T87;
  wire[23:0] T135;
  wire T88;
  wire[63:0] T89;
  wire[23:0] T136;
  wire[14:0] T90;
  wire[14:0] T91;
  reg [31:0] coldSkipCount;
  wire[31:0] T137;
  wire[31:0] T92;
  wire[31:0] T93;
  reg [31:0] writeMissCount;
  wire[31:0] T138;
  wire[31:0] T94;
  wire[31:0] T95;
  wire[31:0] T96;
  wire T97;
  wire T98;
  wire T99;
  wire[31:0] T100;
  wire T101;
  wire T102;
  wire T103;
  reg [31:0] writeCount;
  wire[31:0] T139;
  wire[31:0] T104;
  wire[31:0] T105;
  wire[31:0] T106;
  wire[31:0] T107;
  wire[31:0] T108;
  wire[31:0] T109;
  wire[31:0] T110;
  wire[31:0] T111;
  reg [31:0] readMissCount;
  wire[31:0] T140;
  wire[31:0] T112;
  wire[31:0] T113;
  reg [31:0] readCount;
  wire[31:0] T141;
  wire[31:0] T114;
  wire[31:0] T115;
  wire T116;
  wire T117;
  wire[63:0] T118;
  wire[63:0] T119;
  wire[23:0] T120;
  wire[23:0] T121;
  wire[23:0] T122;
  wire[23:0] T123;
  wire T124;
  wire T125;
  wire T126;
  wire T127;
  wire T128;
  wire T129;
  reg [23:0] prevReadRequestReg;
  reg  enableReadRespReg;
  wire T142;

`ifndef SYNTHESIS
  integer initvar;
  initial begin
    #0.002;
    state = {1{$random}};
    initCtr = {1{$random}};
    coldSkipCount = {1{$random}};
    writeMissCount = {1{$random}};
    writeCount = {1{$random}};
    readMissCount = {1{$random}};
    readCount = {1{$random}};
    prevReadRequestReg = {1{$random}};
    enableReadRespReg = {1{$random}};
  end
`endif

  assign io_tagPortB_addr = T130;
  assign T130 = {9'h0, currentWriteReqInd};
  assign currentWriteReqInd = io_externalIF_writePort_writeReq_bits[4'he:1'h0];
  assign io_tagPortA_writeEn = T0;
  assign T0 = T69 ? 1'h1 : T1;
  assign T1 = T67 ? 1'h1 : T2;
  assign T2 = state == 4'h0;
  assign T131 = reset ? 4'h0 : T3;
  assign T3 = T69 ? 4'h1 : T4;
  assign T4 = T61 ? 4'h5 : T5;
  assign T5 = T67 ? 4'h1 : T6;
  assign T6 = T56 ? T55 : T7;
  assign T7 = T49 ? 4'h4 : T8;
  assign T8 = T37 ? 4'h2 : T9;
  assign T9 = T32 ? 4'h6 : T10;
  assign T10 = T30 ? 4'h1 : T11;
  assign T11 = T28 ? 4'h7 : T12;
  assign T12 = T13 ? 4'h1 : state;
  assign T13 = T2 & T14;
  assign T14 = initCtr == 15'h7fff;
  assign T132 = reset ? 15'h0 : T15;
  assign T15 = T32 ? 15'h0 : T16;
  assign T16 = T22 ? T21 : T17;
  assign T17 = T28 ? T20 : T18;
  assign T18 = T2 ? T19 : initCtr;
  assign T19 = initCtr + 15'h1;
  assign T20 = initCtr + 15'h1;
  assign T21 = initCtr + 15'h1;
  assign T22 = T23 & io_externalIF_memWrite_memWriteReq_ready;
  assign T23 = T25 & T24;
  assign T24 = state == 4'h7;
  assign T25 = T26 ^ 1'h1;
  assign T26 = T2 | T27;
  assign T27 = state == 4'h6;
  assign T28 = T29 & T27;
  assign T29 = T2 ^ 1'h1;
  assign T30 = T22 & T31;
  assign T31 = initCtr == 15'h0;
  assign T32 = T33 & io_externalIF_flushCache;
  assign T33 = T35 & T34;
  assign T34 = state == 4'h1;
  assign T35 = T36 ^ 1'h1;
  assign T36 = T26 | T24;
  assign T37 = T46 & T38;
  assign T38 = T42 & T39;
  assign T39 = T41 | T40;
  assign T40 = ~ currentReqLineValid;
  assign currentReqLineValid = io_tagPortA_dataOut[1'h0:1'h0];
  assign T41 = currentReqTag != currentReqLineTag;
  assign currentReqLineTag = io_tagPortA_dataOut[4'h9:1'h1];
  assign currentReqTag = currentReq[5'h17:4'hf];
  assign currentReq = io_externalIF_readPort_readReq_bits[5'h17:1'h0];
  assign T42 = T43 ^ 1'h1;
  assign T43 = T44 & io_externalIF_readPort_readResp_ready;
  assign T44 = T45 & currentReqLineValid;
  assign T45 = currentReqTag == currentReqLineTag;
  assign T46 = T47 & io_externalIF_readPort_readReq_valid;
  assign T47 = T33 & T48;
  assign T48 = io_externalIF_flushCache ^ 1'h1;
  assign T49 = T51 & T50;
  assign T50 = io_externalIF_writePort_writeReq_valid ^ 1'h1;
  assign T51 = T53 & T52;
  assign T52 = state == 4'h2;
  assign T53 = T54 ^ 1'h1;
  assign T54 = T36 | T34;
  assign T55 = currentReqCold ? 4'h8 : 4'h3;
  assign currentReqCold = io_externalIF_readPort_readReq_bits[5'h18:5'h18];
  assign T56 = T57 & io_externalIF_memWrite_memWriteReq_ready;
  assign T57 = T59 & T58;
  assign T58 = state == 4'h4;
  assign T59 = T60 ^ 1'h1;
  assign T60 = T54 | T52;
  assign T61 = T63 & T62;
  assign T62 = state == 4'h3;
  assign T63 = T64 ^ 1'h1;
  assign T64 = T66 | T65;
  assign T65 = state == 4'h8;
  assign T66 = T60 | T58;
  assign T67 = T68 & T65;
  assign T68 = T66 ^ 1'h1;
  assign T69 = T70 & io_externalIF_memRead_memResp_valid;
  assign T70 = T72 & T71;
  assign T71 = state == 4'h5;
  assign T72 = T73 ^ 1'h1;
  assign T73 = T64 | T62;
  assign io_tagPortA_dataIn = T133;
  assign T133 = {54'h0, T74};
  assign T74 = T2 ? 10'h0 : T75;
  assign T75 = {currentReqTag, 1'h1};
  assign io_tagPortA_addr = T134;
  assign T134 = {9'h0, T76};
  assign T76 = T22 ? T78 : T77;
  assign T77 = T2 ? initCtr : currentReqInd;
  assign currentReqInd = currentReq[4'he:1'h0];
  assign T78 = initCtr - 15'h1;
  assign io_dataPortB_writeEn = T79;
  assign T79 = T84 ? 1'h1 : T80;
  assign T80 = T83 & T81;
  assign T81 = T82 & currentWriteReqLineValid;
  assign currentWriteReqLineValid = io_tagPortB_dataOut[1'h0:1'h0];
  assign T82 = currentWriteReqTag == currentWriteReqLineTag;
  assign currentWriteReqLineTag = io_tagPortB_dataOut[4'h9:1'h1];
  assign currentWriteReqTag = io_externalIF_writePort_writeReq_bits[5'h17:4'hf];
  assign T83 = T47 & io_externalIF_writePort_writeReq_valid;
  assign T84 = T87 & T85;
  assign T85 = T86 & currentWriteReqLineValid;
  assign T86 = currentWriteReqTag == currentWriteReqLineTag;
  assign T87 = T51 & io_externalIF_writePort_writeReq_valid;
  assign io_dataPortB_dataIn = io_externalIF_writePort_writeData;
  assign io_dataPortB_addr = T135;
  assign T135 = {9'h0, currentWriteReqInd};
  assign io_dataPortA_writeEn = T88;
  assign T88 = T69 ? 1'h1 : T67;
  assign io_dataPortA_dataIn = T89;
  assign T89 = T67 ? 64'h0 : io_externalIF_memRead_memResp_bits;
  assign io_dataPortA_addr = T136;
  assign T136 = {9'h0, T90};
  assign T90 = T23 ? initCtr : T91;
  assign T91 = T28 ? initCtr : currentReqInd;
  assign io_externalIF_coldSkipCount = coldSkipCount;
  assign T137 = reset ? 32'h0 : T92;
  assign T92 = T67 ? T93 : coldSkipCount;
  assign T93 = coldSkipCount + 32'h1;
  assign io_externalIF_writeMissCount = writeMissCount;
  assign T138 = reset ? 32'h0 : T94;
  assign T94 = T101 ? T100 : T95;
  assign T95 = T97 ? T96 : writeMissCount;
  assign T96 = writeMissCount + 32'h1;
  assign T97 = T83 & T98;
  assign T98 = T99 & io_externalIF_memWrite_memWriteReq_ready;
  assign T99 = T81 ^ 1'h1;
  assign T100 = writeMissCount + 32'h1;
  assign T101 = T87 & T102;
  assign T102 = T103 & io_externalIF_memWrite_memWriteReq_ready;
  assign T103 = T85 ^ 1'h1;
  assign io_externalIF_writeCount = writeCount;
  assign T139 = reset ? 32'h0 : T104;
  assign T104 = T101 ? T111 : T105;
  assign T105 = T84 ? T110 : T106;
  assign T106 = T97 ? T109 : T107;
  assign T107 = T80 ? T108 : writeCount;
  assign T108 = writeCount + 32'h1;
  assign T109 = writeCount + 32'h1;
  assign T110 = writeCount + 32'h1;
  assign T111 = writeCount + 32'h1;
  assign io_externalIF_readMissCount = readMissCount;
  assign T140 = reset ? 32'h0 : T112;
  assign T112 = T37 ? T113 : readMissCount;
  assign T113 = readMissCount + 32'h1;
  assign io_externalIF_readCount = readCount;
  assign T141 = reset ? 32'h0 : T114;
  assign T114 = T116 ? T115 : readCount;
  assign T115 = readCount + 32'h1;
  assign T116 = T46 & T43;
  assign io_externalIF_cacheActive = T117;
  assign T117 = state == 4'h1;
  assign io_externalIF_memWrite_memWriteData = T118;
  assign T118 = T56 ? io_dataPortA_dataOut : T119;
  assign T119 = T22 ? io_dataPortA_dataOut : io_externalIF_writePort_writeData;
  assign io_externalIF_memWrite_memWriteReq_bits = T120;
  assign T120 = T56 ? T123 : T121;
  assign T121 = T22 ? T122 : io_externalIF_writePort_writeReq_bits;
  assign T122 = {currentReqLineTag, T78};
  assign T123 = {currentReqLineTag, currentReqInd};
  assign io_externalIF_memWrite_memWriteReq_valid = T124;
  assign T124 = T56 ? currentReqLineValid : T125;
  assign T125 = T101 ? 1'h1 : T126;
  assign T126 = T97 ? 1'h1 : T127;
  assign T127 = T22 ? currentReqLineValid : 1'h0;
  assign io_externalIF_memRead_memResp_ready = T70;
  assign io_externalIF_memRead_memReq_bits = currentReq;
  assign io_externalIF_memRead_memReq_valid = T61;
  assign io_externalIF_writePort_writeReq_ready = T128;
  assign T128 = T51 ? io_externalIF_memWrite_memWriteReq_ready : T129;
  assign T129 = T47 ? io_externalIF_memWrite_memWriteReq_ready : 1'h0;
  assign io_externalIF_readPort_readRespInd = prevReadRequestReg;
  assign io_externalIF_readPort_readResp_bits = io_dataPortA_dataOut;
  assign io_externalIF_readPort_readResp_valid = enableReadRespReg;
  assign T142 = reset ? 1'h0 : T116;
  assign io_externalIF_readPort_readReq_ready = T116;

  always @(posedge clk) begin
    if(reset) begin
      state <= 4'h0;
    end else if(T69) begin
      state <= 4'h1;
    end else if(T61) begin
      state <= 4'h5;
    end else if(T67) begin
      state <= 4'h1;
    end else if(T56) begin
      state <= T55;
    end else if(T49) begin
      state <= 4'h4;
    end else if(T37) begin
      state <= 4'h2;
    end else if(T32) begin
      state <= 4'h6;
    end else if(T30) begin
      state <= 4'h1;
    end else if(T28) begin
      state <= 4'h7;
    end else if(T13) begin
      state <= 4'h1;
    end
    if(reset) begin
      initCtr <= 15'h0;
    end else if(T32) begin
      initCtr <= 15'h0;
    end else if(T22) begin
      initCtr <= T21;
    end else if(T28) begin
      initCtr <= T20;
    end else if(T2) begin
      initCtr <= T19;
    end
    if(reset) begin
      coldSkipCount <= 32'h0;
    end else if(T67) begin
      coldSkipCount <= T93;
    end
    if(reset) begin
      writeMissCount <= 32'h0;
    end else if(T101) begin
      writeMissCount <= T100;
    end else if(T97) begin
      writeMissCount <= T96;
    end
    if(reset) begin
      writeCount <= 32'h0;
    end else if(T101) begin
      writeCount <= T111;
    end else if(T84) begin
      writeCount <= T110;
    end else if(T97) begin
      writeCount <= T109;
    end else if(T80) begin
      writeCount <= T108;
    end
    if(reset) begin
      readMissCount <= 32'h0;
    end else if(T37) begin
      readMissCount <= T113;
    end
    if(reset) begin
      readCount <= 32'h0;
    end else if(T116) begin
      readCount <= T115;
    end
    prevReadRequestReg <= currentReq;
    if(reset) begin
      enableReadRespReg <= 1'h0;
    end else begin
      enableReadRespReg <= T116;
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
  reg [63:0] cacheLines [32767:0];
  wire[63:0] T2;
  wire[14:0] T6;
  wire[63:0] T3;
  wire[14:0] T7;
  wire[14:0] T8;
  reg [63:0] R4;
  wire[63:0] T5;
  wire[14:0] T9;

`ifndef SYNTHESIS
  integer initvar;
  initial begin
    #0.002;
    R0 = {2{$random}};
    for (initvar = 0; initvar < 32768; initvar = initvar+1)
      cacheLines[initvar] = {2{$random}};
    R4 = {2{$random}};
  end
`endif

  assign io_portB_dataOut = R0;
  assign T1 = cacheLines[T8];
  assign T6 = io_portB_addr[4'he:1'h0];
  assign T7 = io_portA_addr[4'he:1'h0];
  assign T8 = io_portB_addr[4'he:1'h0];
  assign io_portA_dataOut = R4;
  assign T5 = cacheLines[T9];
  assign T9 = io_portA_addr[4'he:1'h0];

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
  wire[9:0] T0;
  reg [9:0] tagStorage [32767:0];
  wire[9:0] T1;
  wire[9:0] T4;
  wire[14:0] T5;
  wire[14:0] T6;
  wire[63:0] T7;
  wire[9:0] T2;
  wire[14:0] T8;

`ifndef SYNTHESIS
  integer initvar;
  initial begin
    #0.002;
    for (initvar = 0; initvar < 32768; initvar = initvar+1)
      tagStorage[initvar] = {1{$random}};
  end
`endif

  assign io_portB_dataOut = T3;
  assign T3 = {54'h0, T0};
  assign T0 = tagStorage[T6];
  assign T4 = io_portA_dataIn[4'h9:1'h0];
  assign T5 = io_portA_addr[4'he:1'h0];
  assign T6 = io_portB_addr[4'he:1'h0];
  assign io_portA_dataOut = T7;
  assign T7 = {54'h0, T2};
  assign T2 = tagStorage[T8];
  assign T8 = io_portA_addr[4'he:1'h0];

  always @(posedge clk) begin
    if (io_portA_writeEn)
      tagStorage[T5] <= T4;
  end
endmodule

module ColdMissSkipVectorCache(input clk, input reset,
    output io_readPort_readReq_ready,
    input  io_readPort_readReq_valid,
    input [24:0] io_readPort_readReq_bits,
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
    output[31:0] io_writeMissCount,
    output[31:0] io_coldSkipCount
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
  wire[31:0] controller_io_externalIF_coldSkipCount;
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


  assign io_coldSkipCount = controller_io_externalIF_coldSkipCount;
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
       .io_externalIF_coldSkipCount( controller_io_externalIF_coldSkipCount ),
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

