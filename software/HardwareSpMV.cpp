#include "HardwareSpMV.h"
#include <assert.h>
#include <iostream>
using namespace std;

#define isAligned(x) (assert((unsigned int)x % 64 == 0))

void HardwareSpMV::resetAccelerator() {
	*m_resetBase = 1;
	*m_resetBase = 0;
}

HardwareSpMV::HardwareSpMV(unsigned int aBase, unsigned int aReset,
		SparseMatrix * A, SpMVData *x, SpMVData *y) :
		SpMV(A, x, y) {
	// make sure all pointers are aligned
	isAligned((unsigned int ) A->getIndPtrs());
	isAligned((unsigned int ) A->getInds());
	isAligned((unsigned int ) A->getNzData());
	isAligned((unsigned int ) x);
	isAligned((unsigned int ) y);

	m_accelBase = (volatile unsigned int *) aBase;
	m_resetBase = (volatile unsigned int *) aReset;
	m_acc = new SpMVAcceleratorDriver(m_accelBase);
}

HardwareSpMV::~HardwareSpMV() {
	delete m_acc;
}

void HardwareSpMV::setupRegs() {
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

void HardwareSpMV::init() {
	assert(m_acc->statFrontend() == 0);

	m_acc->startInit(1);

	while (!readFrontendStatus(frontendMaskDoneInit))
		;

	m_acc->startInit(0);
}

void HardwareSpMV::write() {
	assert(m_acc->statBackend() == 0);
	assert(m_acc->statFrontend() == 0);

	m_acc->startWrite(1);

	while (!(readBackendStatus(backendMaskDoneWrite)
			&& readFrontendStatus(frontendMaskDoneWrite)))
		;

	m_acc->startWrite(0);
}

bool HardwareSpMV::readBackendStatus(BackendStatMask mask) {
	return (m_acc->statBackend() & (unsigned int) mask) != 0;
}

bool HardwareSpMV::readFrontendStatus(FrontendStatMask mask) {
	return (m_acc->statFrontend() & (unsigned int) mask) != 0;
}

bool HardwareSpMV::exec() {
	// TODO init + regular + write + reset
	resetAccelerator();
	setupRegs();
	init();
	regular();
	write();

	return false;
}

void HardwareSpMV::regular() {
	assert(m_acc->statBackend() == 0);
	assert(m_acc->statFrontend() == 0);

	m_acc->startRegular(1);
	while (!(readBackendStatus(backendMaskDoneRegular)
				&& readFrontendStatus(frontendMaskDoneRegular)))
			;

	cout << "Hazard stalls: " << m_acc->hazardStalls() << endl;
	cout << "Active cycles: " << m_acc->bwMon_activeCycles() << endl;
	cout << "Total cycles: " << m_acc->bwMon_totalCycles() << endl;
	float act = (float)(m_acc->bwMon_activeCycles()) / (float)(m_acc->bwMon_totalCycles());
	cout << "Active/Total = " << act << endl;

	m_acc->startRegular(0);
}
