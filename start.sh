#!/bin/sh
cd `dirname $0`
if [ -d "./gradle-embed" ] && [ ! -d "./lib" ]; then
  echo copy dependencies jar
  sh ./gradle-embed/bin/gradle clean deps
fi

echo start...
cd src
sh ../bin/groovy $@ main.groovy


# sh start.sh -Dlog.path="`pwd`/log" > /dev/null 2>&1 &