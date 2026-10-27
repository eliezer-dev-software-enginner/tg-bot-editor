plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}
group = "megalodonte"
version = "0.1.0"
repositories { mavenLocal(); mavenCentral() }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }
javafx { version = "25.0.1"; modules("javafx.controls", "javafx.graphics") }
dependencies {
    implementation("megalodonte:megalodonte-components:1.0.0-beta")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("megalodonte:megalodonte-base:1.0.0-beta")
    implementation("megalodonte:megalodonte-theme:1.0.0-beta")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
application {
    mainClass.set("tg_bot_editor.Main")
    applicationDefaultJvmArgs = listOf("--enable-native-access=javafx.graphics,ALL-UNNAMED")
}
tasks.test { useJUnitPlatform() }
tasks.processResources {
    from("src/main/java") {
        include(
            "tg_bot_editor/ActionCompiler.java", "tg_bot_editor/BotAction.java",
            "tg_bot_editor/BotButton.java", "tg_bot_editor/BotContext.java",
            "tg_bot_editor/BotDelivery.java", "tg_bot_editor/BotReply.java",
            "tg_bot_editor/BotRunner.java", "tg_bot_editor/ActionBindings.java",
            "tg_bot_editor/YouTubeSubscriptions.java", "tg_bot_editor/FlowProject.java",
            "tg_bot_editor/FlowRouter.java", "tg_bot_editor/FlowStore.java",
            "tg_bot_editor/FlowValidator.java", "tg_bot_editor/ProjectEnvironment.java"
        )
        into("templates/project-runtime")
    }
}
tasks.register<JavaExec>("runBot") {
    group = "application"
    description = "Run all saved flows through one Telegram bot token"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("tg_bot_editor.BotRunner")
    args(providers.gradleProperty("botProject").orElse("examples/meu-primeiro-projeto/bot.json").get())
}
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
