pluginManagement {
    plugins {
        val kotlinVersion = providers.gradleProperty("kotlinVersion").get()
        val kotlinterVersion = providers.gradleProperty("kotlinterVersion").get()

        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("org.jmailen.kotlinter") version kotlinterVersion
    }
}
