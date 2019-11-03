#!/bin/sh
cd `dirname $0`/src && ./gradle-5.6.2/bin/gradle clean deps && ../bin/groovy $@ main.groovy