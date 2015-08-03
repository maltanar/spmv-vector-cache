#include "HardwareSpMV.h"
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
}

HardwareSpMV::~HardwareSpMV() {

}

void HardwareSpMV::resetAccelerator() {
	*m_resetBase = 1;
	*m_resetBase = 0;
	m_diffFromGolden = 1;
}

void HardwareSpMV::compareGolden(SpMVData* golden) {
	m_diffFromGolden = memcmp(golden, m_y, m_A->getRows()*sizeof(SpMVData));
}

unsigned int HardwareSpMV::statInt(std::string name) {
	if(name == "diffFromGolden") return m_diffFromGolden;
	else if(name == "rows") return m_A->getRows();
	else if(name == "cols") return m_A->getCols();
	else if(name == "nz") return m_A->getNz();
	else return 0;
}
