#!/bin/bash

# cleanup
rm -rf ${KEYSTORE} ${JAR_FILE}

JAR_FILE=payload.jar
PASSWORD=password
KEYSTORE=ks.jks
ALIAS=cert

# create a jar file
${JAVA_HOME}/bin/jar cfm ${JAR_FILE} manifest.mf -C classes .

# create a keystore with a self-signed certificate
${JAVA_HOME}/bin/keytool -genkey -keyalg rsa -alias ${ALIAS} -keystore ${KEYSTORE} \
    -storepass ${PASSWORD} -keypass ${PASSWORD} \
    -dname "CN=This is a safe applet, OU=Safe, O=Safe, L=Safe, ST=Safe, C=US"

# sign the jar file
${JAVA_HOME}/bin/jarsigner -keystore ${KEYSTORE} \
    -storepass ${PASSWORD} -keypass ${PASSWORD} ${JAR_FILE} ${ALIAS}

# smoke test
${JAVA_HOME}/bin/jarsigner -keystore ${KEYSTORE} \
    -storepass ${PASSWORD} -keypass ${PASSWORD} -verify -verbose ${JAR_FILE}
