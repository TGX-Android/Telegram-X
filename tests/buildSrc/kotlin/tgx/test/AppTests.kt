package tgx.test

import Keystore
import Sdk
import SdkVariant
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.TestExtension
import com.android.build.api.dsl.TestOptions
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.TestAndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.of
import org.gradle.kotlin.dsl.register
import tgx.gradle.source.KeystoreSource
import java.io.File
import java.util.Properties

private const val TEST_INSTRUMENTATION_RUNNER = "androidx.test.runner.AndroidJUnitRunner"

// Newer AndroidX test libraries require Android 7.0+
private const val TEST_MIN_SDK_VERSION = 24

const val TEST_DEVICE = "emulator"

internal const val SNAPSHOT_PATH = "tests/core/src/main/assets/snapshot.zip"

// -Ptgx.checks=false builds tests without checks of a change, e.g. to measure a commit before it,
// where they don't compile
internal val Project.hasChecks: Boolean
  get() = providers.gradleProperty("tgx.checks")
    .map {
      it != "false"
    }
    .getOrElse(true)

// Dry runs of microbenchmarks run each test once, without measuring
private val Project.isDryRun: Boolean
  get() = providers
    .gradleProperty("android.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable")
    .orNull == "true"

// Libraries of the app that tests inside app process depend on too
private val APP_LIBRARIES = listOf(
  mapOf("group" to "androidx.annotation"),
  mapOf("group" to "androidx.concurrent"),
  mapOf("group" to "androidx.lifecycle"),
  mapOf("group" to "androidx.tracing", "module" to "tracing"),
  mapOf("group" to "androidx.tracing", "module" to "tracing-ktx"),
  mapOf("group" to "com.google.auto.value"),
  mapOf("group" to "com.google.errorprone"),
  mapOf("group" to "com.google.guava"),
  mapOf("group" to "com.google.j2objc"),
  mapOf("group" to "com.squareup.okio"),
  mapOf("group" to "org.checkerframework"),
  mapOf("group" to "org.jetbrains"),
  mapOf("group" to "org.jetbrains.kotlin"),
  mapOf("group" to "org.jetbrains.kotlinx"),
  mapOf("group" to "org.jspecify")
)

@Suppress("UnstableApiUsage")
private fun TestOptions.addTestDevice() {
  managedDevices.localDevices.create(TEST_DEVICE) {
    device = "Pixel 6"
    apiLevel = 34 /*Android 14*/
    systemImageSource = "aosp"
    require64Bit = true
  }
}

fun Project.configureTests() {
  extensions.configure<ApplicationExtension> {
    // Built-in Kotlin compiles only Kotlin source directories
    sourceSets.getByName("test") {
      kotlin.directories += "../tests/unit/kotlin"
      if (hasChecks) {
        kotlin.directories += "../tests/unit/checks"
      }
    }
    sourceSets.getByName("androidTest") {
      kotlin.directories += "../tests/integration/kotlin"
      if (hasChecks) {
        kotlin.directories += "../tests/integration/checks"
      }
      if (hasTestTools) {
        kotlin.directories += "../tests/integration/tools"
      }
    }
    defaultConfig.testInstrumentationRunner = TEST_INSTRUMENTATION_RUNNER
    testOptions.addTestDevice()
  }
  extensions.configure<ApplicationAndroidComponentsExtension> {
    // Activity that microbenchmarks keep open inside app process, see MicroBenchmarkRunner
    onVariants(selector().withBuildType("nonMinifiedRelease")) { variant ->
      if (variant.minSdk.apiLevel >= TEST_MIN_SDK_VERSION) {
        val directory = "../tests/microbenchmark/app"
        variant.sources.kotlin!!.addStaticSourceDirectory("$directory/kotlin")
        variant.sources.manifests.addStaticManifestFile("$directory/AndroidManifest.xml")
      }
    }
    // Tools that UI tests start inside app process, e.g. fake notifications of NotificationFlood
    for (buildType in listOf("nonMinifiedRelease", "benchmarkRelease")) {
      onVariants(selector().withBuildType(buildType)) { variant ->
        if (variant.minSdk.apiLevel >= TEST_MIN_SDK_VERSION) {
          val directory = "../tests/ui/app"
          variant.sources.kotlin!!.addStaticSourceDirectory("$directory/kotlin")
          variant.sources.manifests.addStaticManifestFile("$directory/AndroidManifest.xml")
        }
      }
    }
  }
  runTestsOnDeviceOneAtATime()
}

/** @param testedBuildTypes When empty, build types are managed by baseline profile plugin */
@Suppress("UnstableApiUsage")
fun Project.configureAppTests(
  vararg testedBuildTypes: String,
  inAppProcess: Boolean = false,
  testedVariants: (SdkVariant) -> Boolean = { it.supportsSdk(TEST_MIN_SDK_VERSION) }
) {
  val snapshot = isolated.rootProject.projectDirectory.file(SNAPSHOT_PATH)
  val snapshotUserIds = providers.fileContents(snapshot).asBytes
    .map { readSnapshotUserIds(it.inputStream()) }
    .orNull
    .orEmpty()
  val appKeystore = if (inAppProcess) findAppKeystore() else null

  extensions.configure<TestExtension> {
    defaultConfig {
      minSdk = TEST_MIN_SDK_VERSION
      testInstrumentationRunner = TEST_INSTRUMENTATION_RUNNER
    }

    // Checks of a single change, which are ignored by git
    sourceSets.getByName("main") {
      if (hasChecks) {
        kotlin.directories += "checks"
      }
      if (hasTestTools) {
        kotlin.directories += "tools"
      }
    }

    targetProjectPath = ":app"
    // Tests that clear app data must not run inside app process
    experimentalProperties["android.experimental.self-instrumenting"] = !inAppProcess
    if (inAppProcess) {
      // Microbenchmarks fail when the app isn't fully compiled ahead of time,
      // benchmark plugin of AndroidX turns on the same. Dry runs measure nothing,
      // and the compilation takes about a minute before each run
      experimentalProperties["android.experimental.force-aot-compilation"] = !isDryRun
    }

    // Tests inside app process need the key of the app, which is debug key when keystore isn't set
    val testSigningConfig = appKeystore?.let { keystore ->
      signingConfigs.maybeCreate("app").apply {
        storeFile = keystore.file
        storePassword = keystore.password
        keyAlias = keystore.keyAlias
        keyPassword = keystore.keyPassword
      }
    } ?: signingConfigs["debug"]
    testedBuildTypes.forEach { buildType ->
      buildTypes.create(buildType) {
        isDebuggable = true
        signingConfig = testSigningConfig
        matchingFallbacks += "release"
      }
    }

    flavorDimensions += "SDK"
    Sdk.VARIANTS.values.filter(testedVariants).forEach { variant ->
      productFlavors.create(variant.flavor) {
        dimension = "SDK"
      }
    }

    testOptions.addTestDevice()
  }

  extensions.configure<TestAndroidComponentsExtension> {
    if (testedBuildTypes.isNotEmpty()) {
      beforeVariants { variantBuilder ->
        variantBuilder.enable = variantBuilder.buildType in testedBuildTypes
      }
    }
    onVariants { variant ->
      val artifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
      variant.instrumentationRunnerArguments.put(
        "app.id",
        variant.testedApks.map {
          artifactsLoader.load(it)?.applicationId
        }
      )
      if (snapshotUserIds.isNotEmpty()) {
        variant.instrumentationRunnerArguments.put(
          "snapshot.users",
          snapshotUserIds.joinToString(",")
        )
      }
    }
  }

  tasks.register<DescribeHeapProfilesTask>("describeHeapProfiles") {
    mustRunAfter(tasks.named { it.startsWith("connected") && it.endsWith("AndroidTest") })
    results.set(layout.buildDirectory.dir("outputs/connected_android_test_additional_output"))
    traceProcessor.set(providers.gradleProperty("traceProcessor"))
  }

  if (inAppProcess) {
    // App process loads classes of the test APK before classes of the app,
    // so their other versions would replace ones the app is built with
    configurations
      .named { it.endsWith("RuntimeClasspath") }
      .configureEach {
        APP_LIBRARIES.forEach {
          exclude(it)
        }
      }
  }

  runTestsOnDeviceOneAtATime()
}

// Same properties as in AppConfigurationSource
@Suppress("UnstableApiUsage")
private fun Project.findAppKeystore(): Keystore? {
  val root = isolated.rootProject.projectDirectory
  val properties = Properties()
  providers.fileContents(root.file("local.properties")).asText.orNull?.let {
    properties.load(it.reader())
  }
  val path = properties.getProperty("keystore.file")?.takeIf {
    it.isNotEmpty() && properties.getProperty("app.disable_signing")?.toBoolean() != true
  } ?: return null
  val keystore = providers.of(KeystoreSource::class) {
    parameters.properties.set(root.asFile.resolve(path))
  }
  return keystore.orNull
}

/**
 * Tests of different modules install the same app,
 * and accounts from snapshot.zip must not be used by several apps at once
 */
@Suppress("UnstableApiUsage")
private fun Project.runTestsOnDeviceOneAtATime() {
  val snapshotLock = gradle.sharedServices.registerIfAbsent(
    "snapshotLock",
    SnapshotLockService::class.java
  ) {
    maxParallelUsages.set(1)
    parameters.snapshot.set(isolated.rootProject.projectDirectory.file(SNAPSHOT_PATH))
    parameters.locksDirectory.set(File(gradle.gradleUserHomeDir, "tgx-test-accounts"))
  }
  val adb = extensions.getByType(AndroidComponentsExtension::class.java).sdkComponents.adb
  // Connected tests use every device connected to adb,
  // including emulator of managed device while it's set up or tested, so they run first
  val connectedTests = tasks.named {
    it.startsWith("connected") && it.endsWith("AndroidTest")
  }
  tasks
    .named { it.startsWith(TEST_DEVICE) }
    .configureEach {
      mustRunAfter(connectedTests)
    }
  val deviceTests = tasks.named {
    it.endsWith("AndroidTest") && (it.startsWith("connected") || it.startsWith(TEST_DEVICE))
  }
  deviceTests.configureEach {
    usesService(snapshotLock)
    doFirst {
      snapshotLock.get().lock()
      if (name.startsWith("connected")) {
        requireSingleDevice(adb.get().asFile)
      }
    }
  }
}
