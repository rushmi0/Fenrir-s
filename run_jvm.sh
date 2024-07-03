#!/bin/sh

if [ ! -f "build/docker/optimized/layers/application.jar" ]; then
    ./gradlew assemble
fi

java -jar build/docker/optimized/layers/application.jar