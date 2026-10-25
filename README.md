# [Telegram X](https://play.google.com/store/apps/details?id=org.thunderdog.challegram) — a slick experimental Telegram client based on [TDLib](https://core.telegram.org/tdlib).

![Telegram X](/images/feature.png)

This is the complete source code and the build instructions for the official alternative Android client for the Telegram messenger, based on the [Telegram API](https://core.telegram.org/api) and the [MTProto](https://core.telegram.org/mtproto) secure protocol via [TDLib](https://github.com/TGX-Android/tdlib).

* [**Telegram X** on Google Play](http://play.google.com/store/apps/details?id=org.thunderdog.challegram) ([subscribe to beta](https://play.google.com/apps/testing/org.thunderdog.challegram))
* [APKs and Build Info](https://t.me/tgx_log)
* [Bot to verify APK hash](https://t.me/tgx_bot)

<details>
<summary>Other sources</summary>

* [**Telegram X** on Huawei AppGallery](https://appgallery.huawei.com/app/C101754199)
* [**GitHub Releases**](https://github.com/TGX-Android/Telegram-X/releases)

</details>

## Build instructions

### Prerequisites

* Repository must be fetched via `git`
* **JDK** or **[Android Studio](https://developer.android.com/studio/)** (with compatible bundled JDK)
* At least **8GB** of RAM
* At least **7,32GB** of free disk space when cloning with `--shallow-submodules --depth=1`
* At least **2-5x** times more disk space for files generated during build process.

#### macOS

* [Homebrew](https://brew.sh)
* git with LFS: `$ brew install git git-lfs && git lfs install`
* JDK: `$ brew install openjdk@25`

#### Ubuntu

* git with LFS: `# apt install git git-lfs`
* Run `$ git lfs install` if you just installed `git-lfs`
* JDK: `# apt install openjdk-25-jdk`
* If multiple JDK versions are installed:<br/>`# update-java-alternatives --list`<br/>`# update-java-alternatives --set java-1.25.0-openjdk-amd64` (or other compatible version)

#### Windows

* [MSYS2](https://www.msys2.org/#installation) (**UCRT64** environment)
* Update packages: `pacman -Syu`
* Run `pacman -S --needed git mingw-w64-ucrt-x86_64-git-lfs perl make diffutils`
* Run `git lfs install`
* Run `git config --global core.longpaths true`
* Clone the repository
* Set `msys2.dir` in `local.properties` after cloning
* Note: you have to use `\\` or `/` as path separator in properties files

### Building

1. `$ git clone --recursive https://github.com/TGX-Android/Telegram-X tgx`
2. In case you forgot the `--recursive` flag, `cd` into `tgx` directory and run: `$ git submodule update --init --recursive`
3. Open project via **[Android Studio](https://developer.android.com/studio/)** or build manually from the command line: `./gradlew assembleLatestDebug`
4. If build fails, follow the instructions provided in the error message.

#### Publishing

1. [Obtain Telegram API credentials](https://core.telegram.org/api/obtaining_api_id)
2. Create `local.properties` file in the root project folder using any text editor:<br/><pre># Location where you have Android SDK installed
   sdk.dir=YOUR_ANDROID_SDK_FOLDER
   \# Telegram API credentials obtained at previous step
   telegram.api_id=YOUR_TELEGRAM_API_ID
   telegram.api_hash=YOUR_TELEGRAM_API_HASH</pre>
3. [Setup Firebase](https://firebase.google.com/docs/android/setup) and replace `google-services.json` with the one that's suitable for your `app.id`

### Commands

<details>
<summary>Reference of commands for building, testing and maintenance</summary>

`-Pabi` limits native libraries to given ABIs, separated by commas: `arm64`, `arm32`, `x64` or `x86`. Without it, all ABIs are built. `Latest` in task names can be replaced with another flavor, see [flavors](/tests/README.md#flavors).

#### Building

Set up Android SDK, NDK and `local.properties`, on Windows in MSYS2:
```shell
scripts/setup.sh
```

Check that submodules and Git LFS files are fetched:
```shell
./gradlew :app:validateGitSetup
```

Build and install debug build on connected device:
```shell
./gradlew :app:installLatestDebug -Pabi=arm64
```

Build `legacy` flavor for Android 4.x, which requires older NDK:
```shell
./gradlew :app:assembleLegacyDebug -PuseLegacyNdk=true -Pabi=arm32
```

Build libvpx and FFmpeg for all flavors and ABIs, add `-PuseLegacyNdk=true` for `legacy` flavor:
```shell
./gradlew :app:buildLibvpx
./gradlew :app:buildFfmpeg
```

#### Checking

Run unit tests:
```shell
./gradlew :app:testLatestDebugUnitTest
```

Run integration tests and UI tests on emulator:
```shell
./gradlew :app:emulatorLatestDebugAndroidTest -Pabi=arm64
./gradlew :tests:ui:emulatorLatestNonMinifiedReleaseAndroidTest -Pabi=arm64
```

Run one test on device, e.g. on connected one:
```shell
./gradlew :tests:ui:connectedLatestNonMinifiedReleaseAndroidTest -Pabi=arm64 -Pandroid.testInstrumentationRunnerArguments.class=tgx.test.UnauthorizedLaunchTest
```

Find classes added after minSdk of each flavor in declarations and, for Android 4.x, in imports, and calls to them without version checks:
```shell
./gradlew checkMinSdk
./gradlew checkMinSdk -PuseLegacyNdk=true
./gradlew :app:lintLegacyDebug -PuseLegacyNdk=true
```

Describe results of checks before and after a change for pull request, see [tests](/tests/README.md#tests-and-checks):
```shell
./gradlew clearCheckResults
# Run checks without the change
./gradlew saveCheckResults
# Run checks with the change
./gradlew describeCheckResults
```

Check test tools on connected test device after changing them, see [tests](/tests/README.md#running-tests):
```shell
scripts/check-test-tools.sh
```

#### Performance

Run benchmarks on connected device and compare results before and after a change:
```shell
./gradlew :tests:benchmark:connectedLatestBenchmarkReleaseAndroidTest -Pabi=arm64
./gradlew :tests:benchmark:saveBenchmarkResults
# Apply the change and run benchmarks again
./gradlew :tests:benchmark:compareBenchmarkResults -Pmetric=frameDurationCpuMsP90
```

Run microbenchmarks of app code and show implementations side by side:
```shell
./gradlew :tests:microbenchmark:connectedLatestNonMinifiedReleaseAndroidTest -Pabi=arm64 -Pandroid.testInstrumentationRunnerArguments.class=tgx.test.TextBenchmarks
./gradlew :tests:microbenchmark:describeBenchmarkResults
```

Optimize `Text`: save results before a change, then run microbenchmarks after it and compare, see [tests](/tests/README.md#optimizing-code):
```shell
./gradlew -q :tests:microbenchmark:connectedLatestNonMinifiedReleaseAndroidTest :tests:microbenchmark:saveBenchmarkResults -Pabi=arm64 -Pandroid.testInstrumentationRunnerArguments.class=tgx.test.TextSuite
# Change Text and run them again
./gradlew -q :tests:microbenchmark:connectedLatestNonMinifiedReleaseAndroidTest :tests:microbenchmark:compareBenchmarkResults -Pabi=arm64 -Pandroid.testInstrumentationRunnerArguments.class=tgx.test.TextSuite -Pimplementation=Text -Pcontrol=BoringOrStaticLayout
```

List devices and variants for which baseline profiles will be generated, without generating them, as tasks named `<device><Variant>AndroidTest`:
```shell
./gradlew :app:generateBaselineProfile -Pabi=arm64 -m | grep AndroidTest
```

Generate baseline profiles for all variants on emulator and connected device, keeping computer awake and saving log. ABIs must suit both devices: on x86_64 computers emulator is `x64`, unlike phones:
```shell
# macOS with Apple silicon
caffeinate -i ./gradlew :app:generateBaselineProfile -Pabi=arm64 --continue 2>&1 | tee ~/baseline-profile.log
# Ubuntu
systemd-inhibit --what=idle:sleep ./gradlew :app:generateBaselineProfile -Pabi=arm64,x64 --continue 2>&1 | tee ~/baseline-profile.log
```

</details>

## Reproducing public builds

In order to verify that there is no additional source code injected inside official APKs, you must use one of the following versions of **Ubuntu**:

* **21.04**: for builds published before [26th May 2023](https://github.com/TGX-Android/Telegram-X/commit/e9a054a0f469a98a13f7e0d751539687fef8759b)
* **22.04.2 LTS**: for builds published before 27th September 2025
* **24.04 LTS**: for builds published before 1st October 2026
* **26.04 LTS**: for any newer releases

And update its configuration:

1. Create user called `vk` with the home directory located at `/home/vk`
2. Clone `tgx` repository to `/home/vk/tgx`
3. Check out the specific commit you want to verify
4. In rare cases of builds that include unmerged pull requests, you must follow actions performed by [Publisher's](https://github.com/TGX-Android/Publisher/blob/main/main.js) `fetchPr` and `squashPr` tasks
5. `cd` into `tgx` folder and install dependencies: `# apt install $(cat reproducible-builds/dependencies.txt)`
6. Follow up the build instruction from the previous section
7. Run `$ apkanalyzer apk compare --different-only <remote-apk> <reproduced-apk>`
8. If only signature files and metadata differ, build reproduction is successful.

## Verifying side-loaded APKs

If you downloaded **Telegram X** APK from somewhere and would like to simply verify whether it's an original APK without any injected malicious source code, you need to get checksum (`SHA-256`, `SHA-1` or `MD5`) of the downloaded APK file and find whether it corresponds to any known **Telegram X** version.

In order to obtain **SHA-256** of the APK:

* `$ sha256sum <path-to-apk>` on **Ubuntu**
* `$ shasum -a 256 <path-to-apk>` on **macOS**
* `$ certutil -hashfile <path-to-apk> SHA256` on **Windows**

Once obtained, there are three ways to find out the commit for the specific checksum:

* Sending checksum to [`@tgx_bot`](https://t.me/tgx_bot)
* Searching for a checksum in [`@tgx_log`](https://t.me/tgx_log). You can do so without need in installing any Telegram client by using this URL format: [`https://t.me/s/tgx_log?q={checksum}`](https://t.me/s/tgx_log?q=c541ebb0a3ae7bb6e6bd155530f375d567b8aef1761fdd942fb5d69af62e24ae) (click to see in action). Note: unpublished builds cannot be verified this way.

## License

`Telegram X` is licensed under the terms of the GNU General Public License v3.0.

For more information, see [LICENSE](/LICENSE) file.

License of components and third-party dependencies it relies on might differ, check `LICENSE` file in the corresponding folder.

### Third-party dependencies

List of third-party components used in **Telegram X** can be found [here](/docs/THIRDPARTY.md). Additionally you can check the specific commit of the third-party component used, for example, [here](/app/jni/thirdparty) and [here](/thirdparty).

## Contributions

**Telegram X** welcomes contributions. Check out [pull request template](/docs/PULL_REQUEST_TEMPLATE.md), [code style](/docs/CODE_STYLE.md) and [guide for contributors](/docs/GUIDE.md) to learn more about Telegram X internals before creating the first pull request.

If you are a regular user and experience a problem with Telegram X, the best place to look for solution is [Telegram X chat](https://t.me/tgandroidtests) — a community with over 4 thousand members. Please do not use this repository to ask questions: if you have general issue with Telegram, refer to [FAQ](http://telegram.org/faq) or contact [Telegram Support](https://telegram.org/faq#telegram-support).
