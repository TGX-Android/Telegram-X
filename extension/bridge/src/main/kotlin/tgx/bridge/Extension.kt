package tgx.bridge

import android.app.Application
import android.content.Context

open class Extension(@JvmField val name: String) {
  fun isNotEmpty(): Boolean =
    name != "none"

  open fun createNewTokenRetriever(context: Context): DeviceTokenRetriever? =
    null
  open fun configure(application: Application, deviceTokenRetriever: DeviceTokenRetriever) { }
}