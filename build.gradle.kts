group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.7.3"
val syfoXmlCodegenVersion = "1.0.4"
val ibmMqVersion = "9.3.1.0"
val javaxActivationVersion = "1.1.1"
val jacksonVersion = "2.15.2"
val jaxbApiVersion = "2.4.0-b180830.0359"
val kafkaVersion = "3.4.0"
val ktorVersion = "2.3.4"
val logbackVersion = "1.4.11"
val logstashEncoderVersion = "7.4"
val prometheusVersion = "0.16.0"
val smCommonVersion = "1.0.14"
val jaxwsApiVersion = "2.3.1"
val commonsTextVersion = "1.10.0"
val javaxAnnotationApiVersion = "1.3.2"
val jaxwsToolsVersion = "2.3.2"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val javaTimeAdapterVersion = "1.1.3"
val mockkVersion = "1.13.7"
val kotlinVersion = "1.9.10"
val googleCloudStorageVersion = "2.26.1"
val junitJupiterVersion = "5.10.0"
val flywayVersion = "9.21.1"
val hikariVersion = "5.0.1"
val postgresVersion = "42.6.0"
val embeddedPostgresVersion = "2.0.4"
val commonsCodecVersion = "1.16.0"
val ktfmtVersion = "0.44"


plugins {
    id("application")
    kotlin("jvm") version "1.9.10"
    id("com.diffplug.spotless") version "6.21.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

application {
    mainClass.set("no.nav.syfo.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    maven(url= "https://packages.confluent.io/maven/")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfosm-common")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    // override transient version 1.11 from io.ktor:ktor-client-apache due to security vulnerability
    // https://devhub.checkmarx.com/cve-details/Cxeb68d52e-5509/
    implementation("commons-codec:commons-codec:$commonsCodecVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("no.nav.helse.xml:sm2013:$syfoXmlCodegenVersion")
    implementation("no.nav.helse.xml:xmlfellesformat:$syfoXmlCodegenVersion")
    implementation("no.nav.helse.xml:kith-hodemelding:$syfoXmlCodegenVersion")
    implementation("no.nav.helse.xml:kith-apprec:$syfoXmlCodegenVersion")

    implementation("no.nav.helse:syfosm-common-models:$smCommonVersion")
    implementation("no.nav.helse:syfosm-common-networking:$smCommonVersion")
    implementation("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
    implementation("no.nav.helse:syfosm-common-mq:$smCommonVersion")

    implementation("com.google.cloud:google-cloud-storage:$googleCloudStorageVersion")

    implementation("org.apache.commons:commons-text:$commonsTextVersion")

    implementation("com.migesok", "jaxb-java-time-adapters", javaTimeAdapterVersion)

    implementation("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    implementation("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    implementation("javax.activation:activation:$javaxActivationVersion")

    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("io.zonky.test:embedded-postgres:$embeddedPostgresVersion")
}


tasks {

    shadowJar {
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
