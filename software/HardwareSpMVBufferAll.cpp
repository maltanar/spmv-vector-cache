#include "HardwareSpMVBufferAll.h"
#include <assert.h>
#include <iostream>
using namespace std;

HardwareSpMVBufferAll::HardwareSpMVBufferAll(unsigned int aBase, unsigned int aReset,
		SparseMatrix * A, SpMVData *x, SpMVData *y) :
		HardwareSpMV(aBase, aReset, A, x, y) {
	m_acc = new SpMVAcceleratorBufferAllDriver(m_accelBase);

	// buffer-all: ensure all rows will fit within OCM
	cout << "# OCM words: " << m_acc->ocmWords() << endl;
	assert(A->getRows() <= m_acc->ocmWords());
}

HardwareSpMVBufferAll::~HardwareSpMVBufferAll() {
	delete m_acc;
}

void HardwareSpMVBufferAll::setupRegs() {
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

void HardwareSpMVBufferAll::init() {
	assert(m_acc->statFrontend() == 0);

	m_acc->startInit(1);

	while (!readFrontendStatus(frontendMaskDoneInit))
		;

	m_acc->startInit(0);
}

void HardwareSpMVBufferAll::write() {
	assert(m_acc->statBackend() == 0);
	assert(m_acc->statFrontend() == 0);

	m_acc->startWrite(1);

	while (!(readBackendStatus(backendMaskDoneWrite)
			&& readFrontendStatus(frontendMaskDoneWrite)))
		;

	m_acc->startWrite(0);
}

bool HardwareSpMVBufferAll::readBackendStatus(BackendStatMask mask) {
	return (m_acc->statBackend() & (unsigned int) mask) != 0;
}

bool HardwareSpMVBufferAll::readFrontendStatus(FrontendStatMask mask) {
	return (m_acc->statFrontend() & (unsigned int) mask) != 0;
}

bool HardwareSpMVBufferAll::exec() {
	// TODO init + regular + write + reset
	resetAccelerator();
	setupRegs();
	init();
	regular();
	write();

	return false;
}

void HardwareSpMVBufferAll::regular() {
	assert(m_acc->statBackend() == 0);
	assert(m_acc->statFrontend() == 0);

	m_acc->startRegular(1);
	while (!(readBackendStatus(backendMaskDoneRegular)
			&& readFrontendStatus(frontendMaskDoneRegular)))
		;

	cout << "Hazard stalls: " << m_acc->hazardStalls() << endl;
	cout << "Active cycles: " << m_acc->bwMon_activeCycles() << endl;
	cout << "Total cycles: " << m_acc->bwMon_totalCycles() << endl;
	float act = (float) (m_acc->bwMon_activeCycles())
			/ (float) (m_acc->bwMon_totalCycles());
	cout << "Active/Total = " << act << endl;
	//printAllFIFOLevels();

	m_acc->startRegular(0);
}

volatile unsigned short HardwareSpMVBufferAll::getFIFOLevel(SpMVFIFONum num) {
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

void HardwareSpMVBufferAll::printAllFIFOLevels() {
	cout << "FIFO levels" << endl << "=================" << endl;
	cout << "ColPtr: " << getFIFOLevel(fifoColPtr) << endl;
	cout << "RowInd: " << getFIFOLevel(fifoRowInd) << endl;
	cout << "NZData: " << getFIFOLevel(fifoNZData) << endl;
	cout << "InpVec: " << getFIFOLevel(fifoInpVec) << endl;
}
