#!/bin/bash

JCMD="java -XX:+UnlockDiagnosticVMOptions -XX:+PrintAssembly -cp templib/junit-4.5.jar:build/classes/main:build/classes/perf:build/classes/test"

$JCMD com.lmax.disruptor.OnePublisherToOneProcessorUniCastThroughputTest | tee old.asm
$JCMD com.lmax.disruptor.OnePublisherToOneProcessorUniCastOffHeapThroughputTest | tee new.asm

