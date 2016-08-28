#!/bin/bash

# start an HTTP server
${JAVA_HOME}/bin/java -classpath classes SimpleHttpServer ${1}
