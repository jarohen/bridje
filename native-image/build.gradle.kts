plugins {
    base
    id("com.palantir.graal")
}

evaluationDependsOn(":language")
evaluationDependsOn(":launcher")

graal {
    graalVersion("20.2.0")
    mainClass("brj.BridjeLauncher")
    outputName("brj")
    javaVersion(JavaVersion.VERSION_11.toString())

    val languageJar = project(":language").tasks["jar"].outputs.files.singleFile
    val launcherJar = project(":launcher").tasks["jar"].outputs.files.singleFile
    option("-cp")
    option("$languageJar:$launcherJar")

    option("--macro:truffle")
    option("--features=org.graalvm.home.HomeFinderFeature")
    option("-Dorg.graalvm.launcher.relative.home=languages/brj/bin/brj")
    option("-Dorg.graalvm.launcher.classpath=lib/brj/brj-launcher.jar")
    option("--initialize-at-build-time")
    option("--no-fallback")
    option("-H:IncludeResources='.*\\.brj'")
}
