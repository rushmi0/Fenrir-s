#!/bin/sh

if [ ! -f "build/docker/optimized/layers/app/application.jar" ]; then
    ./gradlew assemble
fi

java -jar build/docker/optimized/layers/app/application.jar