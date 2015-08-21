#include "HardwareSpMV.h"
#include "xil_cache.h"
#include <assert.h>
#include <string.h>

#define isAligned(x) (assert((unsigned int)x % 64 == 0))

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
	m_diffFromGolden = 1;

	m_thres_colPtr = 128;
	m_thres_rowInd = 128;
	m_thres_nzData = 128;
	m_thres_inpVec = 128;
}

HardwareSpMV::~HardwareSpMV() {

}

void HardwareSpMV::resetAccelerator() {
	*m_resetBase = 1;
	*m_resetBase = 0;
	m_diffFromGolden = 1;
}

void HardwareSpMV::compareGolden(SpMVData* golden) {
	m_diffFromGolden = memcmp(golden, m_y, m_A->getRows() * sizeof(SpMVData));
}

unsigned int HardwareSpMV::statInt(std::string name) {
	if (name == "diffFromGolden")
		return m_diffFromGolden;
	else if (name == "rows")
		return m_A->getRows();
	else if (name == "cols")
		return m_A->getCols();
	else if (name == "nz")
		return m_A->getNz();
	else
		return 0;
}

std::vector<std::string> HardwareSpMV::statKeys() {
	std::vector<std::string> keys;
	keys.push_back("diffFromGolden");
	keys.push_back("rows");
	keys.push_back("cols");
	keys.push_back("nz");
	return keys;
}

void HardwareSpMV::init() {
}

void HardwareSpMV::write() {
	// make sure output vector is invalidated in cache prior to reads by CPU
	//Xil_DCacheInvalidateRange((unsigned int)m_y, sizeof(SpMVData)*(m_A->getRows()));
}

void HardwareSpMV::regular() {
	// make sure all SpMV data is flushed to DRAM
	/*
	 Xil_DCacheFlushRange((unsigned int)m_A->getIndPtrs(), sizeof(SpMVIndex)*(m_A->getCols()+1));
	 Xil_DCacheFlushRange((unsigned int)m_A->getInds(), sizeof(SpMVIndex)*(m_A->getNz()));
	 Xil_DCacheFlushRange((unsigned int)m_A->getNzData(), sizeof(SpMVData)*(m_A->getNz()));
	 Xil_DCacheFlushRange((unsigned int)m_x, sizeof(SpMVData)*(m_A->getCols()));
	 Xil_DCacheFlushRange((unsigned int)m_y, sizeof(SpMVData)*(m_A->getRows()));
	 */
}

void HardwareSpMV::setThresholds(unsigned int colPtr, unsigned int rowInd,
		unsigned int nzData, unsigned int inpVec) {
	m_thres_colPtr = colPtr;
	m_thres_rowInd = rowInd;
	m_thres_nzData = nzData;
	m_thres_inpVec = inpVec;
}

void HardwareSpMV::setupRegs() {
	setThresholdRegisters();
}
