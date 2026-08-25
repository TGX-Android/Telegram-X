plugins.withType<JavaBasePlugin> {
  extensions.configure<JavaPluginExtension> {
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(21))
    }
  }
}