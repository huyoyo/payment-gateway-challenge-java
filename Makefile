# Payment Gateway local developer commands.
#
#   make verify   -> full build + unit + integration tests (no Docker)
#   make run      -> local Maven/Java flow: start bank simulator, wait, run jar
#   make up       -> build and run the Docker Compose gateway + bank stack
#   make down     -> stop the Docker Compose stack
#
# Override when needed:
#   make verify MVN_ARGS="-Dmaven.repo.local=.m2-repo"
#   make run BANK_URL=http://localhost:8080 WAIT_TIMEOUT=90

SHELL := /bin/sh

MVN ?= ./mvnw
MVN_ARGS ?=
BANK_URL ?= http://localhost:8080
GATEWAY_HEALTH_URL ?= http://localhost:8081/actuator/health/readiness
WAIT_TIMEOUT ?= 60
# Use the classic builder by default for reliable local builds.
# Set DOCKER_BUILDKIT=1 to opt into BuildKit.
DOCKER_BUILDKIT ?= 0

.DEFAULT_GOAL := help

.PHONY: help verify test package image up down run bank wait-bank stop logs clean

help:
	@printf '%s\n' \
	  'make verify   Run ./mvnw clean verify (unit + Failsafe IT)' \
	  'make test     Run unit/slice tests only' \
	  'make package  Build the executable jar without tests' \
	  'make run      Start bank simulator, wait, then run the jar locally' \
	  'make up       Build and start the Docker Compose stack' \
	  'make down     Stop the Docker Compose stack' \
	  'make stop     Alias for make down' \
	  'make logs     Follow gateway and bank simulator logs' \
	  'make clean    Remove Maven target directories'

verify:
	$(MVN) $(MVN_ARGS) clean verify

test:
	$(MVN) $(MVN_ARGS) test

package:
	$(MVN) $(MVN_ARGS) -DskipTests package

image:
	@DOCKER_BUILDKIT=$(DOCKER_BUILDKIT) docker compose build gateway

up: image bank wait-bank
	@docker compose up -d
	@command -v curl >/dev/null 2>&1 || { echo "curl is required to wait for the gateway" >&2; exit 1; }
	@printf 'Waiting for gateway at %s ' "$(GATEWAY_HEALTH_URL)"
	@i=0; \
	while [ $$i -lt $(WAIT_TIMEOUT) ]; do \
	  if curl -fsS --max-time 2 "$(GATEWAY_HEALTH_URL)" >/dev/null 2>&1; then \
	    echo "ready"; \
	    exit 0; \
	  fi; \
	  printf '.'; \
	  sleep 1; \
	  i=$$((i + 1)); \
	done; \
	echo; \
	echo "Timed out after $(WAIT_TIMEOUT)s waiting for $(GATEWAY_HEALTH_URL)." >&2; \
	docker compose ps >&2 || true; \
	exit 1

down:
	@docker compose down

bank:
	@docker compose up -d bank-simulator

wait-bank:
	@command -v curl >/dev/null 2>&1 || { echo "curl is required to wait for the bank simulator" >&2; exit 1; }
	@printf 'Waiting for bank simulator at %s ' "$(BANK_URL)"
	@i=0; \
	while [ $$i -lt $(WAIT_TIMEOUT) ]; do \
	  code=$$(curl -s -o /dev/null -w '%{http_code}' --max-time 1 "$(BANK_URL)/" 2>/dev/null || true); \
	  if [ "$$code" != "000" ] && [ -n "$$code" ]; then \
	    echo "ready (HTTP $$code)"; \
	    exit 0; \
	  fi; \
	  printf '.'; \
	  sleep 1; \
	  i=$$((i + 1)); \
	done; \
	echo; \
	echo "Timed out after $(WAIT_TIMEOUT)s waiting for $(BANK_URL)." >&2; \
	exit 1

run: bank wait-bank package
	@echo "Starting gateway on http://localhost:8090"
	@jar=$$(ls gateway-web/target/gateway-web-*.jar 2>/dev/null | head -1); \
	if [ -z "$$jar" ]; then \
	  echo "Gateway jar not found; run 'make package' first." >&2; \
	  exit 1; \
	fi; \
	exec java -jar "$$jar"

stop: down

logs:
	@docker compose logs -f gateway bank-simulator

clean:
	$(MVN) $(MVN_ARGS) clean
