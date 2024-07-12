#!/bin/bash

# เริ่มต้นตรวจสอบว่า Gradle สามารถคอมไพล์โปรเจคได้โดยไม่ต้องรันอีกครั้ง
if [ ! -f "build/libs/fenrir-s-0.1-all-optimized.jar" ]; then
    echo "Building project..."
    ./gradlew build
    # shellcheck disable=SC2181
    if [ $? -ne 0 ]; then
        echo "Error: Gradle build failed."
        exit 1
    fi
fi

# รันแอปพลิเคชัน Java จาก JAR ที่คอมไพล์แล้ว
echo "Starting application..."
java -jar build/libs/fenrir-s-0.1-all-optimized.jar
