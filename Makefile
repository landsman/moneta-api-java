.DEFAULT_GOAL := help

.PHONY: help
help: ## show this help
	@awk 'BEGIN {FS = ":.*##"; print "usage: make <target>\n"} \
		/^##@/ { printf "\n\033[1m%s:\033[0m\n", substr($$0, 5); next } \
		/^[a-zA-Z0-9_.-]+:.*##/ { printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

##@ Build
.PHONY: build test lock format lint
build: ## check formatting, compile with error prone and nullaway, test and package the jar
	./gradlew build

test: ## run the tests
	./gradlew test

lock: ## rewrite gradle.lockfile after a dependency changes, the vulnerability scan reads it
	./gradlew dependencies --configuration runtimeClasspath --write-locks

##@ Code style
format: ## reformat java and gradle files in place
	./gradlew spotlessApply

lint: ## fail on formatting that make format would change
	./gradlew spotlessCheck
