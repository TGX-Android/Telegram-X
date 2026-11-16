plugins {
  id("java-toolchain-convention")
  id(libs.plugins.android.library.get().pluginId)
  id("tgx-module")
}

android {
  namespace = "tgx.stub.fingerprint"
}
