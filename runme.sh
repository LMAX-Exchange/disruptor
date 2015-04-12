#!/bin/bash

rm *-gc.log

ARGS="$JAVA_HOME/bin/java -mx128m"
GCARGS="-verbose:gc -XX:+PrintGCDateStamps -XX:+PrintGCApplicationStoppedTime -XX:+UseParNewGC -XX:+UseConcMarkSweepGC"
CPATH="-cp build/classes/main:build/classes/perf:templib/*.jar"

echo "Running Simple..."
time $ARGS $GCARGS -Xloggc:simple-gc.log $CPATH com.lmax.disruptor.immutable.SimplePerformanceTest
echo "Done"

grep 'stopped:' simple-gc.log | sed 's/.*stopped: \([0-9.]*\) seconds/\1/' | sort -n | awk '{ printf "%1.3f\n", $1 }' | (echo " Count Millis" ; uniq -c )
     
echo "Running Custom..."
time $ARGS $GCARGS -Xloggc:custom-gc.log $CPATH com.lmax.disruptor.immutable.CustomPerformanceTest
echo "Done"

grep 'stopped:' custom-gc.log | sed 's/.*stopped: \([0-9.]*\) seconds/\1/' | sort -n | awk '{ printf "%1.3f\n", $1 }' | (echo " Count Millis" ; uniq -c )
     
