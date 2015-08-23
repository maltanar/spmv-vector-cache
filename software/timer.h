#include <xscutimer.h>
#include "xparameters.h"

#define TIMER_DEVICE_ID		XPAR_XSCUTIMER_0_DEVICE_ID

void TimerSetup();
void TimerStart();
void TimerStop();
unsigned int TimerRead();
float TimerReadSecs();
