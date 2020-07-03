@cd %~dp0

@if not exist ./lib/ (
    @echo copy dependencies jar
    @if exist ./gradle-embed/ (
        @call ./gradle-embed/bin/gradle clean deps
    ) else (
        @call gradle clean deps
    )
)

@echo start ...
set JAVA_OPTS=-Dgroovy.attach.runtime.groovydoc=true %JAVA_OPTS% %*
cd src/
../bin/groovy main.groovy