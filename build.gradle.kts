import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "no.nav.syfo"
version = "1.0.0"

val javaVersion = JvmTarget.JVM_25


val coroutinesVersion = "1.10.2"
val syfoXmlCodegenVersion = "2.0.1"
val ibmMqVersion = "10.0.0.0"
val javaxActivationVersion = "1.1.1"
val jacksonVersion = "3.2.2"
val jaxbApiVersion = "2.4.0-b180830.0359"
val kafkaVersion = "4.3.1"
val ktorVersion = "3.5.2"
val logbackVersion = "1.6.3"
val logstashEncoderVersion = "9.0"
val prometheusVersion = "0.16.0"
val commonsTextVersion = "1.14.0"
val javaxAnnotationApiVersion = "1.3.2"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val javaTimeAdapterVersion = "1.1.3"
val mockkVersion = "1.14.11"
val googleCloudStorageVersion = "2.72.0"
val junitJupiterVersion = "6.1.3"
val flywayVersion = "13.3.0"
val hikariVersion = "7.1.0"
val postgresVersion = "42.7.8"
val ktfmtVersion = "0.56"
val opentelemetryVersion = "2.21.0"
val diagnosekoderVersion = "1.2026.0"
val testcontainerVersion = "2.0.3"

plugins {
    id("application")
    kotlin("jvm") version "2.4.10"
    id("com.diffplug.spotless") version "8.9.0"
}

application {
    mainClass.set("no.nav.syfo.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

dependencies {

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache5:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson3:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")

    implementation("tools.jackson.module:jackson-module-jaxb-annotations:${jacksonVersion}")
    implementation("tools.jackson.module:jackson-module-kotlin:${jacksonVersion}")
    implementation("tools.jackson.dataformat:jackson-dataformat-xml:${jacksonVersion}")

    implementation("no.nav.helse.xml:sm2013:$syfoXmlCodegenVersion")
    implementation("no.nav.helse.xml:xmlfellesformat:$syfoXmlCodegenVersion")
    implementation("no.nav.helse.xml:kith-hodemelding:$syfoXmlCodegenVersion")
    implementation("no.nav.helse.xml:kith-apprec:$syfoXmlCodegenVersion")
    implementation("com.ibm.mq:com.ibm.mq.jakarta.client:$ibmMqVersion")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:$opentelemetryVersion")

    implementation("com.google.cloud:google-cloud-storage:$googleCloudStorageVersion")

    implementation("org.apache.commons:commons-text:$commonsTextVersion")

    implementation("com.migesok:jaxb-java-time-adapters:$javaTimeAdapterVersion")

    implementation("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    implementation("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    implementation("javax.activation:activation:$javaxActivationVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    compileOnly("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("no.nav.helse:diagnosekoder:$diagnosekoderVersion")

    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainerVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(javaVersion)
    }
}


tasks {

    test {
        useJUnitPlatform {}
        testLogging {
            events("skipped", "failed")
            showStandardStreams = true
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }


    spotless {
        kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
        check {
            dependsOn("spotlessApply")
        }
    }
}
