#!/usr/bin/env bash

set -euo pipefail

BUILD_DIR=build_tmp
BUILD_LIB_DIR=${BUILD_DIR}/lib
BUILD_MAIN_CLASSES=${BUILD_DIR}/main/classes
BUILD_TEST_CLASSES=${BUILD_DIR}/test/classes
BUILD_PERF_CLASSES=${BUILD_DIR}/perf/classes

if [ -z ${JAVA_HOME+x} ]; then
    echo "JAVA_HOME is not set"
    exit -1
else
    JC=${JAVA_HOME}/bin/javac
fi

function create_dir()
{
    if [ ! -d $1 ]; then
        mkdir -p $1
    fi
}

function create_build()
{
    create_dir ${BUILD_LIB_DIR}
    create_dir ${BUILD_MAIN_CLASSES}
    create_dir ${BUILD_TEST_CLASSES}
    create_dir ${BUILD_PERF_CLASSES}
}

function clean_classes()
{
    rm -rf ${BUILD_MAIN_CLASSES}/*
    rm -rf ${BUILD_TEST_CLASSES}/*
    rm -rf ${BUILD_PERF_CLASSES}/*
}

function download()
{
    if [ ! -e $BUILD_LIB_DIR/$1 ]; then
        cd ${BUILD_LIB_DIR} &&
        {
            curl -o $1 $2
            if [ $? -eq 1 ]; then
                echo "curl download failed for $2"
                exit $?
            fi
            cd -
        }
    fi
}

function do_javac()
{
    $JC -Xlint:deprecation -g -d $2 @$1
}

function download_dependencies()
{
    download 'junit-4.12.jar' 'http://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar'
    download 'hamcrest-core-1.3.jar' 'http://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar'
    download 'HdrHistogram-2.1.8.jar' 'http://repo1.maven.org/maven2/org/hdrhistogram/HdrHistogram/2.1.8/HdrHistogram-2.1.8.jar'
}

function compile()
{
    COMPILE_FILE=${BUILD_DIR}/$2
    find $1 -name '*.java' >> ${COMPILE_FILE}
    do_javac ${COMPILE_FILE} $3
}

function main_compile()
{
    rm -f ${BUILD_DIR}/java.main.java.txt
    compile 'src/main/java' java.main.java.txt ${BUILD_MAIN_CLASSES}
}

function test_compile()
{
    rm -f ${BUILD_DIR}/java.test.java.txt
    JARS=$(find ${BUILD_LIB_DIR} -name "*.jar" | paste -sd ':')
    echo "-cp ${BUILD_MAIN_CLASSES}:$JARS" >> ${BUILD_DIR}/java.test.java.txt
    compile 'src/test/java' java.test.java.txt ${BUILD_TEST_CLASSES}
}

function run_tests()
{
    JARS=$(find ${BUILD_LIB_DIR} -name "*.jar" | paste -sd ':')
    TESTS=$(for i in $(find build_tmp/test/classes -name '*Test.class') ; do l=${i#build_tmp/test/classes/} ; j=${l%.class} ; echo ${j////.} ; done | paste -sd ' ')
    $JAVA_HOME/bin/java -cp ${JARS}:${BUILD_MAIN_CLASSES}:${BUILD_TEST_CLASSES} org.junit.runner.JUnitCore $TESTS
}

create_build &&
download_dependencies &&
clean_classes &&
main_compile &&
test_compile &&
run_tests