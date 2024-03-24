#!/bin/bash
set -e

test "$SED" || (echo "\$SED is not set!" && exit 1)

DESTINATION_DIR="$THIRDPARTY_LIBRARIES/androidx-media"
SOURCE_DIR=thirdparty/androidx-media/libraries
SOURCE_FILES=(
  "${SOURCE_DIR}/decoder_flac/src/main/jni/include"
  "${SOURCE_DIR}/decoder_ffmpeg/src/main/jni/ffmpeg_jni.cc"
  "${SOURCE_DIR}/decoder_flac/src/main/jni/flac_jni.cc"
  "${SOURCE_DIR}/decoder_flac/src/main/jni/flac_parser.cc"
  "${SOURCE_DIR}/decoder_opus/src/main/jni/opus_jni.cc"
  "${SOURCE_DIR}/decoder_vp9/src/main/jni/vpx_jni.cc"
)

for SOURCE_FILE in ${SOURCE_FILES[@]}; do
  test -f "$SOURCE_FILE" || test -d "$SOURCE_FILE" || (echo "$SOURCE_FILE not found!" && exit 1)
done

test -d "$DESTINATION_DIR" || mkdir "$DESTINATION_DIR"

echo "Copying androidx-media files to ${DESTINATION_DIR}..."

for SOURCE_FILE in ${SOURCE_FILES[@]}; do
  cp -pfr "$SOURCE_FILE" "$DESTINATION_DIR"
done

pushd "$DESTINATION_DIR" > /dev/null

sed_rules=\
'$!N;s/^(#include <)android\/(log\.h>)/\1\2/g;'\
'$!N;s/^#define LOG_TAG "[^"]+"\n//g;'\
'$!N;s/^(#define A?LOGE\(\.\.\.\) (\\\n *)*\((\(void\))?)[a-zA-Z_]+\([^\\)]+(\\\n[^\\)]+)*\)/\1loge(TAG_NDK, __VA_ARGS__)/g;'\
'$!N;s/^(#define A?LOGV\(\.\.\.\) (\\\n *)*\((\(void\))?)[a-zA-Z_]+\([^\\)]+(\\\n[^\\)]+)*\)/\1logv(TAG_NDK, __VA_ARGS__)/g;'\
'$!N;s/^(#define A?LOGD\(\.\.\.\) (\\\n *)*\((\(void\))?)[a-zA-Z_]+\([^\\)]+(\\\n[^\\)]+)*\)/\1logd(TAG_NDK, __VA_ARGS__)/g;'\
'$!N;s/^(#define LOG_ALWAYS_FATAL\(\.\.\.\) (\\\n *)*\((\(void\))?)[a-zA-Z_]+\([^\\)]+(\\\n[^\\)]+)*\)/\1loga(TAG_NDK, __VA_ARGS__)/g;'\
'$!N;s/(^jint JNI_OnLoad\(JavaVM *\* *[a-zA-Z0-9_]*, void *\* *[a-zA-Z0-9_]*\) \{\n(  [^\n]+\n)*\})/\/*\1*\//g;P;D'

files=( ffmpeg_jni.cc flac_jni.cc flac_parser.cc opus_jni.cc vpx_jni.cc )
for file in "${files[@]}"
do
  echo "Patching $file..."
  $SED -i".bak" -E "$sed_rules" "$file" && rm "$file.bak"
done

popd > /dev/null

echo "androidx-media successfully patched!"