#include "SoftwareSpMV.h"
#include <iostream>
#include "timer.h"

using namespace std;

SoftwareSpMV::SoftwareSpMV(SparseMatrix * A, SpMVData *x, SpMVData *y) :
		SpMV(A, x, y) {
	m_allocX = false;
	m_allocY = false;
	m_execTime = 0;

	if (A == 0) {
		cerr << "Invalid matrix for SoftwareSpMV!" << endl;
		return;
	}

	if (x == 0) {
		m_allocX = true;
		// allocate new all-ones input vector
		m_x = new SpMVData[A->getCols()];
		for (SpMVIndex i = 0; i < A->getCols(); i++) {
			m_x[i] = (SpMVData) 1;
		}
	}

	if (y == 0) {
		m_allocY = true;
		// allocate new all-zeroes result vector
		m_y = new SpMVData[A->getRows()];
		for (SpMVIndex i = 0; i < A->getRows(); i++) {
			m_y[i] = (SpMVData) 0;
		}
	}

}

SoftwareSpMV::~SoftwareSpMV() {
	if(m_allocX) delete [] m_x;
	if(m_allocY) delete [] m_y;
}

bool SoftwareSpMV::exec() {
	SpMVIndex col, colCount = m_A->getCols();
	SpMVIndex elemInd;
	SpMVIndex * colPtr = m_A->getIndPtrs();
	SpMVIndex * rowInd = m_A->getInds();
	SpMVData * nzData = m_A->getNzData();

	TimerStart();

	for (col = 0; col < colCount; col++) {
		SpMVData inpVecElem = m_x[col];
		for (elemInd = colPtr[col]; elemInd < colPtr[col + 1]; elemInd++) {
			m_y[rowInd[elemInd]] += nzData[elemInd] * inpVecElem;
		}
	}

	TimerStop();
	m_execTime = TimerRead();

	return true;
}
std::vector<std::string> SoftwareSpMV::statKeys() {
	vector<string> keys;
	keys.push_back("rows");
	keys.push_back("cols");
	keys.push_back("nz");
	keys.push_back("time");
	return keys;
}


unsigned int SoftwareSpMV::statInt(std::string name) {
	if (name == "rows")
		return m_A->getRows();
	else if (name == "cols")
		return m_A->getCols();
	else if (name == "nz")
		return m_A->getNz();
	else if (name == "time")
		return m_execTime;
	else
		return 0;
}
