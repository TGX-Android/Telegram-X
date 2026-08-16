// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.androidx.baselineprofile) apply false
}

allprojects {
  plugins.withType<JavaBasePlugin> {
    extensions.configure<JavaPluginExtension> {
      toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
      }
    }
  }
}