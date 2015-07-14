#include <malloc.h>
#include <assert.h>
#include <iostream>
#include <string.h>
#include "TestSpMVBackendDriver.hpp"

using namespace std;

#define isAligned(x) (assert((unsigned int)x % 64 == 0))

// modified version of new, to return burst-aligned pointers
void* operator new(size_t size) {
	//cout << "new: size: " << size << endl;
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

unsigned int sumTo(unsigned int i) {
	return (i*(i+1))/2;
}

void TestSpMVBackend() {
	Xil_DCacheDisable();

	volatile unsigned int * base = (volatile unsigned int *) 0x40000000;
	TestSpMVBackendDriver drv(base);

	unsigned int numRows = 64;
	unsigned int numCols = 64;
	unsigned int numNZ = 1024;

	// allocate pointers & make sure they are aligned
	SpMVIndex * colPtr = new SpMVIndex [numCols+1];
	SpMVIndex * rowInd = new SpMVIndex [numNZ];
	SpMVData * nzData = new SpMVData [numNZ];
	SpMVData * inpVec = new SpMVData [numCols];
	SpMVData * outVec = new SpMVData [numRows];
	isAligned(colPtr); isAligned(rowInd); isAligned(nzData); isAligned(inpVec); isAligned(outVec);
	// set value parameters for testbench
	drv.in_numCols(numCols);
	drv.in_numRows(numRows);
	drv.in_numNZ(numNZ);
	drv.in_baseColPtr((unsigned int) colPtr);
	drv.in_baseRowInd((unsigned int) rowInd);
	drv.in_baseNZData((unsigned int) nzData);
	drv.in_baseInputVec((unsigned int) inpVec);
	drv.in_baseOutputVec((unsigned int) outVec);
	// prepare test data
	memset(outVec, 0, sizeof(SpMVData)*numRows);
	for(unsigned int i = 0; i < numCols+1; i++) colPtr[i] = i+1;
	for(unsigned int i = 0; i < numNZ; i++) rowInd[i] = i+1;
	for(unsigned int i = 0; i < numNZ; i++) nzData[i] = i+1;
	for(unsigned int i = 0; i < numCols; i++) inpVec[i] = i+1;

	int errors = 0;

	// write test
	drv.in_startWrite(1);
	while(drv.out_doneWrite() == 0);

	errors = 0;
	for(unsigned int i = 0; i < numRows; i++) {
		if(outVec[i] != i) {
			cout << "outVec mismatch, " << i << " = " << outVec[i] << endl;
			errors++;
		}
	}
	cout << "outVec errors: " << errors << endl;
	drv.in_startWrite(0);
	assert(drv.out_doneWrite() == 0);

	// regular test
	drv.in_startRegular(1);
	while(drv.out_allMonsFinished() == 0);
	cout << "colPtr: " << drv.out_redColPtr() << " expected " << sumTo(numCols+1) << endl;
	cout << "rowInd: " << drv.out_redRowInd() << " expected " << sumTo(numNZ) << endl;
	cout << "nzData: " << drv.out_redNZData0() << " expected " << sumTo(numNZ) << endl;
	cout << "inpVec: " << drv.out_redInputVec0()<< " expected " << sumTo(numCols) << endl;

	drv.in_startRegular(0);
	assert(drv.out_doneRegular() == 0);

	cout << "Active cycles: " << drv.out_rdMon_activeCycles() << endl;
	cout << "Total cycles: " << drv.out_rdMon_totalCycles() << endl;

	float dataVol = (numCols + numNZ) *(sizeof(SpMVData)+sizeof(SpMVIndex));
	float bw = dataVol/(float)drv.out_rdMon_totalCycles();
	cout << "BW: " << bw << " bytes/cycle" << endl;

	Xil_DCacheEnable();
}
