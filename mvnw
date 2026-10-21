#!/bin/sh
# Maven Wrapper script for Unix/Linux/macOS
if [ -n "$MAVEN_HOME" ]; then
    exec "$MAVEN_HOME/bin/mvn" "$@"
else
    exec mvn "$@"
fi
