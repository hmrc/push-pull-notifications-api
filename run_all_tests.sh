#!/usr/bin/env bash
export SBT_OPTS="-XX:MaxMetaspaceSize=1G"
sbt clean compile coverage test it:test coverageReport
unset SBT_OPTS
