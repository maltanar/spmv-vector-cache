#!/bin/sh

GOLDEN="../matrices/$1/golden.bin"
TEST="out-$1.bin"

xxd -g8 -c8 -ps $GOLDEN > A.txt
xxd -g8 -c8 -ps $TEST > B.txt

diff A.txt B.txt
