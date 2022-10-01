# srtp

set(SRTP_DIR "${THIRDPARTY_DIR}/libsrtp")

# libsrtp version matches the one used in Chromium:
# https://chromium.googlesource.com/chromium/deps/libsrtp/+/5b7c744/LIBSRTP_VERSION
# TODO: extract commit hash dynamically instead of hardcoding current $(git rev-parse HEAD)
set(SRTP_COMMIT_HASH "860492290f7d1f25e2bd45da6471bfd4cd4d7add")

add_library(srtp STATIC
  "${SRTP_DIR}/crypto/cipher/aes_gcm_ossl.c"
  "${SRTP_DIR}/crypto/cipher/aes_icm_ossl.c"
  "${SRTP_DIR}/crypto/cipher/cipher.c"
  "${SRTP_DIR}/crypto/cipher/null_cipher.c"
  "${SRTP_DIR}/crypto/hash/auth.c"
  "${SRTP_DIR}/crypto/hash/hmac_ossl.c"
  "${SRTP_DIR}/crypto/hash/null_auth.c"
  "${SRTP_DIR}/crypto/kernel/alloc.c"
  "${SRTP_DIR}/crypto/kernel/crypto_kernel.c"
  "${SRTP_DIR}/crypto/kernel/err.c"
  "${SRTP_DIR}/crypto/kernel/key.c"
  "${SRTP_DIR}/crypto/math/datatypes.c"
  "${SRTP_DIR}/crypto/math/stat.c"
  "${SRTP_DIR}/crypto/replay/rdb.c"
  "${SRTP_DIR}/crypto/replay/rdbx.c"
  "${SRTP_DIR}/crypto/replay/ut_sim.c"
  "${SRTP_DIR}/srtp/ekt.c"
  "${SRTP_DIR}/srtp/srtp.c"
)
# config.h options match the ones used in Chromium:
# https://chromium.googlesource.com/chromium/deps/libsrtp/+/5b7c744/BUILD.gn
target_compile_definitions(srtp PRIVATE
  PACKAGE_VERSION="${SRTP_COMMIT_HASH}"
  PACKAGE_STRING="${SRTP_COMMIT_HASH}"

  HAVE_CONFIG_H
  OPENSSL
  GCM
  HAVE_STDLIB_H
  HAVE_STRING_H
  HAVE_STDINT_H
  HAVE_INTTYPES_H
  HAVE_INT16_T
  HAVE_INT32_T
  HAVE_INT8_T
  HAVE_UINT16_T
  HAVE_UINT32_T
  HAVE_UINT64_T
  HAVE_UINT8_T
  HAVE_ARPA_INET_H
  HAVE_SYS_TYPES_H
  HAVE_UNISTD_H

  HAVE_ARPA_INET_H
  HAVE_NETINET_IN_H
  HAVE_SYS_TYPES_H
  HAVE_UNISTD_H
  )
target_include_directories(srtp PRIVATE
  "${STUB_DIR}"
)
target_link_libraries(srtp PUBLIC
  usrsctp ssl
)
target_include_directories(srtp PUBLIC
  "${SRTP_DIR}/include"
  "${SRTP_DIR}/crypto/include"
)