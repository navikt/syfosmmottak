import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "no.nav.syfo"
version = "1.0.0"

val javaVersion = JvmTarget.JVM_21


val coroutinesVersion = "1.10.2"
val syfoXmlCodegenVersion = "2.0.1"
val ibmMqVersion = "9.4.3.0"
val javaxActivationVersion = "1.1.1"
val jacksonVersion = "2.19.1"
val jaxbApiVersion = "2.4.0-b180830.0359"
val kafkaVersion = "3.9.1"
val ktorVersion = "3.2.2"
val logbackVersion = "1.5.18"
val logstashEncoderVersion = "8.1"
val prometheusVersion = "0.16.0"
val commonsTextVersion = "1.13.1"
val javaxAnnotationApiVersion = "1.3.2"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val javaTimeAdapterVersion = "1.1.3"
val mockkVersion = "1.14.5"
val kotlinVersion = "2.2.0"
val googleCloudStorageVersion = "2.53.3"
val junitJupiterVersion = "5.13.3"
val flywayVersion = "11.10.2"
val hikariVersion = "6.3.0"
val postgresVersion = "42.7.7"
val embeddedPostgresVersion = "2.1.0"
val ktfmtVersion = "0.44"
val opentelemetryVersion = "2.17.1"
val diagnosekoderVersion = "1.2025.0"
///Due to vulnerabilities
val nettycommonVersion = "4.2.3.Final"
val snappyJavaVersion = "1.1.10.7"
val commonsCodecVersion = "1.18.0"


plugins {
    id("application")
    kotlin("jvm") version "2.2.0"
    id("com.diffplug.spotless") version "7.1.0"
    id("com.gradleup.shadow") version "8.3.8"
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion"){
        constraints {
            implementation("io.netty:netty-common:$nettycommonVersion") {
                because("Due to vulnerabilities in io.ktor:ktor-server-netty")
            }
        }
    }
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    constraints {
        implementation("commons-codec:commons-codec:$commonsCodecVersion") {
            because("override transient from io.ktor:ktor-client-apache")
        }
    }
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")
    constraints {
        implementation("org.xerial.snappy:snappy-java:$snappyJavaVersion") {
            because("override transient from org.apache.kafka:kafka_2.12")
        }
    }

    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("no.nav.helse.xml:sm2013:$syfoXmlCodegenVersion")
    implementation("no.nav.helse.xml:xmlfellesformat:$syfoXmlCodegenVersion")
    implementation("no.nav.helse.xml:kith-hodemelding:$syfoXmlCodegenVersion")
    implementation("no.nav.helse.xml:kith-apprec:$syfoXmlCodegenVersion")
    implementation("com.ibm.mq:com.ibm.mq.jakarta.client:$ibmMqVersion")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:$opentelemetryVersion")

    implementation("com.google.cloud:google-cloud-storage:$googleCloudStorageVersion")

    implementation("org.apache.commons:commons-text:$commonsTextVersion")

    implementation("com.migesok", "jaxb-java-time-adapters", javaTimeAdapterVersion)

    implementation("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    implementation("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    implementation("javax.activation:activation:$javaxActivationVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    compileOnly("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("no.nav.helse:diagnosekoder:$diagnosekoderVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("io.zonky.test:embedded-postgres:$embeddedPostgresVersion") {
        exclude(group = "commons-io", module = "commons-io")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(javaVersion)
    }
}


tasks {

    shadowJar {
        mergeServiceFiles {
            setPath("META-INF/services/org.flywaydb.core.extensibility.Plugin")
        }
        archiveBaseName.set("app")
        archiveClassifier.set("")
        isZip64 = true
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "no.nav.syfo.ApplicationKt",
                ),
            )
        }
    }


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
