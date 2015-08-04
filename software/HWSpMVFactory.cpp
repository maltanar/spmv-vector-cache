#include <iostream>
#include <assert.h>
#include "HWSpMVFactory.h"
#include "HardwareSpMVBufferAll.h"
#include "HardwareSpMVBufferNone.h"
#include "HardwareSpMVBufferSel.h"
#include "HardwareSpMVNewCache.h"

using namespace std;

HWSpMVFactory::HWSpMVFactory() {
	// TODO Auto-generated constructor stub

}

HWSpMVFactory::~HWSpMVFactory() {
	// TODO Auto-generated destructor stub
}

HardwareSpMV* HWSpMVFactory::make(unsigned int aBase, unsigned int aReset,
		SparseMatrix * A, SpMVData *x, SpMVData *y) {
	unsigned int sign = *(volatile unsigned int *) aBase;

	if (sign == SpMVAcceleratorBufferNoneDriver::expSignature()) {
		return new HardwareSpMVBufferNone(aBase, aReset, A, x, y);
	} else if (sign == SpMVAcceleratorBufferSelDriver::expSignature()) {
		return new HardwareSpMVBufferSel(aBase, aReset, A, x, y);
	} else if (sign == SpMVAcceleratorNewCacheDriver::expSignature()) {
		return new HardwareSpMVNewCache(aBase, aReset, A, x, y);
	} else if (sign == SpMVAcceleratorBufferAllDriver::expSignature()) {
		return new HardwareSpMVBufferAll(aBase, aReset, A, x, y);
	} else {
		cout << "Accelerator signature unrecognized! " << hex << sign << dec
				<< endl;
		assert(0);
		return 0;
	}
}

std::string HWSpMVFactory::name(unsigned int aBase) {
	unsigned int sign = *(volatile unsigned int *) aBase;

	if (sign == SpMVAcceleratorBufferNoneDriver::expSignature()) {
		return "BufferNone";
	} else if (sign == SpMVAcceleratorBufferSelDriver::expSignature()) {
		return "BufferSel";
	} else if (sign == SpMVAcceleratorNewCacheDriver::expSignature()) {
		return "NewCache";
	} else if (sign == SpMVAcceleratorBufferAllDriver::expSignature()) {
		return "BufferAll";
	} else {
		cout << "Accelerator signature unrecognized! " << hex << sign << dec
				<< endl;
		assert(0);
		return "<undefined>";
	}
}
