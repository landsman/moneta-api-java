.DEFAULT_GOAL := help

.PHONY: help
help: ## show this help
	@awk 'BEGIN {FS = ":.*##"; print "usage: make <target>\n"} \
		/^##@/ { printf "\n\033[1m%s:\033[0m\n", substr($$0, 5); next } \
		/^[a-zA-Z0-9_.-]+:.*##/ { printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

##@ Build
.PHONY: build test lock dev release publish publish-local format lint scripts-test
build: ## check formatting, compile with error prone and nullaway, test, check declared dependencies and package the jar; TESTS_JDK=21 tests on that JDK
	./gradlew build buildHealth $(if $(TESTS_JDK),-PtestsJdk=$(TESTS_JDK))

test: ## run the tests
	./gradlew test

lock: ## rewrite modules/moneta-api/gradle.lockfile after a dependency changes, the vulnerability scan reads it
	./gradlew :modules:moneta-api:dependencies --configuration runtimeClasspath --write-locks

dev: ## run the example café app on :8080 against the account MONETA_TOKEN opens, see examples/cafe/README.md
	@test -n "$${MONETA_TOKEN:-}" || { echo 'MONETA_TOKEN is empty: run it as MONETA_TOKEN=op://<vault>/<item>/<field> op run -- make dev' >&2; exit 1; }
	./gradlew :examples:cafe:bootRun

release: ## tag origin/main, asking which version, wait for release.yml to publish it, write the release page; TAG=v0.1.0 answers
	./bin/release.sh

publish: ## build and publish both jars to GitHub Packages, as the release workflow does; RELEASE_VERSION=v0.1.0 releases
	./gradlew build publish $(if $(RELEASE_VERSION),-PreleaseVersion=$(RELEASE_VERSION:v%=%))

publish-local: ## install both jars into ~/.m2, to try them in another project without a token
	./gradlew publishToMavenLocal

##@ Trying the live API
# The token is read from 1Password for one run by op run, which hands it to that process only and masks it in the
# output. Nothing stores it: no file, and not Gradle, which only builds the tool. Point MONETA_OP at your own item.
MONETA_OP ?= op://dev-homelab/moneta money bank - api token/credential
PEEK = ./gradlew -q :tools:peek:installDist && MONETA_TOKEN="$(MONETA_OP)" op run -- tools/peek/build/install/peek/bin/peek

.PHONY: peek peek-accounts peek-balance record-fixture
peek: ## print the account's movements as MONETA sends them, people masked; FROM=2026-11-01 TO=2026-11-07, SHAPE=1
	@[ -n "$(FROM)" ] || [ -z "$(TO)" ] || { echo 'FROM and TO go together; neither is the last week' >&2; exit 1; }
	$(PEEK) transactions $(FROM) $(TO) $(if $(SHAPE),--shape)

peek-accounts: ## print the accounts the token opens
	$(PEEK) accounts

peek-balance: ## print each account's booked and available balance
	$(PEEK) balance

record-fixture: ## save movement N of make peek FROM= TO=, the same dates, as …/moneta/live/NAME.json, people replaced
	@[ -n "$(N)" ] && [ -n "$(NAME)" ] && [ -n "$(FROM)" ] && [ -n "$(TO)" ] || { echo 'make record-fixture N=3 NAME=booked-card FROM=… TO=…, as make peek FROM=… TO=… numbered it' >&2; exit 1; }
	$(PEEK) record "$(N)" "$(NAME)" $(FROM) $(TO)

##@ Code style
format: ## reformat java and gradle files in place
	./gradlew spotlessApply

lint: ## fail on formatting that make format would change
	./gradlew spotlessCheck

scripts-test: ## shellcheck every script, run every *.test.sh
	mise exec shellcheck -- shellcheck $$(git ls-files '*.sh')
	@rc=0; for t in $$(git ls-files '*.test.sh'); do echo "== $$t"; "./$$t" || { echo "FAIL $$t"; rc=1; }; done; exit $$rc
