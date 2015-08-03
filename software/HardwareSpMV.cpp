#include "HardwareSpMV.h"

HardwareSpMV::HardwareSpMV(unsigned int aBase, unsigned int aReset, SparseMatrix * A, SpMVData *x, SpMVData *y)
: SpMV(A, x, y){

	m_accelBase = (volatile unsigned int *) aBase;
	m_resetBase = (volatile unsigned int *) aReset;

}

HardwareSpMV::~HardwareSpMV() {

}

