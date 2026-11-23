# Telegram X (https://github.com/TGX-Android/Telegram-X), an official alternative Telegram client for Android.
#
# The image builds the app as README.md tells a developer to on Ubuntu: the debug build of the `latest` flavor for one
# ABI, x86_64, with its native libraries unstripped. No keystore, no secrets and no google_services.file:
# local.properties gets only the homes of the Android SDK and of rustup, the rest comes from local.properties.sample.
# The steps read the versions of the toolchain from the repository, so that the image follows the app:
# - packages: the names of reproducible-builds/dependencies.txt, the JDK of the Java toolchain of buildSrc;
# - the Android SDK, NDK and CMake: version.properties, read by scripts/read-property.sh;
# - Rust with the targets of the Android ABIs: scripts/setup-rust.sh, by version.rust.
# Ubuntu 26.04 is the one README.md names for reproducing new releases; the NDK has Linux hosts only for x86-64.
FROM --platform=linux/amd64 ubuntu:26.04
ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    ANDROID_HOME=/opt/android-sdk
ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin"

# scanner contract: the checkout lives inside the image, at /src
COPY . /src
WORKDIR /src

# The packages of reproducible-builds/dependencies.txt without their versions, which are those of an older Ubuntu, and
# with the JDK that the Java toolchain of the build asks for instead of the one listed there.
RUN java=$(sed -n 's/.*JavaLanguageVersion\.of(\([0-9][0-9]*\)).*/\1/p' \
      buildSrc/src/main/kotlin/java-toolchain-convention.gradle.kts | head -n 1) \
    && { [ -n "$java" ] || { echo "no Java toolchain version in buildSrc" >&2; exit 1; }; } \
    && packages=$(sed -e 's/[=[:space:]].*//' -e '/^openjdk-/d' -e '/^$/d' reproducible-builds/dependencies.txt) \
    && apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates "openjdk-$java-jdk-headless" $packages \
    && rm -rf /var/lib/apt/lists/*

# The command-line tools of version.cmdline_tools from Google over HTTPS, as scripts/setup-sdk.sh fetches them, then
# the packages of version.properties that this variant uses (setup-sdk.sh also installs the NDK of the legacy flavor).
RUN prop() { scripts/read-property.sh version.properties "$1"; } \
    && tools=$(prop version.cmdline_tools) \
    && platform=$(prop version.sdk_package) \
    && build_tools=$(prop version.build_tools) \
    && ndk=$(prop version.ndk_primary) \
    && cmake=$(prop version.cmake) \
    && wget -nv -O /tmp/tools.zip \
      "https://dl.google.com/android/repository/commandlinetools-linux-${tools}_latest.zip" \
    && mkdir -p "$ANDROID_HOME/cmdline-tools" \
    && unzip -q /tmp/tools.zip -d "$ANDROID_HOME/cmdline-tools" \
    && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
    && rm /tmp/tools.zip \
    && yes | sdkmanager --licenses > /dev/null \
    && { sdkmanager "platforms;$platform" "build-tools;$build_tools" "ndk;$ndk" "cmake;$cmake" > /tmp/sdkmanager.log \
         || { tail -n 40 /tmp/sdkmanager.log; exit 1; }; } \
    && rm /tmp/sdkmanager.log

# Rust of version.rust with the targets of the Android ABIs, by the script of the project (TERM: it prints in colors),
# then the default of rustup, so that cargo also works in a shell.
RUN TERM=xterm scripts/setup-rust.sh \
    && rust=$(scripts/read-property.sh version.properties version.rust) \
    && rustup default "$rust"

# The scanner clones without submodules. git-checkout of TGX-Android/git-tools fetches the pinned commit of each one,
# recursively and one commit deep, and with the profile of Telegram X leaves out what the build doesn't use, e.g. most
# of Chromium's third_party. Git LFS brings the prebuilt TDLib and OpenSSL of the tdlib submodule. A second run picks
# up a submodule that a network error left out.
RUN git lfs install --system \
    && git clone --quiet --depth 1 https://github.com/TGX-Android/git-tools /opt/git-tools \
    && git -C /opt/git-tools log -1 --format='git-tools %h %s' \
    && git config gitclone.profile tgx \
    && { /opt/git-tools/bin/git-checkout || /opt/git-tools/bin/git-checkout; }

# local.properties of a build from source: the homes of the SDK and of rustup and nothing else. So the app is the
# experimental org.example.tgx of local.properties.sample, signed by the debug key that the Android Gradle plugin
# generates, with pushes of Firebase off.
RUN rust_home=$(rustup show home) \
    && printf 'sdk.dir=%s\nrust.dir=%s\n' "$ANDROID_HOME" "$rust_home" > local.properties

# Debug, flavor latest, x86_64 only (-Pabi takes arm64, arm32, x64 or x86). CMake builds the native code of the app in
# its Debug configuration, with debug info, and app/build/intermediates keeps the libraries unstripped.
RUN ./gradlew --no-daemon --console=plain :app:assembleLatestDebug -Pabi=x64

# The scan has no network: every later build of Gradle is offline, and so is Cargo, which Gradle runs for tlottie.
# The machine of the scan has 8 GB, while gradle.properties gives Gradle and the Kotlin daemon 8 GB of heap each, so
# rebuilds there take the smaller heaps of /root/.gradle/gradle.properties, which wins over the project's.
RUN mkdir -p /root/.gradle/init.d /root/.cargo \
    && echo 'gradle.startParameter.offline = true' > /root/.gradle/init.d/offline.gradle \
    && printf '[net]\noffline = true\n' >> /root/.cargo/config.toml \
    && printf '%s\n' 'org.gradle.jvmargs=-Xmx3G -Dfile.encoding=UTF-8' 'kotlin.daemon.jvmargs=-Xmx2G' \
      > /root/.gradle/gradle.properties
