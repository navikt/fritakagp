pluginManagement {
    plugins {
        val kotlinVersion = providers.gradleProperty("kotlinVersion").get()
        val ktlintVersion = providers.gradleProperty("ktlintVersion").get()

        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("org.jlleitschuh.gradle.ktlint") version ktlintVersion
    }
}
