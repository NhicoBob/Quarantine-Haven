plugins {
    id("java")
    id("application")
}

group = "com.example.game"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation("de.gurkenlabs:litiengine:0.12.0")
}

application {
    mainClass.set("com.example.game.Program")
}

