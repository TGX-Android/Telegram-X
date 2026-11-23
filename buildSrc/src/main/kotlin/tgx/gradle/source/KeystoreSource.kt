package tgx.gradle.source

import Keystore
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import tgx.gradle.fatal
import tgx.gradle.task.isWindowsHost

abstract class KeystoreSource : ValueSource<Keystore, KeystoreSource.Params> {
  interface Params : ValueSourceParameters {
    val properties: RegularFileProperty
  }

  override fun obtain(): Keystore? {
    val properties = parameters.properties.orNull?.asFile ?: return null
    return if (properties.exists() && properties.isFile) {
      Keystore(properties).also {
        if (!it.file.exists() || !it.file.isFile) {
          fatal(
            "keystore.file in ${properties.absolutePath} must point to an existing keystore: ${it.file.absolutePath}" + if (isWindowsHost()) {
              "\nNote for Windows: you must use \\\\ or / as path separator."
            } else ""
          )
        }
      }
    } else {
      null
    }
  }
}