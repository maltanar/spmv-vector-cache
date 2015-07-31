#include "HardwareSpMVNewCache.h"
#include <assert.h>
#include <iostream>
#include <string.h>
using namespace std;

#define isAligned(x) (assert((unsigned int)x % 64 == 0))

void HardwareSpMVNewCache::resetAccelerator() {
	*m_resetBase = 1;
	*m_resetBase = 0;
}

HardwareSpMVNewCache::HardwareSpMVNewCache(unsigned int aBase,
		unsigned int aReset, SparseMatrix * A, SpMVData *x, SpMVData *y) :
		SpMV(A, x, y) {
	// make sure all pointers are aligned
	isAligned((unsigned int ) A->getIndPtrs());
	isAligned((unsigned int ) A->getInds());
	isAligned((unsigned int ) A->getNzData());
	isAligned((unsigned int ) x);
	isAligned((unsigned int ) y);

	m_accelBase = (volatile unsigned int *) aBase;
	m_resetBase = (volatile unsigned int *) aReset;
	m_acc = new SpMVAcceleratorNewCacheDriver(m_accelBase);

	cout << "# cachelines: " << m_acc->ocmWords() << endl;
	cout << "Issue window: " << m_acc->issueWindow() << endl;

	m_totalCycles = 0;
	m_activeCycles = 0;
  m_readMisses = 0;
  m_conflictMisses = 0;
  memset(m_stateCounts, 0, PROFILER_STATES*4);
}

HardwareSpMVNewCache::~HardwareSpMVNewCache() {
	delete m_acc;
}

void HardwareSpMVNewCache::setupRegs() {
	m_acc->numCols(m_A->getCols());
	m_acc->numNZ(m_A->getNz());
	m_acc->numRows(m_A->getRows());

	m_acc->baseColPtr((unsigned int) m_A->getIndPtrs());
	m_acc->baseRowInd((unsigned int) m_A->getInds());
	m_acc->baseNZData((unsigned int) m_A->getNzData());

	m_acc->baseInputVec((unsigned int) m_x);
	m_acc->baseOutputVec((unsigned int) m_y);

	// setup thresholds
	m_acc->thresColPtr(128);
	m_acc->thresInputVec(128);
	m_acc->thresNZData(256);
	m_acc->thresRowInd(256);
}

void HardwareSpMVNewCache::init() {
	assert(m_acc->statFrontend() == 0);

	m_acc->startInit(1);

	while (!readFrontendStatus(frontendMaskDoneInit))
		;

	m_acc->startInit(0);
}

void HardwareSpMVNewCache::write() {
	assert(m_acc->statBackend() == 0);
	assert(m_acc->statFrontend() == 0);

	m_acc->startWrite(1);

	while (!(readBackendStatus(backendMaskDoneWrite)
			&& readFrontendStatus(frontendMaskDoneWrite)))
		;

	m_acc->startWrite(0);
}

bool HardwareSpMVNewCache::readBackendStatus(BackendStatMask mask) {
	return (m_acc->statBackend() & (unsigned int) mask) != 0;
}

bool HardwareSpMVNewCache::readFrontendStatus(FrontendStatMask mask) {
	return (m_acc->statFrontend() & (unsigned int) mask) != 0;
}

bool HardwareSpMVNewCache::exec() {
	resetAccelerator();
	setupRegs();
	init();
	regular();
	write();

	printAllStatistics();

	return false;
}

void HardwareSpMVNewCache::regular() {
	assert(m_acc->statBackend() == 0);
	assert(m_acc->statFrontend() == 0);

	m_acc->startRegular(1);
	while (!(readBackendStatus(backendMaskDoneRegular)
			&& readFrontendStatus(frontendMaskDoneRegular)))
		;

	updateStatistics();
	m_acc->startRegular(0);
}

volatile unsigned short HardwareSpMVNewCache::getFIFOLevel(SpMVFIFONum num) {
	switch (num) {
	case fifoColPtr:
		return (m_acc->fifoCountsCPRI() & 0xffff0000) >> 16;
		break;
	case fifoRowInd:
		return (m_acc->fifoCountsCPRI() & 0xffff);
		break;
	case fifoNZData:
		return (m_acc->fifoCountsNZIV() & 0xffff0000) >> 16;
		break;
	case fifoInpVec:
		return (m_acc->fifoCountsNZIV() & 0xffff);
		break;
	default:
		return 0;
	}
}

void HardwareSpMVNewCache::printAllFIFOLevels() {
	cout << "FIFO levels" << endl << "=================" << endl;
	cout << "ColPtr: " << getFIFOLevel(fifoColPtr) << endl;
	cout << "RowInd: " << getFIFOLevel(fifoRowInd) << endl;
	cout << "NZData: " << getFIFOLevel(fifoNZData) << endl;
	cout << "InpVec: " << getFIFOLevel(fifoInpVec) << endl;
}

void HardwareSpMVNewCache::updateStatistics() {
	m_totalCycles = m_acc->bwMon_totalCycles();
	m_activeCycles = m_acc->bwMon_activeCycles();

	m_readMisses = m_acc->readMissCount();
  m_conflictMisses = m_acc->conflictMissCount();
  for(unsigned int i = 0; i < PROFILER_STATES; i++) {
    m_acc->profileSel(i);
    m_stateCounts[i] = m_acc->profileCount();
  }
}

void HardwareSpMVNewCache::printAllStatistics() {
  cout << "Read misses: " << m_readMisses << endl;
  cout << "Conflict misses: " << m_conflictMisses << endl;
  
  const char * stateNames[] = {"sActive ", "sFill ", "sFlush ", "sDone ", "sReadMiss1 ", "sReadMiss2 ", "sReadMiss3"};
  for(unsigned int i = 0; i < PROFILER_STATES; i++) {
    cout << "Cache state = " << stateNames[i] << " = " << m_stateCounts[i] << endl;
  }

	cout << "Active cycles: " << m_activeCycles << endl;
	cout << "Total cycles: " << m_totalCycles << endl;
	float act = (float) m_activeCycles / (float) m_totalCycles;
	cout << "Active/Total = " << act << endl;
}
