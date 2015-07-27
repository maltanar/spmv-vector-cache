#include "HardwareSpMVBufferSel.h"
#include <assert.h>
#include <iostream>
using namespace std;

#define isAligned(x) (assert((unsigned int)x % 64 == 0))

void HardwareSpMVBufferSel::resetAccelerator() {
	*m_resetBase = 1;
	*m_resetBase = 0;
}

HardwareSpMVBufferSel::HardwareSpMVBufferSel(unsigned int aBase,
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
	m_acc = new SpMVAcceleratorBufferSelDriver(m_accelBase);

	cout << "# OCM words: " << m_acc->ocmWords() << endl;
	cout << "DDR issue window: " << m_acc->issueWindow() << endl;

	m_totalCycles = 0;
	m_activeCycles = 0;
	m_hazardStallsOCM = 0;
	m_hazardStallsDDR = 0;
	m_capacityStallsOCM = 0;
	m_capacityStallsDDR = 0;
}

HardwareSpMVBufferSel::~HardwareSpMVBufferSel() {
	delete m_acc;
}

void HardwareSpMVBufferSel::setupRegs() {
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

void HardwareSpMVBufferSel::init() {
	assert(m_acc->statFrontend() == 0);

	m_acc->startInit(1);

	while (!readFrontendStatus(frontendMaskDoneInit))
		;

	m_acc->startInit(0);
}

void HardwareSpMVBufferSel::write() {
	assert(m_acc->statBackend() == 0);
	assert(m_acc->statFrontend() == 0);

	m_acc->startWrite(1);

	while (!(readBackendStatus(backendMaskDoneWrite)
			&& readFrontendStatus(frontendMaskDoneWrite)))
		;

	m_acc->startWrite(0);
}

bool HardwareSpMVBufferSel::readBackendStatus(BackendStatMask mask) {
	return (m_acc->statBackend() & (unsigned int) mask) != 0;
}

bool HardwareSpMVBufferSel::readFrontendStatus(FrontendStatMask mask) {
	return (m_acc->statFrontend() & (unsigned int) mask) != 0;
}

bool HardwareSpMVBufferSel::exec() {
	// TODO init + regular + write + reset
	resetAccelerator();
	setupRegs();
	init();
	regular();
	write();

	printAllStatistics();

	return false;
}

void HardwareSpMVBufferSel::regular() {
	assert(m_acc->statBackend() == 0);
	assert(m_acc->statFrontend() == 0);

	m_acc->startRegular(1);
	while (!(readBackendStatus(backendMaskDoneRegular)
			&& readFrontendStatus(frontendMaskDoneRegular)))
		;

	updateStatistics();
	m_acc->startRegular(0);
}

volatile unsigned short HardwareSpMVBufferSel::getFIFOLevel(SpMVFIFONum num) {
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

void HardwareSpMVBufferSel::printAllFIFOLevels() {
	cout << "FIFO levels" << endl << "=================" << endl;
	cout << "ColPtr: " << getFIFOLevel(fifoColPtr) << endl;
	cout << "RowInd: " << getFIFOLevel(fifoRowInd) << endl;
	cout << "NZData: " << getFIFOLevel(fifoNZData) << endl;
	cout << "InpVec: " << getFIFOLevel(fifoInpVec) << endl;
}

void HardwareSpMVBufferSel::updateStatistics() {
	m_totalCycles = m_acc->bwMon_totalCycles();
	m_activeCycles = m_acc->bwMon_activeCycles();
	m_hazardStallsOCM = m_acc->hazardStallsOCM();
	m_hazardStallsDDR = m_acc->hazardStallsDDR();
	m_capacityStallsOCM = m_acc->capacityStallsOCM();
	m_capacityStallsDDR = m_acc->capacityStallsDDR();
}

void HardwareSpMVBufferSel::printAllStatistics() {
	cout << "OCM hazard stalls: " << m_hazardStallsOCM << endl;
	cout << "OCM capacity stalls: " << m_capacityStallsOCM << endl;
	cout << "DDR hazard stalls: " << m_hazardStallsDDR << endl;
	cout << "DDR capacity stalls: " << m_capacityStallsDDR << endl;

	cout << "Active cycles: " << m_activeCycles << endl;
	cout << "Total cycles: " << m_totalCycles << endl;
	float act = (float) m_activeCycles / (float) m_totalCycles;
	cout << "Active/Total = " << act << endl;
}
