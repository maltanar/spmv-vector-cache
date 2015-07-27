#include <stdlib.h>
#include <iostream>
#include <string.h>
#include "xil_cache.h"
#include "SparseMatrix.h"
#include "SoftwareSpMV.h"
#include "malloc_aligned.h"

using namespace std;

unsigned int matrixMetaBase = 0x08000100;
unsigned int accBase = 0x40000000;
unsigned int resBase = 0x43c00000;

// use compile-time defines to decide which type of HW SpMV to use
#if defined(HWSPMV_BUFFERALL)
	#include "HardwareSpMV.h"
	#define HWSPMV	HardwareSPMV
	static const char * hwSpMVIDString = "BufferAll";
#elif defined(HWSPMV_BUFFERNONE)
	#include "HardwareSpMVBufferNone.h"
	#define HWSPMV HardwareSpMVBufferNone
	static const char * hwSpMVIDString = "BufferNone";
#elif defined(HWSPMV_BUFFERSEL)
	#include "HardwareSpMVBufferSel.h"
	#define HWSPMV HardwareSpMVBufferSel
	static const char * hwSpMVIDString = "BufferSel";
#endif

int main(int argc, char *argv[]) {
  Xil_DCacheDisable();

  cout << "SpMVAccel-" << hwSpMVIDString << endl;
  cout << "=====================================" << endl;

  SparseMatrix * A = SparseMatrix::fromMemory(matrixMetaBase);

  A->printSummary();

  SpMVData *x = (SpMVData *) malloc_aligned(64, sizeof(SpMVData)*A->getCols());
  SpMVData *y = (SpMVData *) malloc_aligned(64, sizeof(SpMVData)*A->getRows());

  for(SpMVIndex i = 0; i < A->getCols(); i++) { x[i] = (SpMVData) 1; }
  for(SpMVIndex i = 0; i < A->getRows(); i++) { y[i] = (SpMVData) 0; }

  SpMV * spmv = new HWSPMV(accBase, resBase, A, x, y);
  SoftwareSpMV check(A, x);

  spmv->exec();
  check.exec();

  SpMVData * goldenY = check.getY();
  int res = memcmp(goldenY, y, sizeof(SpMVData)*A->getRows());

  cout << "Memcmp result = " << res << endl;

  if(res != 0) {
	  unsigned int diffs = 0;
	  for(SpMVIndex i = 0; i < A->getRows(); i++) {
		  if(y[i] != goldenY[i]) diffs++;
	  }
	  cout << "A total of " << diffs << " result elements are different." << endl;
  }

  cout << "Exiting..." << endl;

  return 0;
}
