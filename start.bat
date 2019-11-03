@cd %~dp0

@echo copy dependencies jar
@call ./gradle-5.6.2/bin/gradle clean deps

@echo start ...
cd src/

@rem ../bin/groovy -Dprofile=dev main.groovy
../bin/groovy main.groovy