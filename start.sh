#!/bin/sh
cd `dirname $0`
if [ ! -d "./lib" ]; then
  echo copy dependencies jar
  if [ -d "./gradle-embed" ]; then
    sh ./gradle-embed/bin/gradle clean deps
  else
    gradle clean deps
  fi
fi

echo start...
cd src
sh ../bin/groovy $@ main.groovy

# nohup sh start.sh -Xms512m -Xmx512m [-Dprofile=pro] > /dev/null 2>&1 &