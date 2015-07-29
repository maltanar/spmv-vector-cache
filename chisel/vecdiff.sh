#!/bin/sh

GOLDEN="../matrices/$1/golden.bin"
TEST="out-$1.bin"

perl -e 'open F,shift; do { read(F,$a,8); print scalar reverse($a);} while(!eof(F));' $GOLDEN > golden-rev.bin
perl -e 'open F,shift; do { read(F,$a,8); print scalar reverse($a);} while(!eof(F));' $TEST > test-rev.bin


xxd -g8 -c8 -ps golden-rev.bin > A.txt
xxd -g8 -c8 -ps test-rev.bin > B.txt

diff A.txt B.txt
