# ---------------------------------------------------------------------------
# SalaryTontine - raccourcis de developpement.
#
#   make dev    Postgres (Docker) + backend (Maven) + frontend (Vite)  <- dev
#   make up     pile Docker complete, prod-like                        <- demo
#   make help   liste toutes les cibles
# ---------------------------------------------------------------------------

SHELL := /bin/bash
.DEFAULT_GOAL := help

# Charge .env (DB_*, JWT_*, APP_*) et l'expose aux recettes.
# Note : une valeur ne doit pas contenir de '#', make le lirait comme un commentaire.
-include .env
export

DEV_DIR   := .dev
LOG_DIR   := $(DEV_DIR)/logs
BACK_PID  := $(DEV_DIR)/backend.pid
FRONT_PID := $(DEV_DIR)/frontend.pid

FRONT_URL := http://localhost:5173
API_URL   := http://localhost:8080

.PHONY: help dev up down stop status logs install \
        db db-wait back front back-wait \
        start-back start-front stop-back stop-front restart-back \
        test test-back test-front psql reset-db check-env

## ---------------------------------------------------------------- aide -----

help: ## Affiche cette aide
	@grep -hE '^[a-zA-Z_-]+:.*?## ' Makefile \
	  | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

check-env:
	@test -f .env || { echo "ERREUR: .env manquant -> cp .env.example .env"; exit 1; }

## ------------------------------------------------------ developpement -----

dev: check-env db ## Dev complet : Postgres + backend + frontend en arriere-plan
	@mkdir -p $(LOG_DIR)
	@$(MAKE) --no-print-directory start-back
	@$(MAKE) --no-print-directory start-front
	@echo ""
	@echo "  Frontend   $(FRONT_URL)"
	@echo "  API        $(API_URL)"
	@echo "  Swagger    $(API_URL)/swagger-ui.html"
	@echo ""
	@echo "  make logs           suivre les logs"
	@echo "  make restart-back   apres une modif Java (pas de hot reload backend)"
	@echo "  make stop           tout arreter"

db: check-env ## Demarre uniquement PostgreSQL (Docker)
	@docker compose up -d postgres
	@$(MAKE) --no-print-directory db-wait

db-wait:
	@printf "PostgreSQL"; \
	for i in $$(seq 1 30); do \
	  if [ "$$(docker inspect -f '{{.State.Health.Status}}' salarytontine-postgres 2>/dev/null)" = "healthy" ]; then \
	    echo " OK"; exit 0; \
	  fi; \
	  printf "."; sleep 1; \
	done; \
	echo " ECHEC (voir: docker compose logs postgres)"; exit 1

back: db ## Backend en premier plan, dans ce terminal (Ctrl+C pour arreter)
	cd backend && ./mvnw spring-boot:run

front: ## Frontend en premier plan, dans ce terminal (Ctrl+C pour arreter)
	cd frontend && npm run dev

install: ## Installe les dependances frontend
	cd frontend && npm install

restart-back: ## Redemarre le backend (a faire apres chaque modif Java)
	@$(MAKE) --no-print-directory stop-back
	@$(MAKE) --no-print-directory start-back

logs: ## Suit les logs du backend et du frontend
	@tail -f $(LOG_DIR)/backend.log $(LOG_DIR)/frontend.log

status: ## Etat des conteneurs Docker
	@docker compose ps

## ------------------------------------------------ demarrage interne -------

start-back:
	@mkdir -p $(LOG_DIR)
	@# La detection porte sur le port, pas sur le fichier PID : un backend lance
	@# hors Makefile occuperait 8080 sans qu'aucun PID ne soit enregistre, et le
	@# second demarrage echouerait silencieusement.
	@if [ -n "$$(lsof -ti tcp:8080 2>/dev/null)" ]; then \
	  echo "backend deja en ecoute sur 8080 (pid $$(lsof -ti tcp:8080 | tr '\n' ' ')) - make stop pour le remplacer"; \
	else \
	  echo "demarrage backend..."; \
	  ( cd backend && nohup ./mvnw spring-boot:run > ../$(LOG_DIR)/backend.log 2>&1 & echo $$! > ../$(BACK_PID) ); \
	  $(MAKE) --no-print-directory back-wait; \
	fi

back-wait:
	@printf "backend"; \
	for i in $$(seq 1 120); do \
	  if curl -sf $(API_URL)/actuator/health >/dev/null 2>&1; then echo " OK  $(API_URL)"; exit 0; fi; \
	  printf "."; sleep 1; \
	done; \
	echo " ECHEC (voir $(LOG_DIR)/backend.log)"; exit 1

start-front:
	@mkdir -p $(LOG_DIR)
	@if [ -n "$$(lsof -ti tcp:5173 2>/dev/null)" ]; then \
	  echo "frontend deja en ecoute sur 5173 - make stop pour le remplacer"; \
	else \
	  test -d frontend/node_modules || (cd frontend && npm install); \
	  echo "demarrage frontend..."; \
	  ( cd frontend && nohup npm run dev > ../$(LOG_DIR)/frontend.log 2>&1 & echo $$! > ../$(FRONT_PID) ); \
	  sleep 2; echo "frontend OK  $(FRONT_URL)"; \
	fi

## ------------------------------------------------------------ arret -------

stop: stop-back stop-front ## Arrete backend + frontend (Postgres reste demarre)

stop-back:
	@if [ -f $(BACK_PID) ]; then \
	  pid=$$(cat $(BACK_PID)); \
	  pkill -TERM -P $$pid 2>/dev/null || true; \
	  kill -TERM $$pid 2>/dev/null || true; \
	  rm -f $(BACK_PID); \
	fi; \
	pids=$$(lsof -ti tcp:8080 2>/dev/null); \
	if [ -n "$$pids" ]; then kill -TERM $$pids 2>/dev/null || true; fi; \
	for i in $$(seq 1 30); do \
	  [ -z "$$(lsof -ti tcp:8080 2>/dev/null)" ] && break; \
	  sleep 1; \
	done; \
	echo "backend arrete"

stop-front:
	@if [ -f $(FRONT_PID) ]; then \
	  pid=$$(cat $(FRONT_PID)); \
	  pkill -TERM -P $$pid 2>/dev/null || true; \
	  kill -TERM $$pid 2>/dev/null || true; \
	  rm -f $(FRONT_PID); \
	fi; \
	pids=$$(lsof -ti tcp:5173 2>/dev/null); \
	if [ -n "$$pids" ]; then kill -TERM $$pids 2>/dev/null || true; fi; \
	for i in $$(seq 1 20); do \
	  [ -z "$$(lsof -ti tcp:5173 2>/dev/null)" ] && break; \
	  sleep 1; \
	done; \
	echo "frontend arrete"

## ------------------------------------------------------ pile Docker -------

up: check-env ## Pile Docker complete (Postgres + backend + frontend)
	docker compose up --build -d
	@echo "  Frontend $(FRONT_URL)   API $(API_URL)"

down: ## Arrete la pile Docker (conserve les donnees)
	docker compose down

## ----------------------------------------------------------- tests --------

test: test-back test-front ## Lance tous les tests

test-back: ## Tests backend (JUnit + Testcontainers, Docker requis)
	cd backend && ./mvnw test

test-front: ## Tests frontend (Vitest)
	cd frontend && npm test

## -------------------------------------------------------- base de donnees -

psql: ## Ouvre un shell psql sur la base
	docker exec -it salarytontine-postgres psql -U $(DB_USERNAME) -d $(DB_NAME)

reset-db: ## DESTRUCTIF : supprime le volume Postgres, rejoue Flyway + le seed
	@echo "Cela supprime le volume salarytontine-postgres-data (toutes les donnees locales)."
	@read -p "Confirmer ? [y/N] " ans; [ "$$ans" = "y" ] || { echo "annule"; exit 1; }
	@$(MAKE) --no-print-directory stop
	docker compose down -v
	@$(MAKE) --no-print-directory db
