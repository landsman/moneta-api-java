.DEFAULT_GOAL := help

.PHONY: help
help: ## show this help
	@awk 'BEGIN {FS = ":.*##"; print "usage: make <target>\n"} \
		/^##@/ { printf "\n\033[1m%s:\033[0m\n", substr($$0, 5); next } \
		/^[a-zA-Z0-9_.-]+:.*##/ { printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

##@ Build
.PHONY: build test lock dev format lint
build: ## check formatting, compile with error prone and nullaway, test and package the jar; TESTS_JDK=21 tests on that JDK
	./gradlew build $(if $(TESTS_JDK),-PtestsJdk=$(TESTS_JDK))

test: ## run the tests
	./gradlew test

lock: ## rewrite modules/moneta-api/gradle.lockfile after a dependency changes, the vulnerability scan reads it
	./gradlew :modules:moneta-api:dependencies --configuration runtimeClasspath --write-locks

dev: ## run the example café app on :8080 against the account MONETA_TOKEN opens, see examples/cafe/README.md
	@test -n "$${MONETA_TOKEN:-}" || { echo 'MONETA_TOKEN is empty: run it as MONETA_TOKEN=op://<vault>/<item>/<field> op run -- make dev' >&2; exit 1; }
	./gradlew :examples:cafe:bootRun

##@ Code style
format: ## reformat java and gradle files in place
	./gradlew spotlessApply

lint: ## fail on formatting that make format would change
	./gradlew spotlessCheck
