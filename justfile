set shell := ["bash", "-euo", "pipefail", "-c"]

script_dir := justfile_directory()
gradlew    := script_dir / "gradlew"
version    := "2.0"
artifact   := script_dir / "build" / "libs" / "Fenrir-s-" + version + "-jvm.jar"

# List available recipes
default:
    @just --list

# Run unit tests
test:
    {{gradlew}} test

# Build the web client (Fenrir-client) and sync its output into src/main/resources/public
build-client:
    cd {{script_dir}}/Fenrir-client && npm run build
    rm -rf {{script_dir}}/src/main/resources/public/assets
    cp -r {{script_dir}}/Fenrir-client/dist/. {{script_dir}}/src/main/resources/public/

# Build the web client, sync it in, then build the JVM + native artifacts
build-full: build-client build

# Build the JVM shadow jar (build/libs/*-jvm.jar)
build-jvm:
    {{gradlew}} shadowJar

# Run the app as a plain JVM process (no native image)
run-jvm:
    {{gradlew}} run

# Clean, rebuild the web client + shadow jar, and run it standalone (suppresses GraalPy JVM warnings)
run-jvm-app: clean build-client build-jvm
    java -Xmx512m --sun-misc-unsafe-memory-access=allow -jar {{artifact}}

# Build a native executable (fast, non-optimized) for local iteration
native-dev: clean build-client
    {{gradlew}} nativeCompile

# Build the optimized native executable (matches CI / release builds)
build-native: clean build-client
    {{gradlew}} nativeOptimizedCompile

# Build a statically linked musl native executable (Linux only)
build-native-musl: clean build-client
    {{gradlew}} nativeOptimizedCompile -PmuslStatic=true

# Run the optimized native binary via Gradle
run-native: clean build-client
    {{gradlew}} nativeOptimizedRun

# Build both JVM and native artifacts
build: build-jvm build-native

# Build the JVM Docker image
docker-jvm:
    docker build -f {{script_dir}}/Dockerfile.jvm -t fenrir-s:{{version}}-jvm {{script_dir}}

# Build the native Docker image
docker-native:
    docker build -f {{script_dir}}/Dockerfile.native -t fenrir-s:{{version}}-native {{script_dir}}

# Start the full stack (db + jvm + native) with docker compose
up:
    docker compose -f {{script_dir}}/compose.yml up -d --build

# Stop the stack
down:
    docker compose -f {{script_dir}}/compose.yml down

# Remove Gradle build outputs
clean:
    {{gradlew}} clean