#include <stdlib.h>
#include <iostream>
#include <string.h>
#include "xil_cache.h"
#include "SparseMatrix.h"
#include "SoftwareSpMV.h"
#include "HardwareSpMV.h"
#include "SpMVAcceleratorDriver.hpp"

using namespace std;

unsigned int matrixMetaBase = 0x08000100;
unsigned int accBase = 0x40000000;
unsigned int resBase = 0x43c00000;

// don't call free() on ptrs returned from this, it might crash
void* allocAligned(size_t size) {
	if (size % 64 != 0)
		size = size - (size % 64) + 64;

	void *p = malloc(size);
	if (p == 0) {
		cout << "OUCHIES: " << size << endl;
		throw std::bad_alloc(); // ANSI/ISO compliant behavior
	}

	unsigned int t = (unsigned int) p;
	if(t % 64 != 0) {
		t = t - (t % 64) + 64;
		p = (void *) t;
	}

	return p;
}

int main(int argc, char *argv[]) {
  Xil_DCacheDisable();

  cout << "SpMVAccel-BufferAll" << endl;
  cout << "=====================================" << endl;

  SparseMatrix * A = SparseMatrix::fromMemory(matrixMetaBase);

  A->printSummary();

  SpMVData *x = (SpMVData *) allocAligned(sizeof(SpMVData)*A->getCols());
  SpMVData *y = (SpMVData *) allocAligned(sizeof(SpMVData)*A->getRows());

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
