# ogg

set(OGG_DIR "${THIRDPARTY_DIR}/ogg")

add_library(ogg STATIC
  "${OGG_DIR}/src/bitwise.c"
  "${OGG_DIR}/src/framing.c"
)
target_include_directories(ogg PUBLIC
  "${OGG_DIR}/include"
)
target_compile_definitions(ogg PUBLIC
  __EMX__
)