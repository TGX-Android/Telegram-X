#!/bin/bash
set -e

EXO_DST=app/jni/thirdparty/exoplayer
EXO_SRC=thirdparty/ExoPlayer/extensions

test -d "$EXO_DST" || mkdir "$EXO_DST"

echo "Copying ExoPlayer files to $EXO_DST..."

cp -pf "$EXO_SRC/ffmpeg/src/main/jni/ffmpeg_jni.cc" "$EXO_DST"
cp -pf "$EXO_SRC/flac/src/main/jni/flac_jni.cc" "$EXO_DST"
cp -pf "$EXO_SRC/flac/src/main/jni/flac_parser.cc" "$EXO_DST"
# cp -pf "$EXO_SRC/flac/src/main/jni/flac_sources.mk" "$EXO_DST"
cp -pfr "$EXO_SRC/flac/src/main/jni/include" "$EXO_DST"
cp -pf "$EXO_SRC/opus/src/main/jni/opus_jni.cc" "$EXO_DST"
cp -pf "$EXO_SRC/vp9/src/main/jni/vpx_jni.cc" "$EXO_DST"

pushd $EXO_DST > /dev/null

test "$SED" || (echo "\$SED is not set!" && exit 1)

sed_rules=\
'$!N;s/^(#include <)android\/(log\.h>)/\1\2/g;'\
'$!N;s/^#define LOG_TAG "[^"]+"\n//g;'\
'$!N;s/^(#define A?LOGE\(\.\.\.\) (\\\n *)*\((\(void\))?)[a-zA-Z_]+\([^\\)]+(\\\n[^\\)]+)*\)/\1loge(TAG_NDK, __VA_ARGS__)/g;'\
'$!N;s/^(#define A?LOGV\(\.\.\.\) (\\\n *)*\((\(void\))?)[a-zA-Z_]+\([^\\)]+(\\\n[^\\)]+)*\)/\1logv(TAG_NDK, __VA_ARGS__)/g;'\
'$!N;s/^(#define LOG_ALWAYS_FATAL\(\.\.\.\) (\\\n *)*\((\(void\))?)[a-zA-Z_]+\([^\\)]+(\\\n[^\\)]+)*\)/\1loga(TAG_NDK, __VA_ARGS__)/g;'\
'$!N;s/(^jint JNI_OnLoad\(JavaVM *\* *[a-zA-Z0-9_]*, void *\* *[a-zA-Z0-9_]*\) \{\n(  [^\n]+\n)*\})/\/*\1*\//g;P;D'

files=( ffmpeg_jni.cc flac_jni.cc flac_parser.cc opus_jni.cc vpx_jni.cc )
for file in "${files[@]}"
do
  echo "Patching $file..."
  $SED -i".bak" -E "$sed_rules" "$file" && rm "$file.bak"
done

#echo "Patching flac_sources.mk..."
#$SED -i".bak" -E \
#'$!N;s/^( *flac_(jni|parser)\.cc *\\\n)+//g;'\
#'$!N;s/^( *)(flac\/src\/libFLAC)/\1thirdparty\/\2/g;P;D' \
#flac_sources.mk && rm flac_sources.mk.bak

popd > /dev/null

echo "ExoPlayer successfully patched!"