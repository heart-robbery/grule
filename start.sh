#!/bin/sh
cd `dirname $0`
./gradle-5.6.2/bin/gradle clean deps

echo start...
cd src 
../bin/groovy $@ main.groovy
