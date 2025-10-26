plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.9.25"
    id("com.google.devtools.ksp") version "1.9.25-1.0.20"
    id("com.gradleup.shadow") version "8.3.7"
    id("io.micronaut.application") version "4.4.0"
    id("io.micronaut.test-resources") version "4.4.0"
    id("io.micronaut.aot") version "4.5.4"
    id("org.graalvm.buildtools.native") version "0.11.1"
    kotlin("plugin.serialization") version "1.9.25"
}

version = "2.0"
group = "org.fenrirs"

val kotlinVersion = project.properties["kotlinVersion"]
repositories {
    mavenCentral()
}

val exposedVersion: String by project

dependencies {

    developmentOnly("io.micronaut.controlpanel:micronaut-control-panel-ui")
    developmentOnly("io.micronaut.controlpanel:micronaut-control-panel-management")


    implementation("io.micronaut.views:micronaut-views-react")

    implementation("org.graalvm.polyglot:polyglot:25.0.0")
    implementation("org.graalvm.polyglot:js:25.0.0")
    implementation("org.graalvm.polyglot:python:25.0.0")
    //implementation("org.graalvm.polyglot:wasm:25.0.0")

    
    implementation("io.github.reactivecircus.cache4k:cache4k:0.13.0")

    implementation("org.jetbrains.exposed:exposed-crypt:$exposedVersion")

    implementation("org.jetbrains.exposed:exposed-jodatime:$exposedVersion")

    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")

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

    // https://mvnrepository.com/artifact/io.micronaut/micronaut-websocket
    implementation("io.micronaut:micronaut-websocket:4.7.1")

    ksp("io.micronaut:micronaut-http-validation")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micronaut.toml:micronaut-toml")
    implementation("org.jetbrains.kotlin:kotlin-reflect:${kotlinVersion}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinVersion}")
    compileOnly("io.micronaut:micronaut-http-client")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
    runtimeOnly("ch.qos.logback:logback-classic:1.5.13")

    runtimeOnly("org.postgresql:postgresql")
    testImplementation("io.micronaut:micronaut-http-client")
}

application {
    mainClass = "org.fenrirs.ApplicationKt"
    applicationDefaultJvmArgs = listOf(
        "-Dapplication.version=$version",
        "-XX:+EnableJVMCI",
        "-Dpolyglotimpl.DisableMultiReleaseCheck=true"
    )

}


java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}


// * https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/BuildOutput.md
graalvmNative {
    binaries {
        all {
            // * https://www.graalvm.org/latest/reference-manual/native-image/overview/BuildOutput/?fbclid=IwAR007Rh7fYg-CJZywqhFM8PF5XDWPvgOfaV9txFDqpy6PWjtZp2bXpgncL0_aem_Af0UTqW_wKY5RFkebOwqrANSJn-d6fpSoJLMyra23KLgMNQuur3l75gjN29_Ymw1JYkeX7upxGBzGPFkJ4iRuojh
            // * https://github.com/oracle/graal/issues/1446
            buildArgs.add("-H:+AddAllCharsets")
            buildArgs.add("-R:MaxHeapSize=4G")
            buildArgs.add("--no-fallback")
            //buildArgs.add("--target=linux-amd64")
            buildArgs.add("-march=native")
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
        replaceLogbackXml = false
    }
}

tasks.register<Exec>("packageAppJar") {
    dependsOn("shadowJar")

    val appName = "Fenrir-s"
    val versionName = project.version
    val jarFile = file("$buildDir/libs/${project.name}-$versionName-all.jar")

    doFirst {
        println("📦 Packaging fat jar with jpackage...")
    }

    commandLine(
        "jpackage",
        "--name", appName,
        "--input", jarFile.parent,
        "--main-jar", jarFile.name,
        "--main-class", "org.fenrirs.ApplicationKt",
        "--java-options", "-Xmx1G",
        "--app-version", versionName.toString(),
        "--copyright", "Fenrir-s © 2025",
        "--description", "Nostr Relay Native Application",
        "--vendor", "Fenrir-s",
        "--icon", "src/main/resources/public/assets/fenrir-UG18wG2_.svg",
        "--type", "app-image"
    )
}


