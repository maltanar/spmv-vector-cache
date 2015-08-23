#include "timer.h"

#define TIMER_DEVICE_ID		XPAR_XSCUTIMER_0_DEVICE_ID

XScuTimer timer;

void TimerSetup() {
	XScuTimer_Config * timerConfig = XScuTimer_LookupConfig(TIMER_DEVICE_ID);
	XScuTimer_CfgInitialize(&timer, timerConfig, timerConfig->BaseAddr);
	XScuTimer_DisableAutoReload(&timer);
	XScuTimer_DisableInterrupt(&timer);
	//XScuTimer_SetPrescaler(&timer, 1);
}

void TimerStart() {
	XScuTimer_LoadTimer(&timer, 0xFFFFFFFF);
	XScuTimer_Start(&timer);
}

void TimerStop() {
	XScuTimer_Stop(&timer);
}

unsigned int TimerRead() {
	unsigned int ctr = XScuTimer_GetCounterValue(&timer);
	return 0xFFFFFFFF - ctr;
}

float TimerReadSecs() {
	return (float)TimerRead() * (1.0f / 333333333.0f);
}
