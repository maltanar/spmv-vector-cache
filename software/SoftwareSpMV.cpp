#include "SoftwareSpMV.h"
#include <iostream>
#include "timer.h"

using namespace std;

SoftwareSpMV::SoftwareSpMV(SparseMatrix * A, SpMVData *x, SpMVData *y) :
		SpMV(A, x, y) {
	m_allocX = false;
	m_allocY = false;
	m_execTime = 0;
	m_cmsTime = 0;
	m_maxAliveTime = 0;
	m_maxColSpanTime = 0;
	m_maxAlive = 0;
	m_maxColSpan = 0;

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
	if (m_allocX)
		delete[] m_x;
	if (m_allocY)
		delete[] m_y;
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

void SoftwareSpMV::measurePreprocessingTimes() {
	// measure time for maxAlive and maxColSpan

	TimerStart();
	m_maxColSpan = m_A->maxColSpan();
	TimerStop();
	m_maxColSpanTime = TimerRead();

	TimerStart();
	m_maxAlive = m_A->maxAlive();
	TimerStop();
	m_maxAliveTime = TimerRead();

	m_A->clearRowMarkings(~((1 << 31) | (1 << 30)));

	TimerStart();
	m_A->markRowStarts();
	TimerStop();
	m_cmsTime = TimerRead();

	// clear row start info to prevent corruption in software SpMV
	m_A->clearRowMarkings(~((1 << 31) | (1 << 30)));
}

std::vector<std::string> SoftwareSpMV::statKeys() {
	vector<string> keys;
	keys.push_back("rows");
	keys.push_back("cols");
	keys.push_back("nz");
	keys.push_back("spmvtime");
	keys.push_back("cmstime");
	keys.push_back("maxAliveTime");
	keys.push_back("maxColSpanTime");
	keys.push_back("maxAlive");
	keys.push_back("maxColSpan");
	return keys;
}

unsigned int SoftwareSpMV::statInt(std::string name) {
	if (name == "rows")
		return m_A->getRows();
	else if (name == "cols")
		return m_A->getCols();
	else if (name == "nz")
		return m_A->getNz();
	else if (name == "spmvtime")
		return m_execTime;
	else if (name == "cmstime")
		return m_cmsTime;
	else if (name == "maxAliveTime")
		return m_maxAliveTime;
	else if (name == "maxColSpanTime")
		return m_maxColSpanTime;
	else if (name == "maxAlive")
		return m_maxAlive;
	else if (name == "maxColSpan")
		return m_maxColSpan;
	else
		return 0;
}
