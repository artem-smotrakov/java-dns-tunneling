#!/bin/bash

# cleanup
rm -rf classes
mkdir classes

# compile sources
${JAVA_HOME}/bin/javac -d classes src/*.java
