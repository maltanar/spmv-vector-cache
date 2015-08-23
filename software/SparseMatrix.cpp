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
	CompressedSparseMetadata * metadata = (CompressedSparseMetadata *) metaDataBase;

	if(metadata->numCols == 0 || metadata->numRows == 0 || metadata->numNZ == 0) {
		cerr << "Error: matrix data at " << hex << metaDataBase << dec << " is invalid" << endl;
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

void SparseMatrix::markRowStarts() {
	if(!m_rowStartsMarked) {
		m_rowStartsMarked = true;
		// flags to mark encountered row indices
		// packed as 32-bit fields for smaller memory/cache footprint
		unsigned int encSize = (m_rows/32)+1;
		unsigned int * encounteredRow = new unsigned int[encSize];

		// set all encountered flags to zero
		memset(encounteredRow, 0, sizeof(unsigned int)*encSize);

		for(SpMVIndex nz = 0; nz < m_nz; nz++) {
			SpMVIndex rowInd = m_inds[nz];
			unsigned int bitInd = rowInd / 32;
			unsigned int bitMod = rowInd % 32;
			unsigned int bVal = encounteredRow[bitInd];
			if((bVal & (1 << bitMod)) == 0) {
				// set as encountered + add cold miss bit at highest position
				encounteredRow[bitInd] = bVal | (1 << bitMod);
				m_inds[nz] = rowInd | (1 << 31);
			}
		}

		delete [] encounteredRow;
	}
}
