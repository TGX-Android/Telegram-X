// Top-level build file where you can add configuration options common to all sub-projects/modules.

import tgx.test.configureCheckResults
import tgx.test.configureTestToolsCheck

plugins {
  id("java-toolchain-convention")
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.androidx.baselineprofile) apply false
}

configureCheckResults()
configureTestToolsCheck()
