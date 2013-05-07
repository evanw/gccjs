#!/bin/sh
set -e

# Go to the directory containing this file
cd $(dirname $0)
DIR=$(pwd -L)
cd - > /dev/null

# Grab closure compiler if needed
if [ ! -e compiler.jar ]; then
  rm -fr temp
  mkdir temp
  cd temp
  curl -O http://closure-compiler.googlecode.com/files/compiler-latest.zip
  unzip compiler-latest.zip
  cd ..
  mv temp/compiler.jar .
  rm -fr temp
fi

# Run the Java compiler if needed
if [ ! -e ClosureCompilerBuilder.class ]; then
  javac -cp compiler.jar ClosureCompilerBuilder.java
fi
