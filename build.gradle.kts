plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.24"
}

val minecraftVersion: String by project
val paperBuild: String by project

group = "com.maxxlive"
// CI passes -PpluginVersion=<mc>-patch-<n>; local builds are marked as dev builds
version = providers.gradleProperty("pluginVersion").getOrElse("$minecraftVersion-dev")

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/ghost-chu/")
}

dependencies {
    paperweight.paperDevBundle("$minecraftVersion.build.$paperBuild-stable")
    compileOnly("com.ghostchu:quickshop-bukkit:6.3.0.1")
    compileOnly("cn.lunadeer:DominionAPI:4.9.6")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release = 25
    }
    processResources {
        val props = mapOf("version" to project.version, "minecraftVersion" to minecraftVersion)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
