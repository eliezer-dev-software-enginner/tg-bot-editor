rootProject.name = "tg-bot-editor"
if (providers.gradleProperty("useLocalSources").orNull != "false") {
    includeBuild("../../framework/libraries/megalodonte-components")
    includeBuild("../../framework/libraries/megalodonte-theme")
}
