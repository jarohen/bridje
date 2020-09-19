plugins {
    base
    id("com.palantir.docker") version "0.25.0"
}

evaluationDependsOn(":component")

docker {
    name = "bridje/bridje"
    tag("release", "${name}:${version}")

    copySpec.with(copySpec().from(zipTree(project(":component").tasks["component"].outputs.files.singleFile)))
}

tasks.dockerPrepare {
    doLast {
        file("build/docker/bin/brj").setExecutable(true)
    }
}