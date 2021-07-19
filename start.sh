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

# 不写两行的话, docker里面执行的时候多个空格分割的参数会报错
JAVA_OPTS="-Dgroovy.attach.runtime.groovydoc=true $@"
export JAVA_OPTS

cd src
sh ../bin/groovy -pa main.groovy

# nohup sh start.sh -Xms512m -Xmx512m [-Dprofile=pro] -Dlog.totalSizeCap=50GB > /dev/null 2>&1 &