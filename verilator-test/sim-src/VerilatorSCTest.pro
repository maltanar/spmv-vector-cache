#-------------------------------------------------
#
# Project created by QtCreator 2014-10-12T22:11:37
#
#-------------------------------------------------

QT       += core

QT       -= gui

TARGET = VerilatorSCTest
CONFIG   += console
CONFIG   -= app_bundle

TEMPLATE = app

SYSTEMC_ROOT = /home/maltanar/systemc
SYSTEMC_ARCH = linux64
VERILATOR_ROOT = /usr/share/verilator


QMAKE_INCDIR += $$SYSTEMC_ROOT/include $$VERILATOR_ROOT/include $$VERILATOR_ROOT/include/vltstd
QMAKE_LIBDIR += $$SYSTEMC_ROOT/lib-$$SYSTEMC_ARCH

DEFINES += VL_PRINTF=printf VM_TRACE=0 VM_COVERAGE=0

LIBS += -lsystemc -lpthread

SOURCES += main.cpp \
    VChiselModule.cpp \
    VChiselModule__Syms.cpp \
    verilated.cpp

HEADERS += \
    fifoinbreakout.h \
    VChiselModule.h \
    VChiselModule__Syms.h \
    fifooutbreakout.h
