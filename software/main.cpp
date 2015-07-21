#include <stdlib.h>
#include <iostream>
#include <string.h>
#include "xil_cache.h"
#include "SparseMatrix.h"
#include "SoftwareSpMV.h"
#include "HardwareSpMV.h"
#include "SpMVAcceleratorDriver.hpp"
#include "malloc_aligned.h"

using namespace std;

unsigned int matrixMetaBase = 0x08000100;
unsigned int accBase = 0x40000000;
unsigned int resBase = 0x43c00000;
/*
void* operator new(size_t size) {
	cout << "new: " << size << endl;
	void *p = malloc(size);
	if (p == 0) {
		cout << "OUCHIES: " << size << endl;
		throw std::bad_alloc(); // ANSI/ISO compliant behavior
	}
	return p;
}
*/
int main(int argc, char *argv[]) {
  Xil_DCacheDisable();

  cout << "SpMVAccel-BufferAll" << endl;
  cout << "=====================================" << endl;

  SparseMatrix * A = SparseMatrix::fromMemory(matrixMetaBase);

  A->printSummary();

  SpMVData *x = (SpMVData *) malloc_aligned(64, sizeof(SpMVData)*A->getCols());
  SpMVData *y = (SpMVData *) malloc_aligned(64, sizeof(SpMVData)*A->getRows());

  for(SpMVIndex i = 0; i < A->getCols(); i++) { x[i] = (SpMVData) 1; }
  for(SpMVIndex i = 0; i < A->getRows(); i++) { y[i] = (SpMVData) 0; }

  HardwareSpMV spmv(accBase, resBase, A, x, y);
  SoftwareSpMV check(A);

  spmv.exec();
  check.exec();

  SpMVData * goldenY = check.getY();
  int res = memcmp(goldenY, y, sizeof(SpMVData)*A->getRows());

  cout << "Memcmp result = " << res << endl;

  cout << "Exiting..." << endl;

  return 0;
}
