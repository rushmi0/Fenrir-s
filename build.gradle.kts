import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jooq.meta.jaxb.Logging
import org.jooq.meta.jaxb.Property

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.9.23"
    id("com.google.devtools.ksp") version "1.9.23-1.0.19"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.micronaut.application") version "4.4.0"
    id("io.micronaut.test-resources") version "4.4.0"
    id("io.micronaut.aot") version "4.4.0"
    id("nu.studer.jooq") version "8.2"
    //id("org.sonarqube") version "4.4.1.3373"
    //id("org.graalvm.buildtools.native") version "0.10.2"
    kotlin("plugin.serialization") version "1.9.23"
}

version = "0.1"
group = "org.fenrirs"

val kotlinVersion = project.properties["kotlinVersion"]
repositories {
    mavenCentral()
}

dependencies {

    // https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp
    implementation("com.squareup.okhttp3:okhttp:4.10.0")

    // https://mvnrepository.com/artifact/fr.acinq.secp256k1/secp256k1-kmp-jni-jvm
    implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-jvm:0.15.0")

    // https://mvnrepository.com/artifact/fr.acinq.secp256k1/secp256k1-kmp-jvm
    implementation("fr.acinq.secp256k1:secp256k1-kmp-jvm:0.15.0")

    // https://mvnrepository.com/artifact/fr.acinq.secp256k1/secp256k1-kmp
    implementation("fr.acinq.secp256k1:secp256k1-kmp:0.15.0")

    // https://github.com/Kotlin/kotlinx.serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")

    // https://mvnrepository.com/artifact/org.mockito/mockito-core
    testImplementation("org.mockito:mockito-core:5.11.0")

    jooqGenerator("org.postgresql:postgresql:42.3.9")

    implementation("io.reactivex.rxjava3:rxkotlin:3.0.1")

    ksp("io.micronaut:micronaut-http-validation")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    implementation("io.micronaut:micronaut-websocket")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.redis:micronaut-redis-lettuce")
    implementation("io.micronaut.rxjava3:micronaut-rxjava3")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micronaut.sql:micronaut-jooq")
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
        javaParameters = true
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
        optimizeServiceLoading = true
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

jooq {
    version.set("3.18.7")  // default (can be omitted)
    edition.set(nu.studer.gradle.jooq.JooqEdition.OSS)  // default (can be omitted)

    configurations {
        create("main") {  // name of the jOOQ configuration
            generateSchemaSourceOnCompilation.set(true)  // default (can be omitted)

            jooqConfiguration.apply {
                logging = Logging.WARN
                //logging = Logging.DEBUG
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    //url = "jdbc:postgresql://localhost:5432/nostr"
                    url = "jdbc:postgresql://relay-db:5432/nostr"
                    user = "rushmi0"
                    password = "sql@min"
                    properties.add(Property().apply {
                        key = "ssl"
                        value = "false"
                    })
                }
                generator.apply {
                    name = "org.jooq.codegen.DefaultGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isImmutablePojos = true
                        isFluentSetters = true
                    }
                    target.apply {
                        packageName = "nostr.relay.infra.database"
                        directory = "target/infra/jooq/main"
                    }
                    strategy.name = "org.jooq.codegen.DefaultGeneratorStrategy"
                }
            }
        }
    }
}
