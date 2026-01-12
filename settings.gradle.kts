pluginManagement {
    plugins {
        val kotlinVersion: String by settings
        val ktlintVersion: String by settings

        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("org.jlleitschuh.gradle.ktlint") version ktlintVersion
    }
}
