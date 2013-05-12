#!/usr/bin/env python

# Need to use python instead of bash because OS X doesn't have realpath
import subprocess
import sys
import os

# Grab the directory containing this file
dir = os.path.dirname(os.path.realpath(__file__))

# Forward arguments to the builder
try:
  sys.exit(subprocess.call(['java', '-cp', dir + '/src:' + dir + '/compiler.jar', 'com.google.javascript.jscomp.ClosureCompilerBuilder'] + sys.argv[1:]))
except KeyboardInterrupt:
  sys.exit(1)
