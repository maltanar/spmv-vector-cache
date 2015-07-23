#ifndef SpMVAcceleratorBufferNoneDriver_H
#define SpMVAcceleratorBufferNoneDriver_H
#include <assert.h>
class SpMVAcceleratorBufferNoneDriver {
public:
 SpMVAcceleratorBufferNoneDriver(volatile unsigned int * baseAddr) {
  m_baseAddr = baseAddr; assert(signature() == m_signature);};
 // read+write register: startRegular index: 7
 void startRegular(unsigned int v) {m_baseAddr[7] = v;};
 unsigned int startRegular() {return m_baseAddr[7];};
 // read+write register: numRows index: 8
 void numRows(unsigned int v) {m_baseAddr[8] = v;};
 unsigned int numRows() {return m_baseAddr[8];};
 // read+write register: numCols index: 9
 void numCols(unsigned int v) {m_baseAddr[9] = v;};
 unsigned int numCols() {return m_baseAddr[9];};
 // read+write register: numNZ index: 10
 void numNZ(unsigned int v) {m_baseAddr[10] = v;};
 unsigned int numNZ() {return m_baseAddr[10];};
 // read+write register: baseColPtr index: 11
 void baseColPtr(unsigned int v) {m_baseAddr[11] = v;};
 unsigned int baseColPtr() {return m_baseAddr[11];};
 // read+write register: baseRowInd index: 12
 void baseRowInd(unsigned int v) {m_baseAddr[12] = v;};
 unsigned int baseRowInd() {return m_baseAddr[12];};
 // read+write register: baseNZData index: 13
 void baseNZData(unsigned int v) {m_baseAddr[13] = v;};
 unsigned int baseNZData() {return m_baseAddr[13];};
 // read+write register: baseInputVec index: 14
 void baseInputVec(unsigned int v) {m_baseAddr[14] = v;};
 unsigned int baseInputVec() {return m_baseAddr[14];};
 // read+write register: baseOutputVec index: 15
 void baseOutputVec(unsigned int v) {m_baseAddr[15] = v;};
 unsigned int baseOutputVec() {return m_baseAddr[15];};
 // read+write register: thresColPtr index: 16
 void thresColPtr(unsigned int v) {m_baseAddr[16] = v;};
 unsigned int thresColPtr() {return m_baseAddr[16];};
 // read+write register: thresRowInd index: 17
 void thresRowInd(unsigned int v) {m_baseAddr[17] = v;};
 unsigned int thresRowInd() {return m_baseAddr[17];};
 // read+write register: thresNZData index: 18
 void thresNZData(unsigned int v) {m_baseAddr[18] = v;};
 unsigned int thresNZData() {return m_baseAddr[18];};
 // read+write register: thresInputVec index: 19
 void thresInputVec(unsigned int v) {m_baseAddr[19] = v;};
 unsigned int thresInputVec() {return m_baseAddr[19];};
 // read+write register: statBackend index: 1
 void statBackend(unsigned int v) {m_baseAddr[1] = v;};
 unsigned int statBackend() {return m_baseAddr[1];};
 // read+write register: statFrontend index: 2
 void statFrontend(unsigned int v) {m_baseAddr[2] = v;};
 unsigned int statFrontend() {return m_baseAddr[2];};
 // read+write register: bwMon_totalCycles index: 3
 void bwMon_totalCycles(unsigned int v) {m_baseAddr[3] = v;};
 unsigned int bwMon_totalCycles() {return m_baseAddr[3];};
 // read+write register: bwMon_activeCycles index: 4
 void bwMon_activeCycles(unsigned int v) {m_baseAddr[4] = v;};
 unsigned int bwMon_activeCycles() {return m_baseAddr[4];};
 // read+write register: fifoCountsCPRI index: 5
 void fifoCountsCPRI(unsigned int v) {m_baseAddr[5] = v;};
 unsigned int fifoCountsCPRI() {return m_baseAddr[5];};
 // read+write register: fifoCountsNZIV index: 6
 void fifoCountsNZIV(unsigned int v) {m_baseAddr[6] = v;};
 unsigned int fifoCountsNZIV() {return m_baseAddr[6];};
 // read+write register: signature index: 0
 void signature(unsigned int v) {m_baseAddr[0] = v;};
 unsigned int signature() {return m_baseAddr[0];};

protected:
 volatile unsigned int * m_baseAddr;
 const static unsigned int m_signature = 0xbc5b9291;
};
#endif
