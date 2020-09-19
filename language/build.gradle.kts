plugins {
    kotlin("jvm")
    kotlin("kapt")
    antlr
}

dependencies {
    val truffleVersion = "20.2.0"

    implementation(kotlin("stdlib"))

    antlr("org.antlr:antlr4:4.7.2")
    implementation("org.antlr:antlr4-runtime:4.7.2")

    compileOnly("org.graalvm.truffle:truffle-api:${truffleVersion}")
    testCompileOnly("org.graalvm.truffle:truffle-api:${truffleVersion}")

    kapt("org.graalvm.truffle:truffle-dsl-processor:${truffleVersion}")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.5.2")
}

configurations {
    compile {
        setExtendsFrom(extendsFrom.filterNot { it == configurations.antlr.get() })
    }
}

sourceSets {
    main { resources { setSrcDirs(listOf("src/main/resources", "src/main/brj")) } }
    test { resources { setSrcDirs(listOf("src/test/resources", "src/test/brj")) } }
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-visitor", "-no-listener")
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

tasks.compileTestKotlin {
    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "-Dtruffle.class.path.append=${sourceSets.main.get().runtimeClasspath.asPath}",
        "--add-exports=org.graalvm.truffle/com.oracle.truffle.api.interop=ALL-UNNAMED",
        "--add-exports=org.graalvm.truffle/com.oracle.truffle.api.source=ALL-UNNAMED",
        "--add-exports=org.graalvm.truffle/com.oracle.truffle.api=ALL-UNNAMED")
}

tasks.jar {
    group = "build"
    archiveFileName.set("brj-language.jar")

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
