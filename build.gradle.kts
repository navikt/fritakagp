import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val mainClassFritakAgp = "no.nav.helse.fritakagp.AppKt"

plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
    id("com.github.ben-manes.versions")
    id("com.autonomousapps.dependency-analysis")
    jacoco
}

application {
    mainClass.set(mainClassFritakAgp)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

repositories {
    val githubPassword: String by project

    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://jitpack.io") {
        content {
            excludeGroup("no.nav.helsearbeidsgiver")
        }
    }
    maven {
        setUrl("https://maven.pkg.github.com/navikt/*")
        credentials {
            username = "x-access-token"
            password = githubPassword
        }
    }
}

tasks {
    named<Jar>("jar") {
        val dependencies = configurations.runtimeClasspath.get()

        archiveBaseName.set("app")

        manifest {
            attributes["Main-Class"] = mainClassFritakAgp
            attributes["Class-Path"] = dependencies.joinToString(separator = " ") { it.name }
        }

        doLast {
            dependencies.forEach {
                val file = layout.buildDirectory.file("libs/${it.name}").get().asFile
                if (!file.exists()) {
                    it.copyTo(file)
                }
            }
        }
    }

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    named<Test>("test") {
        include("no/nav/helse/**")
        exclude("no/nav/helse/slowtests/**")
    }

    register<Test>("slowTests") {
        include("no/nav/helse/slowtests/**")
        outputs.upToDateWhen { false }
        group = "verification"
    }

    jacocoTestReport {
        dependsOn(test)
        reports {
            xml.required.set(true)
            csv.required.set(false)
            html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
        }
    }

    test {
        finalizedBy(jacocoTestReport)
    }
}

dependencies {
    val aaregClientVersion: String by project
    val altinnClientVersion: String by project
    val altinnCorrespondenceAgencyVersion: String by project
    val apacheCommonsVersion: String by project
    val arbeidsgiverNotifikasjonKlientVersion: String by project
    val assertJVersion: String by project
    val bakgrunnsjobbVersion: String by project
    val brregClientVersion: String by project
    val confluentVersion: String by project
    val cxfVersion: String by project
    val dokarkivKlientVersion: String by project
    val flywayVersion: String by project
    val gcpStorageVersion: String by project
    val hikariVersion: String by project
    val jacksonModuleKotlinVersion: String by project
    val jacksonVersion: String by project
    val junitJupiterVersion: String by project
    val kafkaClient: String by project
    val kformatVersion: String by project
    val koinVersion: String by project
    val kotestVersion: String by project
    val kotlinxCoroutinesVersion: String by project
    val kotlinxSerializationVersion: String by project
    val ktorVersion: String by project
    val logbackEncoderVersion: String by project
    val logbackVersion: String by project
    val mockOAuth2ServerVersion: String by project
    val mockkVersion: String by project
    val pdfboxVersion: String by project
    val pdlClientVersion: String by project
    val postgresqlVersion: String by project
    val prometheusVersion: String by project
    val slf4jVersion: String by project
    val tmsVarselKotlinBuilderVersion: String by project
    val tokenSupportVersion: String by project
    val utilsVersion: String by project
    val valiktorVersion: String by project

    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonModuleKotlinVersion")
    implementation("com.google.cloud:google-cloud-storage:$gcpStorageVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("de.m3y.kformat:kformat:$kformatVersion")
    implementation("io.confluent:kafka-schema-registry-client:$confluentVersion")
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.ktor:ktor-client-apache5:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server:$ktorVersion")
    implementation("io.mockk:mockk:$mockkVersion") // Brukes til å mocke eksterne avhengigheter under lokal kjøring
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("no.nav.helsearbeidsgiver:aareg-client:$aaregClientVersion")
    implementation("no.nav.helsearbeidsgiver:altinn-client:$altinnClientVersion")
    implementation("no.nav.helsearbeidsgiver:arbeidsgiver-notifikasjon-klient:$arbeidsgiverNotifikasjonKlientVersion")
    implementation("no.nav.helsearbeidsgiver:brreg-client:$brregClientVersion")
    implementation("no.nav.helsearbeidsgiver:dokarkiv-client:$dokarkivKlientVersion")
    implementation("no.nav.helsearbeidsgiver:hag-bakgrunnsjobb:$bakgrunnsjobbVersion")
    implementation("no.nav.helsearbeidsgiver:pdl-client:$pdlClientVersion")
    implementation("no.nav.helsearbeidsgiver:utils:$utilsVersion")
    implementation("no.nav.security:mock-oauth2-server:$mockOAuth2ServerVersion") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "org.slf4j", module = "slf4j-api")
        exclude(group = "io.netty", module = "netty-all")
    }
    implementation("no.nav.security:token-validation-core:$tokenSupportVersion")
    implementation("no.nav.security:token-validation-ktor-v3:$tokenSupportVersion")
    implementation("no.nav.tjenestespesifikasjoner:altinn-correspondence-agency-external-basic:$altinnCorrespondenceAgencyVersion")
    implementation("no.nav.tms.varsel:kotlin-builder:$tmsVarselKotlinBuilderVersion")
    implementation("org.apache.commons:commons-lang3:$apacheCommonsVersion")
    implementation("org.apache.cxf:cxf-rt-features-logging:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-transports-http:$cxfVersion")
    implementation("org.apache.kafka:kafka-clients:$kafkaClient")
    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.valiktor:valiktor-core:$valiktorVersion")
    implementation("org.valiktor:valiktor-javatime:$valiktorVersion")

    runtimeOnly("ch.qos.logback:logback-classic:$logbackVersion")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion")
    runtimeOnly("org.postgresql:postgresql:$postgresqlVersion")

    testImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
    testImplementation("io.insert-koin:koin-test:$koinVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.assertj:assertj-core:$assertJVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinxCoroutinesVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
