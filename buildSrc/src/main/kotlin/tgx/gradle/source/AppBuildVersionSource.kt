package tgx.gradle.source

import BuildVersions
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import tgx.gradle.loadProperties

abstract class AppBuildVersionSource : ValueSource<BuildVersions, AppBuildVersionSource.Params> {
  interface Params : ValueSourceParameters {
    val version: RegularFileProperty
  }

  override fun obtain(): BuildVersions {
    val version = loadProperties(parameters.version.get().asFile)
    return BuildVersions(version)
  }
}