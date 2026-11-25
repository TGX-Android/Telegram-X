# libprisma

set(PRISMA_DIR "${THIRDPARTY_DIR}/libprisma")
set(BOOST_REGEX_STANDALONE ON)

# libprisma takes Boost::regex from the parent project when it's defined
add_subdirectory("${THIRDPARTY_DIR}/boost-regex"
  EXCLUDE_FROM_ALL
)
add_subdirectory("${PRISMA_DIR}"
  EXCLUDE_FROM_ALL
)
# Boost.Regex reports invalid and too complex patterns with exceptions, libprisma catches them
target_compile_options(prisma PRIVATE
  -fexceptions
)
# Only sources of libprisma need C++20, other native code of the app stays on C++17
set_target_properties(prisma PROPERTIES
  INTERFACE_COMPILE_FEATURES ""
)

set(PRISMA_GRAMMARS "${PRISMA_DIR}/libprisma/grammars.dat")
