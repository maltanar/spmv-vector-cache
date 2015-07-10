#include "SparseMatrix.h"
#include <iostream>

using namespace std;

SparseMatrix::SparseMatrix() {
	// TODO Auto-generated constructor stub

}

SparseMatrix::~SparseMatrix() {
	// TODO Auto-generated destructor stub
}

void SparseMatrix::printSummary() {
	cout << "Matrix summary" << endl;
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

	return ret;
}
