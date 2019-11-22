#!/bin/sh
cd `dirname $0`
if [ -d "./gradle-embed" ]; then
  echo copy dependencies jar
  sh ./gradle-embed/bin/gradle clean deps
fi

echo start...
cd src
sh ../bin/groovy $@ main.groovy