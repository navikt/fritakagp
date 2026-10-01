val mainClassFritakAgp = "no.nav.helse.fritakagp.AppKt"

plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jmailen.kotlinter")
    jacoco
}

application {
    mainClass.set(mainClassFritakAgp)
}

kotlin {
    jvmToolchain(25)
}

repositories {
    val githubPassword = project.property("githubPassword") as String

    mavenCentral()
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
                val file =
                    layout.buildDirectory
                        .file("libs/${it.name}")
                        .get()
                        .asFile
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
        description = "Tester som krever database- og Kafka-containere"
        include("no/nav/helse/slowtests/**")
        outputs.upToDateWhen { false }
        group = "verification"
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
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
    val aaregClientVersion = project.property("aaregClientVersion") as String
    val altinnClientVersion = project.property("altinnClientVersion") as String
    val apacheCommonsTextVersion = project.property("apacheCommonsTextVersion") as String
    val arbeidsgiverNotifikasjonKlientVersion = project.property("arbeidsgiverNotifikasjonKlientVersion") as String
    val assertJVersion = project.property("assertJVersion") as String
    val bakgrunnsjobbVersion = project.property("bakgrunnsjobbVersion") as String
    val brregClientVersion = project.property("brregClientVersion") as String
    val dokarkivKlientVersion = project.property("dokarkivKlientVersion") as String
    val flywayVersion = project.property("flywayVersion") as String
    val gcpStorageVersion = project.property("gcpStorageVersion") as String
    val hikariVersion = project.property("hikariVersion") as String
    val jacksonModuleKotlinVersion = project.property("jacksonModuleKotlinVersion") as String
    val jacksonVersion = project.property("jacksonVersion") as String
    val junitJupiterVersion = project.property("junitJupiterVersion") as String
    val kafkaClient = project.property("kafkaClient") as String
    val kformatVersion = project.property("kformatVersion") as String
    val koinVersion = project.property("koinVersion") as String
    val kotlinxCoroutinesVersion = project.property("kotlinxCoroutinesVersion") as String
    val kotlinxSerializationVersion = project.property("kotlinxSerializationVersion") as String
    val ktorVersion = project.property("ktorVersion") as String
    val logbackEncoderVersion = project.property("logbackEncoderVersion") as String
    val logbackVersion = project.property("logbackVersion") as String
    val mockOAuth2ServerVersion = project.property("mockOAuth2ServerVersion") as String
    val mockkVersion = project.property("mockkVersion") as String
    val pdfboxVersion = project.property("pdfboxVersion") as String
    val pdlClientVersion = project.property("pdlClientVersion") as String
    val postgresqlVersion = project.property("postgresqlVersion") as String
    val prometheusVersion = project.property("prometheusVersion") as String
    val slf4jVersion = project.property("slf4jVersion") as String
    val tmsVarselKotlinBuilderVersion = project.property("tmsVarselKotlinBuilderVersion") as String
    val tokenSupportVersion = project.property("tokenSupportVersion") as String
    val utilsVersion = project.property("utilsVersion") as String
    val valiktorVersion = project.property("valiktorVersion") as String

    implementation("org.apache.commons:commons-text:$apacheCommonsTextVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonModuleKotlinVersion")
    implementation("com.google.cloud:google-cloud-storage:$gcpStorageVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("de.m3y.kformat:kformat:$kformatVersion")
    implementation("io.insert-koin:koin-core-jvm:$koinVersion")
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.ktor:ktor-client-apache5:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.mockk:mockk:$mockkVersion") // Brukes til å mocke eksterne avhengigheter under lokal kjøring
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("org.apache.kafka:kafka-clients:$kafkaClient")
    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.valiktor:valiktor-core:$valiktorVersion")
    implementation("org.valiktor:valiktor-javatime:$valiktorVersion")

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
    implementation("no.nav.security:token-validation-ktor-v3:$tokenSupportVersion")
    implementation("no.nav.tms.varsel:kotlin-builder:$tmsVarselKotlinBuilderVersion")

    runtimeOnly("ch.qos.logback:logback-classic:$logbackVersion")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion")
    runtimeOnly("org.postgresql:postgresql:$postgresqlVersion")

    testImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
    testImplementation("io.insert-koin:koin-test:$koinVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.assertj:assertj-core:$assertJVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
