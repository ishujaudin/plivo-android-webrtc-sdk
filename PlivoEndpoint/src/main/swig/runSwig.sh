#!/bin/bash

# cd to directory of this script.
# Note: don't need to go back - by exit this script, terminal will remain in the directory it currently is.

cd "$(dirname "$0")"

# Define various files and directories for script to work with.

generated_src_base_dir="/Users/rahul/AndroidStudioProjects/testResip/app/src/main"
generated_cpp_dir="${generated_src_base_dir}/cpp/swig-generated"
echo "$generated_src_base_dir"
android_src_dir="${generated_src_base_dir}/cpp"

# Generated C++/JNI code goes into this file.
# This file is included into 'CMakeLists.txt'
generated_cpp_file="${generated_cpp_dir}/SwigAndroidGuide_wrap.cpp"

# Generated Java code goes into this directory.
generated_java_dir="${generated_src_base_dir}/java/com/example/testResip/swiggenerated"

# Swig required already existing output directories.
# Delete all existing generated code and re-create directories.

rm -rf ${generated_cpp_dir}
rm -rf ${generated_java_dir}

mkdir -p ${generated_cpp_dir}
mkdir -p ${generated_java_dir}

# Run 'swig' tool.
#
# '-I' = include directory.
# '-c++ -java' - languages to create interface.
# '-package' defines Java package for generated code 'com.goldberg.swigandroidguide.com.plivo.endpoint.swiggenerated'.
# '-o' - location of generated C++/JNI file.
# '-outdir' - location of generated Java code directory.
# 'SaveAndSpend.i' - script input file.
echo "swig -I${android_src_dir} -D__ANDROID__ -c++ -java -package com.plivo.endpoint.swiggenerated -o ${generated_cpp_file} -outdir ${generated_java_dir} SwigInterface.i"