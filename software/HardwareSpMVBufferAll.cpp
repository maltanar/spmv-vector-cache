#include "HardwareSpMVBufferAll.h"
#include <assert.h>
#include <iostream>
using namespace std;

HardwareSpMVBufferAll::HardwareSpMVBufferAll(unsigned int aBase,
		unsigned int aReset, SparseMatrix * A, SpMVData *x, SpMVData *y) :
		HardwareSpMV(aBase, aReset, A, x, y) {
	m_acc = new SpMVAcceleratorBufferAllDriver(m_accelBase);

	// buffer-all: ensure all rows will fit within OCM
	//cout << "# OCM words: " << m_acc->ocmWords() << endl;
	assert(A->getRows() <= m_acc->ocmWords());
}

HardwareSpMVBufferAll::~HardwareSpMVBufferAll() {
	delete m_acc;
}

void HardwareSpMVBufferAll::setupRegs() {
	HardwareSpMV::setupRegs();

	m_acc->numCols(m_A->getCols());
	m_acc->numNZ(m_A->getNz());
	m_acc->numRows(m_A->getRows());

	m_acc->baseColPtr((unsigned int) m_A->getIndPtrs());
	m_acc->baseRowInd((unsigned int) m_A->getInds());
	m_acc->baseNZData((unsigned int) m_A->getNzData());

	m_acc->baseInputVec((unsigned int) m_x);
	m_acc->baseOutputVec((unsigned int) m_y);

	m_totalCycles = 0;
	m_activeCycles = 0;
	m_hazardStalls = 0;
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

	updateStatistics();

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

unsigned int HardwareSpMVBufferAll::statInt(std::string name) {
	if (name == "totalCycles")
		return m_totalCycles;
	else if (name == "activeCycles")
		return m_activeCycles;
	else if (name == "ocmDepth")
		return m_acc->ocmWords();
	else if (name == "hazardStalls")
		return m_hazardStalls;
	else
		return HardwareSpMV::statInt(name);
}

std::vector<std::string> HardwareSpMVBufferAll::statKeys() {
	vector<string> keys = HardwareSpMV::statKeys();
	keys.push_back("totalCycles");
	keys.push_back("activeCycles");
	keys.push_back("ocmDepth");
	keys.push_back("hazardStalls");
	return keys;
}

void HardwareSpMVBufferAll::updateStatistics() {
	m_totalCycles = m_acc->bwMon_totalCycles();
	m_activeCycles = m_acc->bwMon_activeCycles();
	m_hazardStalls = m_acc->hazardStalls();
}

void HardwareSpMVBufferAll::setThresholdRegisters() {
	// setup thresholds
	m_acc->thresColPtr(m_thres_colPtr);
	m_acc->thresRowInd(m_thres_rowInd);
	m_acc->thresNZData(m_thres_nzData);
	m_acc->thresInputVec(m_thres_inpVec);
}
