# Patch vpx building with Windows style path
patch app/jni/thirdparty/libvpx/build/make/configure.sh < scripts/windows/win.vpx-configure.sh.patch
# Patch vpx building by spliting -rm arguments
patch app/jni/thirdparty/libvpx/build/make/Makefile < scripts/windows/win.vpx-Makefile.patch
echo "libvpx patched to build with Windows"