#include "SpMV.h"

SpMV::SpMV(SparseMatrix * A, SpMVData *x, SpMVData *y) {
	m_A=A;
	m_x=x;
	m_y=y;

}

SpMV::~SpMV() {
	// TODO Auto-generated destructor stub
}

