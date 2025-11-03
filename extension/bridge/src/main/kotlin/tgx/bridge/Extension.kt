package tgx.bridge

import android.app.Application
import android.content.Context

open class Extension(@JvmField val name: String) {
  open fun createNewTokenRetriever(context: Context, googlePlayServicesAvailable: Boolean): DeviceTokenRetriever? =
    null
  open fun configure(application: Application, deviceTokenRetriever: DeviceTokenRetriever) { }
}