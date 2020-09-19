allprojects {
    group = "dev.bridje"
    version = "0.0.2"

    repositories {
        jcenter()
    }
}

plugins {
    kotlin("jvm") version "1.4.10"
    id("com.palantir.graal") version "0.7.1"
}