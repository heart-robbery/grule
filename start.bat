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
cd src/
../bin/groovy %* main.groovy