import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.20.0"
    id("com.diffplug.spotless") version "8.0.0"
}

group = "dev.goij"

version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        val localIde = providers.gradleProperty("localIde")
        if (localIde.isPresent) local(localIde.get()) else intellijIdea("2026.2.2")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
    }
    testImplementation("junit:junit:4.13.2")
}

kotlin { jvmToolchain(25) }

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = "262.*"
        }
    }
    pluginVerification {
        ides {
            if (providers.gradleProperty("localIde").isPresent)
                local(providers.gradleProperty("localIde").get())
            else create("IU", "2026.2.2")
        }
    }
}

spotless {
    kotlin { ktfmt("0.58").kotlinlangStyle() }
    kotlinGradle { ktfmt("0.58").kotlinlangStyle() }
}

tasks.test { systemProperty("idea.is.unit.test", "true") }
