FROM ubuntu:hirsute

COPY scripts/ scripts/
COPY version.properties version.properties
COPY docker/ docker/

RUN apt-get update -y
RUN apt-get install -y $(cat docker/dependencies.txt)

ENV ANDROID_SDK_ROOT "/usr/local/android-sdk-linux"
RUN test -d $ANDROID_SDK_ROOT || (mkdir -p "$ANDROID_SDK_ROOT" && echo "Created ANDROID_SDK_ROOT at $ANDROID_SDK_ROOT")

ENV NDK_VERSION $(scripts/./read-property.sh version.properties version.ndk)
ENV CMAKE_VERSION $(scripts/./read-property.sh version.properties version.cmake)
ENV ANDROID_NDK "$ANDROID_SDK_ROOT/ndk/$NDK_VERSION"

ENV PATH "$ANDROID_NDK/prebuilt/linux-x86_64/bin:$ANDROID_SDK_ROOT/cmake/$CMAKE_VERSION/bin:$ANDROID_NDK:$ANDROID_SDK_ROOT/tools/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
ENV TERM "xterm"

ENV CC "$(which clang-12)"
ENV CPP "$(which clang-cpp-12)"
ENV CXX "$(which clang++-12)"
ENV LD "$(which ld.lld-12)"

RUN scripts/./setup-sdk.sh