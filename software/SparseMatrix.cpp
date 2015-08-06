#include "SparseMatrix.h"
#include <string.h>
#include <iostream>

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
		char * encounteredRow = new char[m_rows];
		// set all encountered flags to zero
		memset(encounteredRow, 0, m_rows);

		for(SpMVIndex nz = 0; nz < m_nz; nz++) {
			SpMVIndex rowInd = m_inds[nz];
			if(!encounteredRow[rowInd]) {
				// set as encountered + add cold miss bit at highest position
				encounteredRow[rowInd] = 1;
				m_inds[nz] = rowInd | (1 << 31);
			}
		}

		delete [] encounteredRow;
	}
}
