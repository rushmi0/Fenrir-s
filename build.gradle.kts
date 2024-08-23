import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.9.23"
    id("com.google.devtools.ksp") version "1.9.23-1.0.19"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.micronaut.application") version "4.4.0"
    id("io.micronaut.test-resources") version "4.4.0"
    id("io.micronaut.aot") version "4.4.0"
    id("org.graalvm.buildtools.native") version "0.10.0"
    kotlin("plugin.serialization") version "1.9.23"
}

version = "1.0"
group = "org.fenrirs"

val kotlinVersion = project.properties["kotlinVersion"]
repositories {
    mavenCentral()
}

val exposedVersion: String by project

dependencies {

    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    implementation("io.github.reactivecircus.cache4k:cache4k:0.13.0")

    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    implementation("org.jetbrains.exposed:exposed-crypt:$exposedVersion")

    implementation("org.jetbrains.exposed:exposed-jodatime:$exposedVersion")

    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")

    implementation("org.jetbrains.exposed:exposed-money:$exposedVersion")

    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")

    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-core
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")

    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-dao
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")

    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-jdbc
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")

    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-java-time
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    // https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp
    implementation("com.squareup.okhttp3:okhttp:4.10.0")


    // https://github.com/Kotlin/kotlinx.serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")

    // https://mvnrepository.com/artifact/org.mockito/mockito-core
    testImplementation("org.mockito:mockito-core:5.11.0")

    implementation("io.reactivex.rxjava3:rxkotlin:3.0.1")

    ksp("io.micronaut:micronaut-http-validation")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    implementation("io.micronaut:micronaut-websocket")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.redis:micronaut-redis-lettuce")
    implementation("io.micronaut.rxjava3:micronaut-rxjava3")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micronaut.toml:micronaut-toml")
    implementation("org.jetbrains.kotlin:kotlin-reflect:${kotlinVersion}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinVersion}")
    compileOnly("io.micronaut:micronaut-http-client")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("io.micronaut:micronaut-http-client")
}


application {
    mainClass = "org.fenrirs.ApplicationKt"
    applicationDefaultJvmArgs = listOf("-Dapplication.version=$version")
}


java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}


tasks {
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "21"
        }
    }
    compileKotlin {
        kotlinOptions {
            jvmTarget = "21"
        }
    }
}


tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_21.toString()
    }
}



graalvmNative {
    binaries {
        all {
            // * https://www.graalvm.org/latest/reference-manual/native-image/overview/BuildOutput/?fbclid=IwAR007Rh7fYg-CJZywqhFM8PF5XDWPvgOfaV9txFDqpy6PWjtZp2bXpgncL0_aem_Af0UTqW_wKY5RFkebOwqrANSJn-d6fpSoJLMyra23KLgMNQuur3l75gjN29_Ymw1JYkeX7upxGBzGPFkJ4iRuojh
            buildArgs.add("-H:+AddAllCharsets")
            buildArgs.add("-R:MaxHeapSize=3G")
            buildArgs.add("--no-fallback")
            imageName.set("${project.name}-v$version")
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
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
    testResources {
        additionalModules.add("jdbc-postgresql")
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


tasks.named<io.micronaut.gradle.docker.NativeImageDockerfile>("dockerfileNative") {
    jdkVersion = "21"
}
