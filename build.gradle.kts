//import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.internal.os.OperatingSystem
import java.util.Locale

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.allopen") version "2.3.21"
    id("com.google.devtools.ksp") version "2.3.7"
    id("io.micronaut.application") version "5.0.2"
    id("com.gradleup.shadow") version "9.4.1"
    id("io.micronaut.aot") version "5.0.2"
    id("org.graalvm.buildtools.native") version "1.1.6"
    kotlin("plugin.serialization") version "2.3.21"
}

version = "2.0"
group = "org.fenrirs"


enum class OsName { WINDOWS, MAC, LINUX, UNKNOWN }
enum class OsArch { X86_32, X86_64, ARM64, UNKNOWN }
data class OsType(val name: OsName, val arch: OsArch)

val currentOsType = run {
    val gradleOs = OperatingSystem.current()
    val osName = when {
        gradleOs.isMacOsX -> OsName.MAC
        gradleOs.isWindows -> OsName.WINDOWS
        gradleOs.isLinux -> OsName.LINUX
        else -> OsName.UNKNOWN
    }

    val osArch = when (providers.systemProperty("sun.arch.data.model").get()) {
        "32" -> OsArch.X86_32
        "64" -> when (providers.systemProperty("os.arch").get().lowercase(Locale.getDefault())) {
            "aarch64" -> OsArch.ARM64
            else -> OsArch.X86_64
        }
        else -> OsArch.UNKNOWN
    }

    OsType(osName, osArch)
}

val nativeImageSuffix = run {
    val osPart = when (currentOsType.name) {
        OsName.MAC -> "macos"
        OsName.WINDOWS -> "windows"
        OsName.LINUX -> "linux"
        OsName.UNKNOWN -> "unknown"
    }
    val archPart = when (currentOsType.arch) {
        OsArch.ARM64 -> "arm64"
        OsArch.X86_64 -> "amd64"
        OsArch.X86_32 -> "x86"
        OsArch.UNKNOWN -> "unknown"
    }
    "$osPart-$archPart"
}


val kotlinVersion= project.properties["kotlinVersion"]

repositories {
    mavenCentral()
}

val exposedVersion: String by project

dependencies {
    ksp("io.micronaut:micronaut-http-validation")
    ksp("io.micronaut.serde:micronaut-serde-processor")

    implementation("org.jetbrains.exposed:exposed-crypt:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-jodatime:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-json:${exposedVersion}")
    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-core
    implementation("org.jetbrains.exposed:exposed-core:${exposedVersion}")
    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-dao
    implementation("org.jetbrains.exposed:exposed-dao:${exposedVersion}")
    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-jdbc
    implementation("org.jetbrains.exposed:exposed-jdbc:${exposedVersion}")
    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-java-time
    implementation("org.jetbrains.exposed:exposed-java-time:${exposedVersion}")

    // Source: https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    implementation("io.micronaut:micronaut-aop")
    implementation("io.micronaut:micronaut-websocket")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micronaut.toml:micronaut-toml")
    implementation("org.jetbrains.kotlin:kotlin-reflect:${kotlinVersion}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinVersion}")
    compileOnly("io.micronaut:micronaut-http-client")
    implementation("tools.jackson.module:jackson-module-kotlin")
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("io.micronaut:micronaut-http-client")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}



application {
    mainClass = "org.fenrirs.ApplicationKt"
}

java {
    sourceCompatibility = JavaVersion.toVersion("25")
}


tasks.shadowJar {
    archiveFileName.set("${project.name}-${version}-jvm.jar")
}

// graalvmNative.toolchainDetection = false
/*graalvmNative {
    binaries {
        all {
            buildArgs.add("-H:+SharedArenaSupport")
            //buildArgs.add("-H:+PrintAnalysisCallTree")
            buildArgs.add("-H:+UnlockExperimentalVMOptions")

            buildArgs.add("-H:+StaticExecutableWithDynamicLibC")
            buildArgs.add("-march=compatibility")
            imageName.set("${project.name}-relay-${version}-${nativeImageSuffix}")
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(25))
                vendor.set(JvmVendorSpec.GRAAL_VM)
            })
            verbose.set(true)
        }
    }

}*/

val muslStatic = providers.gradleProperty("muslStatic")
    .map(String::toBoolean).getOrElse(false)

val libcName: String? = if (currentOsType.name == OsName.LINUX) {
    if (muslStatic) "musl" else "glibc"
} else null

graalvmNative {
    binaries {
        all {
            buildArgs.add("-H:-SharedArenaSupport")
            buildArgs.add("-H:+UnlockExperimentalVMOptions")

            if (muslStatic) {
                buildArgs.add("--static")
                buildArgs.add("--libc=musl")
                buildArgs.add("-H:-CheckToolchain")
            } else {
                buildArgs.add("-H:+StaticExecutableWithDynamicLibC")
            }

            buildArgs.add("-march=compatibility")
            buildArgs.add("-H:BuildOutputJSONFile=build-output.json")
            imageName.set(buildString {
                append("${project.name}-relay-${version}-${nativeImageSuffix}")
                if (libcName != null) append("-$libcName")
            })
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(25))
                vendor.set(JvmVendorSpec.GRAAL_VM)
            })
            verbose.set(true)
        }
    }
}




micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("org.fenrirs.*")
    }
    aot {
        // Please review carefully the optimizations enabled below
        // Check https://micronaut-projects.github.io/micronaut-aot/latest/guide/ for more details
        optimizeServiceLoading = false
        convertYamlToJava = false
        precomputeOperations = true
        cacheEnvironment = true
        optimizeClassLoading = true
        deduceEnvironment = true
        optimizeNetty = true
        replaceLogbackXml = true
    }

}


// https://docs.gradle.org/current/userguide/upgrading_major_version_9.html#test_task_fails_when_no_tests_are_discovered
tasks.withType<AbstractTestTask>().configureEach {
    failOnNoDiscoveredTests = false
}




