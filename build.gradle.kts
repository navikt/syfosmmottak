import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.6.1"
val fellesformatVersion = "1.c22de09"
val ibmMqVersion = "9.2.4.0"
val javaxActivationVersion = "1.1.1"
val jacksonVersion = "2.13.2"
val jacksonPatchVersion = "2.13.2.2"
val jacksonBomVersion = "2.13.2.20220328"
val jaxbApiVersion = "2.4.0-b180830.0359"
val jaxbVersion = "2.3.0.1"
val jedisVersion = "4.2.2"
val kafkaVersion = "3.0.0"
val kithHodemeldingVersion = "1.c22de09"
val kithApprecVersion = "1.c22de09"
val kluentVersion = "1.68"
val ktorVersion = "2.0.1"
val logbackVersion = "1.2.11"
val logstashEncoderVersion = "7.1.1"
val prometheusVersion = "0.15.0"
val smCommonVersion = "1.c55f4d2"
val sykmeldingVersion = "1.c22de09"
val cxfVersion = "3.2.7"
val jaxwsApiVersion = "2.3.1"
val commonsTextVersion = "1.4"
val javaxAnnotationApiVersion = "1.3.2"
val jaxwsToolsVersion = "2.3.2"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val javaTimeAdapterVersion = "1.1.3"
val mockkVersion = "1.12.3"
val kotlinVersion = "1.6.20"
val googleCloudStorageVersion = "2.6.1"
val kotestVersion = "5.2.3"

plugins {
    id("io.mateo.cxf-codegen") version "1.0.0-rc.3"
    kotlin("jvm") version "1.6.20"
    id("org.jmailen.kotlinter") version "3.10.0"
    id("com.diffplug.spotless") version "6.5.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}


buildscript {
    dependencies {
        classpath("javax.xml.bind:jaxb-api:2.4.0-b180830.0359")
        classpath("org.glassfish.jaxb:jaxb-runtime:2.4.0-b180830.0438")
        classpath("com.sun.activation:javax.activation:1.2.0")
        classpath("com.sun.xml.ws:jaxws-tools:2.3.1") {
            exclude(group = "com.sun.xml.ws", module = "policy")
        }
    }
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
    cxfCodegen("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    cxfCodegen("javax.activation:activation:$javaxActivationVersion")
    cxfCodegen("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    cxfCodegen("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    cxfCodegen ("javax.xml.ws:jaxws-api:$jaxwsApiVersion")
    cxfCodegen ("com.sun.xml.ws:jaxws-tools:$jaxwsToolsVersion") {
        exclude(group = "com.sun.xml.ws", module = "policy")
    }
    cxfCodegen("com.sun.xml.bind:jaxb-impl:2.3.3")
    cxfCodegen("jakarta.xml.ws:jakarta.xml.ws-api:2.3.3")
    cxfCodegen("jakarta.annotation:jakarta.annotation-api:1.3.5")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson:jackson-bom:$jacksonBomVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonPatchVersion")
    implementation("no.nav.helse.xml:sm2013:$sykmeldingVersion")
    implementation("no.nav.helse.xml:xmlfellesformat:$fellesformatVersion")
    implementation("no.nav.helse.xml:kith-hodemelding:$kithHodemeldingVersion")
    implementation("no.nav.helse.xml:kith-apprec:$kithApprecVersion")

    implementation("no.nav.helse:syfosm-common-models:$smCommonVersion")
    implementation("no.nav.helse:syfosm-common-networking:$smCommonVersion")
    implementation("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
    implementation("no.nav.helse:syfosm-common-mq:$smCommonVersion")
    implementation("no.nav.helse:syfosm-common-ws:$smCommonVersion")

    implementation("com.google.cloud:google-cloud-storage:$googleCloudStorageVersion")
    implementation("redis.clients:jedis:$jedisVersion")

    implementation("org.apache.commons:commons-text:$commonsTextVersion")

    implementation("com.migesok", "jaxb-java-time-adapters", javaTimeAdapterVersion)

    implementation("javax.xml.ws:jaxws-api:$jaxwsApiVersion")
    implementation("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    implementation("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    implementation("javax.activation:activation:$javaxActivationVersion")
    implementation("com.sun.xml.ws:jaxws-tools:$jaxwsToolsVersion") {
        exclude(group = "com.sun.xml.ws", module = "policy")
    }
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }

    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
}


tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
    }
    cxfCodegen {
        wsdl2java {
            register("subscription") {
                wsdl.set(file("$projectDir/src/main/resources/wsdl/subscription.wsdl"))
                bindingFiles.add("$projectDir/src/main/resources/xjb/binding.xml")
            }
        }
    }
    create("printVersion") {

        doLast {
            println(project.version)
        }
    }
    withType<KotlinCompile> {
        dependsOn("wsdl2javaSubscription")
        kotlinOptions.jvmTarget = "17"
    }



    withType<ShadowJar> {
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
    }

    withType<Test> {
        useJUnitPlatform {}
        testLogging {
            showStandardStreams = true
        }
    }

    "check" {
        dependsOn("formatKotlin")
    }
}
