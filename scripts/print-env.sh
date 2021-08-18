#!/bin/bash

echo '========= ENVIRONMENT ========='
clang --version | head -1
gcc --version | head -1
cmake --version | head -1
echo "ninja version $(ninja --version | head -1)"
java --version | head -1
wget --version | head -1
echo '========= ENVIRONMENT ========='