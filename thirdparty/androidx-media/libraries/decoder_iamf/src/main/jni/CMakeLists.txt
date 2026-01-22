#
# Copyright 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

cmake_minimum_required(VERSION 3.21.0 FATAL_ERROR)

# Enable C++11 features.
set(CMAKE_CXX_STANDARD 11)

# Define project name for your JNI module
project(libiamfJNI C CXX)

set(libiamf_jni_root "${CMAKE_CURRENT_SOURCE_DIR}")

# Build libiamf.
add_subdirectory("${libiamf_jni_root}/libiamf/code"
                 EXCLUDE_FROM_ALL)

# Add the include directory from libiamf.
include_directories ("${libiamf_jni_root}/libiamf/code/include")

# Build libiamfJNI.
add_library(iamfJNI
            SHARED
            iamf_jni.cc)

# Locate NDK log library.
find_library(android_log_lib log)

# Link libiamfJNI against used libraries.
target_link_libraries(iamfJNI
                      PRIVATE android
                      PRIVATE iamf
                      PRIVATE ${android_log_lib})

# Enable 16 KB ELF alignment.
target_link_options(iamfJNI
                    PRIVATE "-Wl,-z,max-page-size=16384")
