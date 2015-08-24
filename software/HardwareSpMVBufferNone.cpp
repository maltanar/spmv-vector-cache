#include "HardwareSpMVBufferNone.h"
#include <assert.h>
#include <iostream>
#include "xil_cache.h"
using namespace std;

HardwareSpMVBufferNone::HardwareSpMVBufferNone(unsigned int aBase,
		unsigned int aReset, SparseMatrix * A, SpMVData *x, SpMVData *y) :
		HardwareSpMV(aBase, aReset, A, x, y) {
	m_acc = new SpMVAcceleratorBufferNoneDriver(m_accelBase);

	//cout << "Issue window: " << m_acc->issueWindow() << endl;

	m_totalCycles = 0;
	m_activeCycles = 0;
	m_hazardStalls = 0;
	m_capacityStalls = 0;
	m_noValidButReady = 0;
	m_noReadyButValid = 0;
}

HardwareSpMVBufferNone::~HardwareSpMVBufferNone() {
	delete m_acc;
}

void HardwareSpMVBufferNone::setupRegs() {
	HardwareSpMV::setupRegs();

	m_acc->numCols(m_A->getCols());
	m_acc->numNZ(m_A->getNz());
	m_acc->numRows(m_A->getRows());

	m_acc->baseColPtr((unsigned int) m_A->getIndPtrs());
	m_acc->baseRowInd((unsigned int) m_A->getInds());
	m_acc->baseNZData((unsigned int) m_A->getNzData());

	m_acc->baseInputVec((unsigned int) m_x);
	m_acc->baseOutputVec((unsigned int) m_y);
}

bool HardwareSpMVBufferNone::readBackendStatus(BackendStatMask mask) {
	return (m_acc->statBackend() & (unsigned int) mask) != 0;
}

bool HardwareSpMVBufferNone::readFrontendStatus(FrontendStatMask mask) {
	return (m_acc->statFrontend() & (unsigned int) mask) != 0;
}

bool HardwareSpMVBufferNone::exec() {
	resetAccelerator();
	setupRegs();

	regular();
	//printAllStatistics();

	return false;
}

void HardwareSpMVBufferNone::regular() {
	assert(m_acc->statBackend() == 0);
	assert(m_acc->statFrontend() == 0);

	m_acc->startRegular(1);
	while (!(readBackendStatus(backendMaskDoneRegular)
			&& readFrontendStatus(frontendMaskDoneRegular)))
		;

	updateStatistics();

	m_acc->startRegular(0);
}

volatile unsigned short HardwareSpMVBufferNone::getFIFOLevel(SpMVFIFONum num) {
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

void HardwareSpMVBufferNone::printAllFIFOLevels() {
	cout << "FIFO levels" << endl << "=================" << endl;
	cout << "ColPtr: " << getFIFOLevel(fifoColPtr) << endl;
	cout << "RowInd: " << getFIFOLevel(fifoRowInd) << endl;
	cout << "NZData: " << getFIFOLevel(fifoNZData) << endl;
	cout << "InpVec: " << getFIFOLevel(fifoInpVec) << endl;
}

void HardwareSpMVBufferNone::printAllStatistics() {
	cout << "Hazard stalls: " << m_hazardStalls << endl;
	cout << "Capacity stalls: " << m_capacityStalls << endl;
	cout << "Active cycles: " << m_activeCycles << endl;
	cout << "Total cycles: " << m_totalCycles << endl;
	float act = (float) m_activeCycles / (float) m_totalCycles;
	cout << "Active/Total = " << act << endl;
}

void HardwareSpMVBufferNone::updateStatistics() {
	m_totalCycles = m_acc->bwMon_totalCycles();
	m_activeCycles = m_acc->bwMon_activeCycles();
	m_hazardStalls = m_acc->hazardStalls();
	m_capacityStalls = m_acc->capacityStalls();
	m_noValidButReady = m_acc->bwMon_noValidButReady();
	m_noReadyButValid = m_acc->bwMon_noReadyButValid();
}

unsigned int HardwareSpMVBufferNone::statInt(std::string name) {
	if (name == "totalCycles")
		return m_totalCycles;
	else if (name == "activeCycles")
		return m_activeCycles;
	else if (name == "capacityStalls")
		return m_capacityStalls;
	else if (name == "issueWindow")
		return m_acc->issueWindow();
	else if (name == "hazardStalls")
		return m_hazardStalls;
	else if (name == "noValidButReady")
		return m_noValidButReady;
	else if (name == "noReadyButValid")
		return m_noReadyButValid;
	else
		return HardwareSpMV::statInt(name);
}

std::vector<std::string> HardwareSpMVBufferNone::statKeys() {
	vector<string> keys = HardwareSpMV::statKeys();

	keys.push_back("totalCycles");
	keys.push_back("activeCycles");
	keys.push_back("capacityStalls");
	keys.push_back("issueWindow");
	keys.push_back("hazardStalls");
	keys.push_back("noValidButReady");
	keys.push_back("noReadyButValid");
	return keys;
}

void HardwareSpMVBufferNone::setThresholdRegisters() {
	// setup thresholds
	m_acc->thresColPtr(m_thres_colPtr);
	m_acc->thresRowInd(m_thres_rowInd);
	m_acc->thresNZData(m_thres_nzData);
	m_acc->thresInputVec(m_thres_inpVec);
}
