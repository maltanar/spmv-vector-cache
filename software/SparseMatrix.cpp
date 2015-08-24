#include "SparseMatrix.h"
#include <string.h>
#include <iostream>
#include <vector>

using namespace std;

SparseMatrix::SparseMatrix() {
	// TODO Auto-generated constructor stub
	m_rowStartsMarked = false;
}

SparseMatrix::~SparseMatrix() {
	// TODO Auto-generated destructor stub
}

void SparseMatrix::printSummary() {
	cout << "Matrix summary" << endl;
	cout << "name = " << m_name << endl;
	cout << "#rows = " << m_rows << endl;
	cout << "#cols = " << m_cols << endl;
	cout << "#nz = " << m_nz << endl;
}

bool SparseMatrix::isSquare() {
	return m_cols == m_rows;
}

SparseMatrix* SparseMatrix::fromMemory(unsigned int metaDataBase) {
	CompressedSparseMetadata * metadata =
			(CompressedSparseMetadata *) metaDataBase;

	if (metadata->numCols == 0 || metadata->numRows == 0
			|| metadata->numNZ == 0) {
		cerr << "Error: matrix data at " << hex << metaDataBase << dec
				<< " is invalid" << endl;
		return 0;
	}

	SparseMatrix * ret = new SparseMatrix();
	ret->m_cols = metadata->numCols;
	ret->m_rows = metadata->numRows;
	ret->m_nz = metadata->numNZ;
	ret->m_indPtrs = (SpMVIndex*) (metadata->indPtrBase);
	ret->m_inds = (SpMVIndex*) (metadata->indBase);
	ret->m_nzData = (SpMVData*) (metadata->nzDataBase);
	ret->m_rowStartsMarked = false;

	return ret;
}

void SparseMatrix::markRowStarts(const bool reverse, const int shift) {
	// flags to mark encountered row indices
	// packed as 32-bit fields for smaller memory/cache footprint
	unsigned int encSize = (m_rows / 32) + 1;
	unsigned int * encounteredRow = new unsigned int[encSize];

	// set all encountered flags to zero
	memset(encounteredRow, 0, sizeof(unsigned int) * encSize);

	if (reverse) {
		for (SpMVIndex nz = 0; nz < m_nz; nz++) {
			SpMVIndex nzInd = m_nz - 1 - nz;
			SpMVIndex rowInd = m_inds[nzInd] & (0x3FFFFFFF);
			unsigned int bitInd = rowInd / 32;
			unsigned int bitMod = rowInd % 32;
			unsigned int bVal = encounteredRow[bitInd];
			if ((bVal & (1 << bitMod)) == 0) {
				// set as encountered + add cold miss bit at highest position
				encounteredRow[bitInd] = bVal | (1 << bitMod);
				m_inds[nzInd] = m_inds[nzInd] | (1 << shift);
			}
		}
	} else {
		for (SpMVIndex nz = 0; nz < m_nz; nz++) {
			SpMVIndex nzInd = nz;
			SpMVIndex rowInd = m_inds[nzInd] & (0x3FFFFFFF);
			unsigned int bitInd = rowInd / 32;
			unsigned int bitMod = rowInd % 32;
			unsigned int bVal = encounteredRow[bitInd];
			if ((bVal & (1 << bitMod)) == 0) {
				// set as encountered + add cold miss bit at highest position
				encounteredRow[bitInd] = bVal | (1 << bitMod);
				m_inds[nzInd] = m_inds[nzInd] | (1 << shift);
			}
		}
	}

	delete[] encounteredRow;
}

unsigned int SparseMatrix::maxAlive() {
	// mark row starts and ends
	markRowStarts(false, 31);	// bit 31 indicates row start
	markRowStarts(true, 30);	// bit 30 indicates row end
	// iterate over all indices to compute aliveness
	// signed integer since we seem to be getting negatives along the way (?)
	unsigned int maxAlive = 0, currentAlive = 0;

	for (SpMVIndex nzInd = 0; nzInd < m_nz; nzInd++) {
		if(m_inds[nzInd] & (1 << 31)) currentAlive += 1;
		if(m_inds[nzInd] & (1 << 30)) currentAlive -= 1;

		maxAlive = currentAlive > maxAlive ? currentAlive : maxAlive;
	}

	return (unsigned int) maxAlive;
}

unsigned int SparseMatrix::maxColSpan() {
	unsigned int maxColSpan = 0, currentColSpan = 0;
	for (SpMVIndex colInd = 0; colInd < m_cols; colInd++) {
		currentColSpan = m_inds[m_indPtrs[colInd + 1] - 1]
				- m_inds[m_indPtrs[colInd]];
		maxColSpan = currentColSpan > maxColSpan ? currentColSpan : maxColSpan;
	}

	return maxColSpan;
}

void SparseMatrix::clearRowMarkings(const unsigned int mask) {
	for (SpMVIndex nzInd = 0; nzInd < m_nz; nzInd++) {
		m_inds[nzInd] = m_inds[nzInd] & mask;
	}
}
