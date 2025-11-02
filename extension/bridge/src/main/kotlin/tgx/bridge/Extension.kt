package tgx.bridge

import android.app.Application

open class Extension(@JvmField val name: String) {
  open fun configure(application: Application, googlePlayServicesAvailable: Boolean) { }
  open fun shouldDisableFirebaseMessaging(googlePlayServicesAvailable: Boolean): Boolean =
    false
}