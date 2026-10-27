plugins { java }

group = "megalodonte"
version = "0.1.0"

repositories { mavenCentral() }

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
}

sourceSets.main { java.srcDir("runtime/src/main/java") }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.jar {
    archiveFileName.set("${project.name}.jar")
    manifest { attributes["Main-Class"] = "tg_bot_editor.BotRunner" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({ configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
}
