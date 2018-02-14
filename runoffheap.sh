#!/bin/bash

rm *-gc.log

ARGS="$JAVA_HOME/bin/java -mx1g"
GCARGS="-verbose:gc -XX:+PrintGCDateStamps -XX:+PrintGCApplicationStoppedTime -XX:+UseParNewGC -XX:+UseConcMarkSweepGC -XX:MaxDirectMemorySize=1g"
CPATH="-cp build/classes/main:build/classes/perf:templib/*.jar"
BIN="perf stat -e L1-dcache-loads -e L1-dcache-load-misses -e LLC-loads -e LLC-load-misses -e dTLB-loads -e dTLB-load-misses"

echo "Running OffHeap..."
$BIN $ARGS $GCARGS -Xloggc:simple-gc.log $CPATH com.lmax.disruptor.offheap.OneToOneOffHeapThroughputTest
echo "Done"

echo "Running OnHeap..."
$BIN $ARGS $GCARGS -Xloggc:custom-gc.log $CPATH com.lmax.disruptor.offheap.OneToOneOnHeapThroughputTest
echo "Done"

echo "Running Sliced OnHeap..."
$BIN $ARGS $GCARGS -Xloggc:custom-gc.log $CPATH -Dsliced=true com.lmax.disruptor.offheap.OneToOneOnHeapThroughputTest
echo "Done"
     
