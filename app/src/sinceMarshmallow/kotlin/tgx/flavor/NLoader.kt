@file:JvmName("NLoader")

package tgx.flavor

import androidx.tracing.trace
import org.thunderdog.challegram.BuildConfig
import org.thunderdog.challegram.N


fun collectLog(): String = ""

private fun loadLibrary(name: String) = trace("load:$name") {
  System.loadLibrary(name)
}

@Synchronized
fun loadLibraries() {
  if (BuildConfig.SHARED_STL) {
    loadLibrary("c++_shared")
  }
  loadLibrary("cryptox")
  loadLibrary("sslx")
  loadLibrary("tdjni")
  loadLibrary("leveldbjni")
  loadLibrary("tgcallsjni")
  loadLibrary("tgxjni")
  trace("setup") {
    N.setupLibraries()
  }
}
