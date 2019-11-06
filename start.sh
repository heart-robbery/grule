#!/bin/sh
cd `dirname $0`
./gradle-embed/bin/gradle clean deps

echo start...
cd src 
../bin/groovy $@ main.groovy