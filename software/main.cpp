#include <iostream>
#include "xil_cache.h"
#include "SparseMatrix.h"
#include "SoftwareSpMV.h"
#include "SpMVAcceleratorDriver.hpp"

using namespace std;

unsigned int matrixMetaBase = 0x08000100;
unsigned int accelBase = 0x40000000;

void resetAccelerator() {
	volatile unsigned int * accelReset = (volatile unsigned int * ) 0x43c00000;
	*accelReset = 1;
	*accelReset = 0;
}

int main(int argc, char *argv[]) {
  Xil_DCacheDisable();

  cout << "SpMVAccel-BufferAll" << endl;
  cout << "=====================================" << endl;

  SparseMatrix * A = SparseMatrix::fromMemory(matrixMetaBase);

  A->printSummary();

  return 0;
}
