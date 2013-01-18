#!/bin/bash

PERF_CMD="perf_3.7.0-7 stat"

$PERF_CMD taskset -c 5,6 java -cp templib/junit-4.5.jar:build/classes/main:build/classes/perf:build/classes/test com.lmax.disruptor.OnePublisherToOneProcessorUniCastOffHeapThroughputTest
$PERF_CMD taskset -c 5,6 java -cp templib/junit-4.5.jar:build/classes/main:build/classes/perf:build/classes/test com.lmax.disruptor.OnePublisherToOneProcessorUniCastThroughputTest
