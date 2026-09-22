package tgx.gradle.source

import Keystore
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

abstract class KeystoreSource : ValueSource<Keystore, KeystoreSource.Params> {
  interface Params : ValueSourceParameters {
    val properties: RegularFileProperty
  }

  override fun obtain(): Keystore? {
    val properties = parameters.properties.orNull?.asFile ?: return null
    return if (properties.exists() && properties.isFile) {
      Keystore(properties)
    } else {
      null
    }
  }
}