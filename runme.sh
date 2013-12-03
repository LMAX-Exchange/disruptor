#!/bin/bash

rm *-gc.log

echo "Running Simple..."
time $JAVA_HOME/bin/java -mx128m -Xloggc:simple-gc.log \
     -verbose:gc \
     -XX:+PrintGCDateStamps \
     -XX:+PrintGCApplicationStoppedTime \
     -cp "build/classes/main:build/classes/perf:templib/*.jar" \
     com.lmax.disruptor.immutable.SimplePerformanceTest
echo "Done"

grep 'stopped:' simple-gc.log | sed 's/.*stopped: \([0-9.]*\) seconds/\1/' | sort -n | awk '{ printf "%1.3f\n", $1 }' | (echo " Count Millis" ; uniq -c )
     
echo "Running Custom..."
time $JAVA_HOME/bin/java -mx128m -Xloggc:custom-gc.log \
     -verbose:gc \
     -XX:+PrintGCDateStamps \
     -XX:+PrintGCApplicationStoppedTime \
     -cp "build/classes/main:build/classes/perf:templib/*.jar" \
     com.lmax.disruptor.immutable.CustomPerformanceTest
echo "Done"

grep 'stopped:' custom-gc.log | sed 's/.*stopped: \([0-9.]*\) seconds/\1/' | sort -n | awk '{ printf "%1.3f\n", $1 }' | (echo " Count Millis" ; uniq -c )
     
