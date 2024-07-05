#!/bin/bash

if [ ! -f "build/libs/fenrir-s-0.1-all-optimized.jar" ]; then
    ./gradlew build
fi

java -jar build/libs/fenrir-s-0.1-all-optimized.jar
