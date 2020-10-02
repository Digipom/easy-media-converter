#!/bin/sh
./gradlew clean
rm -rf build
rm -rf .gradle
cp gradle.properties.debug gradle.properties