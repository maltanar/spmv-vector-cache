#ifndef SpMVAcceleratorDriver_H
#define SpMVAcceleratorDriver_H
#include <assert.h>
class SpMVAcceleratorDriver {
public:
 SpMVAcceleratorDriver(volatile unsigned int * baseAddr) {
  m_baseAddr = baseAddr; assert(signature() == m_signature);};
 // read+write register: startInit index: 4
 void startInit(unsigned int v) {m_baseAddr[4] = v;};
 unsigned int startInit() {return m_baseAddr[4];};
 // read+write register: startRegular index: 5
 void startRegular(unsigned int v) {m_baseAddr[5] = v;};
 unsigned int startRegular() {return m_baseAddr[5];};
 // read+write register: startWrite index: 6
 void startWrite(unsigned int v) {m_baseAddr[6] = v;};
 unsigned int startWrite() {return m_baseAddr[6];};
 // read+write register: numRows index: 7
 void numRows(unsigned int v) {m_baseAddr[7] = v;};
 unsigned int numRows() {return m_baseAddr[7];};
 // read+write register: numCols index: 8
 void numCols(unsigned int v) {m_baseAddr[8] = v;};
 unsigned int numCols() {return m_baseAddr[8];};
 // read+write register: numNZ index: 9
 void numNZ(unsigned int v) {m_baseAddr[9] = v;};
 unsigned int numNZ() {return m_baseAddr[9];};
 // read+write register: baseColPtr index: 10
 void baseColPtr(unsigned int v) {m_baseAddr[10] = v;};
 unsigned int baseColPtr() {return m_baseAddr[10];};
 // read+write register: baseRowInd index: 11
 void baseRowInd(unsigned int v) {m_baseAddr[11] = v;};
 unsigned int baseRowInd() {return m_baseAddr[11];};
 // read+write register: baseNZData index: 12
 void baseNZData(unsigned int v) {m_baseAddr[12] = v;};
 unsigned int baseNZData() {return m_baseAddr[12];};
 // read+write register: baseInputVec index: 13
 void baseInputVec(unsigned int v) {m_baseAddr[13] = v;};
 unsigned int baseInputVec() {return m_baseAddr[13];};
 // read+write register: baseOutputVec index: 14
 void baseOutputVec(unsigned int v) {m_baseAddr[14] = v;};
 unsigned int baseOutputVec() {return m_baseAddr[14];};
 // read+write register: thresColPtr index: 15
 void thresColPtr(unsigned int v) {m_baseAddr[15] = v;};
 unsigned int thresColPtr() {return m_baseAddr[15];};
 // read+write register: thresRowInd index: 16
 void thresRowInd(unsigned int v) {m_baseAddr[16] = v;};
 unsigned int thresRowInd() {return m_baseAddr[16];};
 // read+write register: thresNZData index: 17
 void thresNZData(unsigned int v) {m_baseAddr[17] = v;};
 unsigned int thresNZData() {return m_baseAddr[17];};
 // read+write register: thresInputVec index: 18
 void thresInputVec(unsigned int v) {m_baseAddr[18] = v;};
 unsigned int thresInputVec() {return m_baseAddr[18];};
 // read+write register: statBackend index: 1
 void statBackend(unsigned int v) {m_baseAddr[1] = v;};
 unsigned int statBackend() {return m_baseAddr[1];};
 // read+write register: statFrontend index: 2
 void statFrontend(unsigned int v) {m_baseAddr[2] = v;};
 unsigned int statFrontend() {return m_baseAddr[2];};
 // read+write register: hazardStalls index: 3
 void hazardStalls(unsigned int v) {m_baseAddr[3] = v;};
 unsigned int hazardStalls() {return m_baseAddr[3];};
 // read+write register: signature index: 0
 void signature(unsigned int v) {m_baseAddr[0] = v;};
 unsigned int signature() {return m_baseAddr[0];};

protected:
 volatile unsigned int * m_baseAddr;
 const static unsigned int m_signature = 0xcbbb437f;
};
#endif
