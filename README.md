# SalaryTontine

## 1. Présentation

Une entreprise fictive possède plusieurs employés, chacun doté d'un salaire de base fictif.
Le **comptable** crée une **tontine** : un groupe de participants qui cotisent chacun le même
montant tous les mois. Chaque mois, un seul participant reçoit la totalité de la cagnotte.

Les employés ne sont pas enrôlés d'office : ils consultent les tontines ouvertes et demandent
à rejoindre celle qui les intéresse ; le comptable arbitre.

SalaryTontine simule l'effet de ce mécanisme sur le salaire mensuel de chaque participant.

## 2. Fonctionnement

### La formule

```
finalSalary = baseSalary - tontineDeduction + tontineReceived
```

### La cagnotte

```
potAmount = monthlyAmount × nombre de participants
```

### Exemple

5 employés cotisent 50 000 FCFA par mois. La cagnotte mensuelle vaut 250 000 FCFA.

| Participant | Salaire de base | Cotisation | Tontine reçue | Salaire simulé |
|---|---:|---:|---:|---:|
| Awa (bénéficiaire du mois) | 500 000 | −50 000 | +250 000 | **700 000** |
| Fatou | 500 000 | −50 000 | 0 | **450 000** |
| Mamadou | 500 000 | −50 000 | 0 | **450 000** |
| Khady | 500 000 | −50 000 | 0 | **450 000** |
| Aliou | 500 000 | −50 000 | 0 | **450 000** |

La masse salariale totale est conservée : la tontine redistribue, elle ne crée rien.

### L'ordre de passage

Le cycle dure exactement autant de mois qu'il y a de participants. Le premier mois revient au
participant dont le `turnOrder` vaut 1, le deuxième au `turnOrder` 2, et ainsi de suite.

| Mois | Bénéficiaire | Ordre |
|---|---|---:|
| 2026-08 | Awa Ndiaye | 1 |
| 2026-09 | Fatou Fall | 2 |
| 2026-10 | Mamadou Diop | 3 |
| 2026-11 | Khady Sarr | 4 |
| 2026-12 | Aliou Ba | 5 |

Après le dernier tour, la tontine passe automatiquement au statut `COMPLETED`.

### La cadence

La durée d'un tour n'est pas forcément le mois. Une tontine peut tourner :

| Cadence | Durée d'un tour |
|---|---|
| `MONTHLY` | un mois calendaire |
| `BIWEEKLY` | 14 jours |
| `TEN_DAYS` | 10 jours |
| `WEEKLY` | 7 jours |
| `CUSTOM` | une durée libre, de 1 à 365 jours |

L'unité du cycle est donc le **tour**, numéroté de 1 à n, et non le mois.

Mais un salaire, lui, reste mensuel : « le salaire final de la semaine » n'a aucun sens. Les
deux rythmes sont séparés — le cycle tourne à sa cadence, le **bulletin de paie reste mensuel**
et agrège tout ce qui tombe dans le mois : plusieurs retenues d'une même tontine infra-mensuelle,
et les retenues de toutes les autres.

> ⚠️ Une cotisation hebdomadaire de 50 000 ne coûte pas 50 000 par mois mais **217 410**, soit
> 4,35 prélèvements en moyenne. C'est ce **coût mensuel moyen**, et non la cotisation brute, qui
> est comparé au salaire par la règle de plafond.

### L'adhésion

Un employé n'est pas enrôlé d'office : il choisit la tontine qu'il veut rejoindre.

1. Le comptable crée une tontine. Elle naît au statut `DRAFT`, **ouverte aux inscriptions**.
2. Tout employé voit les tontines ouvertes et demande à rejoindre celle qui l'intéresse.
3. Le comptable accepte ou refuse. L'acceptation seule crée le participant et lui attribue son
   ordre de passage.
4. L'activation **gèle la composition** : la tontine n'accepte plus aucune demande.

Cette fermeture n'est pas une commodité technique, c'est une règle d'équité. Le nombre de
participants fixe à la fois la cagnotte et la durée du cycle. Admettre quelqu'un au troisième
mois changerait la cagnotte de ceux qui ont déjà encaissé, et personne ne recevrait plus
exactement ce qu'il a versé.

Une demande en attente ne compte donc **ni dans la cagnotte, ni dans la durée du cycle, ni
dans les cotisations**. Elle vit dans sa propre table, `tontine_join_requests`, distincte de
`tontine_members` qui ne contient que les participants acceptés.

### Plusieurs tontines à la fois

Un employé peut cotiser à plusieurs tontines simultanément, **tant que son salaire le
supporte** : la somme de ses cotisations mensuelles ne peut pas dépasser son salaire de base.
On ne prélève pas plus que ce qui est versé. Un employé dont le salaire n'est pas renseigné ne
peut donc participer à aucune tontine.

La règle est vérifiée à trois moments : quand l'employé demande à rejoindre, quand le comptable
l'accepte, et à l'activation de la tontine — car il a pu s'engager ailleurs entre-temps.

Conséquence sur le calcul : **le salaire final appartient au mois, pas à une tontine.** Chaque
tontine produit sa ligne de détail, mais le résultat est consolidé :

```
finalSalary = baseSalary − Σ cotisations du mois + Σ cagnottes reçues dans le mois
```

`GET /api/salaries/me/{month}` renvoie ce bulletin consolidé et le détail ligne à ligne.

### Quitter une tontine

| Statut | Départ possible ? |
|---|---|
| `DRAFT` | **Oui**, librement. Rien n'a été versé ni reçu. Les ordres de passage des participants restants sont renumérotés de 1 à n. |
| `ACTIVE` | **Non.** Le cycle est un engagement. |
| `COMPLETED` / `CANCELLED` | Sans objet, le cycle est clos. |

Le refus sur une tontine active n'est pas une limitation technique, c'est la nature d'une
tontine. Deux cas, tous deux inacceptables :

- **Le participant a déjà reçu la cagnotte.** Il a encaissé beaucoup et n'a versé qu'une
  fraction. Partir reviendrait à conserver l'argent des autres.
- **Il n'a pas encore reçu.** Partir lui ferait perdre ce qu'il a versé, réduirait la cagnotte
  de tous les autres et changerait la durée du cycle en cours.

L'application affiche le message correspondant à sa situation plutôt qu'un refus sec. La seule
issue sur un cycle engagé est l'**annulation de la tontine entière** par le comptable, qui
arrête le cycle sans effacer l'historique salarial déjà produit.

### La fin du cycle

La durée n'est pas libre : elle vaut **un mois par participant**. Une date de fin choisie
indépendamment du nombre de participants serait contradictoire.

Le comptable déclare donc un **nombre de places** à la création. La fin du cycle en découle et
peut être annoncée dès le départ : les employés voient « 3 / 5 places, fin prévue en décembre
2026 » avant même de s'inscrire. Tant que la tontine est ouverte, cette fin reste prévisionnelle ;
l'activation aligne le nombre de places sur la composition réelle et la rend définitive.

**Le backend est la seule source de vérité des calculs.** Le frontend n'envoie jamais un
montant de cotisation, de cagnotte ou de salaire final : il les affiche uniquement.

---

## 3. Stack technique

| Couche | Technologies |
|---|---|
| Backend | Java 21, Spring Boot 3.4, Maven, Spring Web, Spring Data JPA / Hibernate 6, Spring Security, JWT (jjwt 0.12, HMAC-SHA), Jakarta Bean Validation, Flyway |
| Base de données | PostgreSQL 16 |
| Documentation API | springdoc-openapi 2.8 (Swagger UI) |
| Tests backend | JUnit 5, Mockito, AssertJ, Spring Security Test, MockMvc, Testcontainers PostgreSQL |
| Frontend | React 19, TypeScript 5.7, Vite 6, React Router 7, Axios |
| Tests frontend | Vitest 3, React Testing Library, jsdom |
| Conteneurisation | Docker, Docker Compose, Nginx (service des fichiers statiques) |

---

## 4. Architecture

```
                  Navigateur
                      │
                      │  cookie HttpOnly (JWT)
                      ▼
        ┌─────────────────────────────┐
        │  React 19 + TypeScript      │   port 5173
        │  Vite / React Router / Axios│
        └──────────────┬──────────────┘
                       │  REST / JSON
                       ▼
        ┌─────────────────────────────┐
        │  Spring Boot REST API       │   port 8080
        │  Controller → Service → Repo│
        │  Spring Security + JWT      │
        └──────────────┬──────────────┘
                       │  JDBC
                       ▼
        ┌─────────────────────────────┐
        │  PostgreSQL 16              │   port 5432
        │  Schéma géré par Flyway     │
        └─────────────────────────────┘
```

### Responsabilités backend

| Couche | Rôle |
|---|---|
| `controller` | HTTP uniquement : validation d'entrée, codes de statut, délégation |
| `dto/request`, `dto/response` | Contrats d'entrée et de sortie, distincts des entités JPA |
| `service` | Logique métier et transactions |
| `repository` | Persistance (Spring Data JPA) |
| `entity` | Modèle JPA |
| `mapper` | Conversion entité ↔ DTO |
| `security` | Authentification, autorisation, identité de l'appelant |
| `exception` | Exceptions métier et traduction centralisée en réponses HTTP |
| `config` | Sécurité, OpenAPI, Jackson, propriétés d'environnement, seed |

Aucune entité JPA n'est exposée directement par l'API. Le hash de mot de passe n'apparaît
dans aucune réponse.

---

## 5. Installation locale

### Le plus rapide

Un `Makefile` regroupe les commandes courantes :

```bash
make dev     # PostgreSQL (Docker) + backend (Maven) + frontend (Vite), rechargement a chaud
make up      # pile Docker complete, prod-like
make help    # liste toutes les cibles
```

`make dev` est le mode de développement : une modification du frontend est rechargée
instantanément, une modification Java demande `make restart-back`. Les sections suivantes
détaillent ce que ces cibles font, pour qui préfère lancer chaque étape à la main.

### Prérequis

- Java 21
- Maven 3.9+ (un wrapper `./mvnw` est fourni)
- Node.js 20+ et npm
- Docker (pour PostgreSQL et pour les tests d'intégration Testcontainers)

### Étape 1 — Configuration

```bash
cp .env.example .env
```

Puis renseignez au minimum `DB_PASSWORD` et `JWT_SECRET` dans `.env`.

Générer un secret JWT robuste :

```bash
openssl rand -base64 48
```

### Le traitement mensuel automatique

Une tâche planifiée génère les cotisations puis les salaires de toutes les tontines actives.
Elle passe **tous les jours** et traite **tous les tours échus** non encore générés : un serveur
arrêté trois semaines rattrape ainsi les trois tours manqués, quelle que soit la cadence. Un
tour déjà traité est ignoré, le passage quotidien est donc sans effet le reste du temps.

| Variable | Rôle | Défaut |
|---|---|---|
| `APP_SCHEDULING_ENABLED` | Active le traitement automatique | `true` |
| `APP_MONTHLY_RUN_CRON` | Expression cron Spring, en UTC | `0 0 2 * * *` |

Le déclenchement manuel reste disponible dans l'interface, sur le détail d'une tontine active.
Il sert aux démonstrations — un cycle de cinq participants dure cinq mois réels — et au
rattrapage d'un mois passé.

Les traces produites par la tâche apparaissent dans le journal d'audit sous l'auteur
**« Système »**, puisqu'aucun utilisateur n'est à l'origine de l'action.

### Étape 2 — Base de données

```bash
docker run -d --name st-postgres \
  -e POSTGRES_DB=salarytontine \
  -e POSTGRES_USER=salarytontine \
  -e POSTGRES_PASSWORD=<votre_mot_de_passe> \
  -p 5432:5432 postgres:16-alpine
```

Flyway crée et migre le schéma automatiquement au démarrage du backend.

### Étape 3 — Backend

```bash
cd backend
set -a; source ../.env; set +a     # charge les variables d'environnement
./mvnw spring-boot:run
```

L'API écoute sur <http://localhost:8080>.

### Étape 4 — Frontend

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

L'interface est disponible sur <http://localhost:5173>.

---

## 6. Variables d'environnement

Toutes les variables sont documentées dans `.env.example`. **Ce fichier ne contient aucune
valeur secrète réelle** et `.env` est ignoré par Git.

| Variable | Rôle | Défaut |
|---|---|---|
| `DB_HOST` | Hôte PostgreSQL | `localhost` |
| `DB_PORT` | Port PostgreSQL | `5432` |
| `DB_NAME` | Nom de la base | `salarytontine` |
| `DB_USERNAME` | Utilisateur PostgreSQL | `salarytontine` |
| `DB_PASSWORD` | Mot de passe PostgreSQL | — *(obligatoire)* |
| `JWT_SECRET` | Clé de signature HMAC-SHA, **32 caractères minimum** | — *(obligatoire)* |
| `JWT_EXPIRATION_SECONDS` | Durée de validité du jeton | `3600` |
| `JWT_COOKIE_SECURE` | `true` uniquement derrière HTTPS | `false` |
| `APP_FRONTEND_URL` | Origine autorisée par CORS | `http://localhost:5173` |
| `APP_SEED_ENABLED` | Active le jeu de données de démonstration | `false` |
| `APP_SEED_PASSWORD` | Mot de passe des comptes de démonstration (8 caractères minimum) | — |
| `VITE_API_BASE_URL` | URL de l'API, figée au build du frontend | `http://localhost:8080` |

Le backend **refuse de démarrer** si `JWT_SECRET` est absent ou trop court. Aucun secret
n'est écrit en dur dans le code source.

---

## 7. Docker

Lancer la pile complète (PostgreSQL + backend + frontend) :

```bash
cp .env.example .env      # puis renseigner DB_PASSWORD et JWT_SECRET
docker compose up --build # ou : make up
```

| Service | URL |
|---|---|
| Frontend | <http://localhost:5173> |
| Backend | <http://localhost:8080> |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| PostgreSQL | `localhost:5432` |

Le backend attend que PostgreSQL soit sain (`healthcheck` `pg_isready`) avant de démarrer, et
le frontend attend que le backend réponde sur `/actuator/health`. Les données PostgreSQL
persistent dans le volume `salarytontine-postgres-data`.

Arrêter la pile :

```bash
docker compose down            # conserve les données
docker compose down -v         # supprime aussi le volume PostgreSQL
```

---

## 8. Tests

### Backend

```bash
cd backend
./mvnw test                    # tests unitaires + tests d'intégration
./mvnw test -Dtest='*ServiceTest,*CalculatorTest'   # unitaires seuls
./mvnw test -Dtest='*IntegrationTest'               # intégration seuls
```

Les tests d'intégration démarrent un **vrai PostgreSQL** via Testcontainers : Docker doit être
en cours d'exécution. H2 n'est volontairement pas utilisé, afin de tester le même moteur que
celui de production (contraintes `CHECK`, regex, types `NUMERIC`).

> **Note Docker récent** — les daemons Docker Engine 25+ refusent la version d'API négociée par
> défaut par le client `docker-java` embarqué dans Testcontainers. Le projet fixe donc
> `api.version=1.44` dans la configuration Surefire. Sur un daemon plus ancien, surcharger :
> `./mvnw test -Ddocker.api.version=1.43`.

### Frontend

```bash
cd frontend
npm test                       # exécution unique
npm run test:watch             # mode veille
npm run lint                   # vérification TypeScript stricte
```

---

## 9. Swagger / OpenAPI

Backend démarré :

- Swagger UI : <http://localhost:8080/swagger-ui.html>
- Schéma OpenAPI : <http://localhost:8080/v3/api-docs>

La documentation décrit tous les endpoints, les DTO et le schéma de sécurité par cookie.

---

## 10. Comptes

### Le compte administrateur

Il est créé **au démarrage, si et seulement si la base ne contient aucun `ADMIN`**, à partir de
l'environnement :

```bash
APP_ADMIN_EMAIL=admin@example.test
APP_ADMIN_PASSWORD=<12 caractères minimum>
```

Sans ce compte, une base vierge n'offrirait aucun moyen d'entrer : l'inscription publique ne
crée que des `EMPLOYEE`, et il faut déjà être `ADMIN` pour attribuer un rôle.

Aucun identifiant n'est écrit en dur dans le code. Le mot de passe n'est jamais journalisé, et
seul son hachage BCrypt est stocké. `.env` est ignoré par Git.

L'amorçage est **ignoré** si l'email ou le mot de passe est absent, si le mot de passe fait
moins de 12 caractères, ou si un `ADMIN` existe déjà. Il ne modifie donc jamais un compte
existant : changer `APP_ADMIN_PASSWORD` après coup n'a aucun effet sur le compte déjà créé.

Générer un mot de passe robuste :

```bash
LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 24
```

> Restez sur des caractères alphanumériques : un `#` dans une valeur de `.env` serait interprété
> comme un début de commentaire par le `Makefile`.

### Comment un employé obtient un compte

L'administrateur **ne crée pas** de comptes et ne choisit jamais de mot de passe. Un
administrateur capable de fixer le mot de passe d'un employé pourrait se connecter à sa place :
la séparation ci-dessous ferme cette porte.

1. L'employé s'inscrit lui-même sur `/register` et choisit son mot de passe.
2. Le compte est créé au statut `PENDING`. **La connexion est refusée** tant qu'il le reste.
3. L'administrateur voit l'inscription dans *Inscriptions*, attribue un rôle et valide. Le
   compte passe `ACTIVE`.
4. L'employé se connecte avec le mot de passe qu'il a choisi, que personne d'autre ne connaît.

Une inscription peut aussi être refusée : le compte passe `REJECTED` et est conservé pour la
traçabilité.

Chacun change son propre mot de passe depuis *Mon compte*, en fournissant l'actuel. Ni un
administrateur, ni le serveur ne peuvent restituer un mot de passe : seule son empreinte
BCrypt est stockée.

Le statut est revérifié **à chaque requête**. Un compte refusé perd donc l'accès aussitôt,
sans attendre l'expiration de son jeton.

### Les comptes de démonstration

Le seed est **désactivé par défaut** et n'a sa place qu'en développement. Pour l'activer :

```bash
APP_SEED_ENABLED=true
APP_SEED_PASSWORD=<votre_mot_de_passe_de_demo>
```

Le mot de passe des comptes de démonstration provient **exclusivement** de
`APP_SEED_PASSWORD` : aucun mot de passe n'est écrit en dur. Le seed ne s'exécute que si la
base est vide, et il est ignoré si `APP_SEED_PASSWORD` fait moins de 8 caractères.

Il est volontairement distinct de l'amorçage de l'administrateur : un déploiement propre part
avec **un seul compte**, tandis que le développement peut disposer d'un jeu complet.

| Email | Rôle | Salaire fictif |
|---|---|---:|
| `admin@salarytontine.test` | ADMIN | 0 |
| `comptable@salarytontine.test` | ACCOUNTANT | 0 |
| `awa@salarytontine.test` | EMPLOYEE | 500 000 |
| `fatou@salarytontine.test` | EMPLOYEE | 450 000 |
| `mamadou@salarytontine.test` | EMPLOYEE | 600 000 |
| `khady@salarytontine.test` | EMPLOYEE | 550 000 |
| `aliou@salarytontine.test` | EMPLOYEE | 400 000 |

Une tontine de démonstration **Tontine Équipe A** (50 000 FCFA/mois) est créée au statut
`DRAFT` : Awa, Fatou et Mamadou y sont déjà participants, tandis que Khady et Aliou arrivent
avec une **demande en attente**, pour que le comptable ait quelque chose à arbitrer dès sa
première connexion.

---

## 11. Rôles et permissions

| Rôle | Peut |
|---|---|
| `EMPLOYEE` | Consulter son profil, son salaire fictif, ses cotisations et son historique de salaires simulés. **Parcourir les tontines ouvertes, demander à en rejoindre une, retirer sa demande.** Il ne voit jamais que ses propres montants. |
| `ACCOUNTANT` | Le comptable. Tout ce que peut un employé, plus : créer et modifier une tontine, **arbitrer les demandes d'adhésion**, fixer l'ordre de passage, activer une tontine, **consulter et corriger le salaire de base de chaque employé**, générer les cotisations et les salaires. |
| `ADMIN` | **Créer les comptes** et attribuer les rôles, consulter le journal d'audit, plus tout ce que peut le comptable. |

Le rôle du comptable réunit volontairement la gestion des tontines et la vue sur les salaires :
la cotisation *est* un prélèvement sur le salaire, elle relève donc du même métier.

L'admin ne s'occupe que des comptes et des droits ; il conserve l'accès aux salaires parce que
rien ne lui est fermé, mais ce n'est pas son travail au quotidien.

À l'inscription publique, le serveur impose `role = EMPLOYEE` et `baseSalary = 0`. Un
utilisateur ne peut jamais s'attribuer lui-même un rôle, un salaire ou des droits. Seul
`POST /api/admin/users` permet de fixer un rôle et un salaire dès la création du compte.

---

## 12. API

Toutes les routes hors authentification exigent une session valide.

### Authentification

| Méthode | Route | Rôle requis |
|---|---|---|
| `POST` | `/api/auth/register` | public |
| `POST` | `/api/auth/login` | public |
| `POST` | `/api/auth/logout` | public |
| `GET` | `/api/auth/me` | authentifié |

### Utilisateurs

| Méthode | Route | Rôle requis |
|---|---|---|
| `GET` | `/api/users/me` | authentifié |
| `PATCH` | `/api/users/me/password` | authentifié *(exige le mot de passe actuel)* |

### Tableau de bord

| Méthode | Route | Rôle requis |
|---|---|---|
| `GET` | `/api/dashboard` | authentifié |

### Tontines

| Méthode | Route | Rôle requis |
|---|---|---|
| `GET` | `/api/tontines` | authentifié *(ses tontines ; toutes pour un gestionnaire)* |
| `GET` | `/api/tontines/open` | authentifié *(tontines `DRAFT`, ouvertes aux inscriptions)* |
| `GET` | `/api/tontines/{id}` | authentifié *(participant, tontine `DRAFT`, ACCOUNTANT ou ADMIN)* |
| `POST` | `/api/tontines` | ACCOUNTANT, ADMIN *(`frequency` et, pour `CUSTOM`, `periodDays`)* |
| `PATCH` | `/api/tontines/{id}` | ACCOUNTANT, ADMIN |
| `GET` | `/api/tontines/{id}/members` | authentifié *(participant, ACCOUNTANT ou ADMIN)* |
| `POST` | `/api/tontines/{id}/members` | ACCOUNTANT, ADMIN |
| `DELETE` | `/api/tontines/{id}/members/{userId}` | ACCOUNTANT, ADMIN |
| `DELETE` | `/api/tontines/{id}/members/me` | authentifié *(quitter, tontine `DRAFT` uniquement)* |
| `POST` | `/api/tontines/{id}/activate` | ACCOUNTANT, ADMIN |
| `POST` | `/api/tontines/{id}/cancel` | ACCOUNTANT, ADMIN *(arrête le cycle, conserve l'historique)* |
| `DELETE` | `/api/tontines/{id}` | ACCOUNTANT, ADMIN *(tontine `DRAFT` uniquement)* |
| `GET` | `/api/tontines/{id}/schedule` | authentifié *(participant, ACCOUNTANT ou ADMIN)* |

### Adhésions

Une tontine n'accepte de demandes que tant qu'elle est au statut `DRAFT`.

| Méthode | Route | Rôle requis |
|---|---|---|
| `POST` | `/api/tontines/{id}/join-requests` | authentifié *(demande pour soi-même)* |
| `DELETE` | `/api/tontines/{id}/join-requests/me` | authentifié *(retire sa propre demande en attente)* |
| `GET` | `/api/tontines/{id}/join-requests` | ACCOUNTANT, ADMIN |
| `POST` | `/api/tontines/{id}/join-requests/{requestId}/accept` | ACCOUNTANT, ADMIN |
| `POST` | `/api/tontines/{id}/join-requests/{requestId}/reject` | ACCOUNTANT, ADMIN |
| `GET` | `/api/join-requests/me` | authentifié |
| `GET` | `/api/join-requests/pending` | ACCOUNTANT, ADMIN *(file d'attente, toutes tontines)* |

L'acceptation crée le participant. Sans `turnOrder` dans le corps de la requête, le demandeur
prend la place suivante disponible dans l'ordre de passage.

### Annuaire salarial

| Méthode | Route | Rôle requis |
|---|---|---|
| `GET` | `/api/employees` | ACCOUNTANT, ADMIN |
| `PATCH` | `/api/employees/{id}/salary` | ACCOUNTANT, ADMIN |
| `GET` | `/api/employees/{id}/salaries` | ACCOUNTANT, ADMIN |

### Cotisations

| Méthode | Route | Rôle requis |
|---|---|---|
| `POST` | `/api/tontines/{id}/contributions/generate` | ACCOUNTANT, ADMIN *(corps : `{"periodIndex": 1}`)* |
| `GET` | `/api/tontines/{id}/contributions` | authentifié *(filtrable par `periodIndex` ; un EMPLOYEE ne voit que les siennes)* |

### Salaires simulés

| Méthode | Route | Rôle requis |
|---|---|---|
| `POST` | `/api/tontines/{id}/salaries/generate` | ACCOUNTANT, ADMIN *(corps : `{"periodIndex": 1}`)* |
| `GET` | `/api/salaries/me` | authentifié *(une ligne par tontine et par tour)* |
| `GET` | `/api/salaries/me/{month}` | authentifié *(bulletin consolidé du mois)* |

### Administration

| Méthode | Route | Rôle requis |
|---|---|---|
| `GET` | `/api/admin/users` | ADMIN |
| `POST` | `/api/admin/users/{id}/approve` | ADMIN *(valide une inscription et attribue le rôle)* |
| `POST` | `/api/admin/users/{id}/reject` | ADMIN |
| `PATCH` | `/api/admin/users/{id}/role` | ADMIN |
| `PATCH` | `/api/admin/users/{id}/salary` | ADMIN |
| `GET` | `/api/admin/users/{id}/salaries` | ADMIN |
| `GET` | `/api/admin/audit-logs` | ADMIN |

### Format des mois

Tout mois échangé avec l'API utilise le format **`YYYY-MM`** (exemple : `2026-08`), en entrée
comme en sortie.

### Format des erreurs

```json
{
  "timestamp": "2026-08-26T09:12:33.421Z",
  "status": 409,
  "error": "Conflict",
  "message": "Les cotisations de 2026-08 ont deja ete generees pour cette tontine.",
  "path": "/api/tontines/1/contributions/generate"
}
```

Les erreurs de validation ajoutent un objet `validationErrors` détaillant chaque champ fautif.
Aucune trace d'exécution Java n'est jamais renvoyée au client.

### Codes de statut

| Code | Signification dans l'application |
|---|---|
| `200` | Requête traitée |
| `201` | Ressource créée (inscription, tontine, participant, génération mensuelle) |
| `204` | Traitée sans contenu (déconnexion, retrait d'un participant) |
| `400` | Données invalides ou règle métier violée (mois hors cycle, activation impossible) |
| `401` | Non authentifié ou identifiants invalides |
| `403` | Authentifié mais privilèges insuffisants |
| `404` | Ressource inexistante |
| `409` | Doublon : email déjà pris, génération mensuelle déjà effectuée, ordre de passage occupé |
| `500` | Erreur interne (message générique, aucun détail technique exposé) |

---

## 13. Collection API

Une collection Postman prête à l'emploi est fournie :

```
docs/SalaryTontine.postman_collection.json
```

Import : *Postman → Import → File*. La collection couvre l'inscription, la connexion, le
profil, l'administration, la création d'une tontine, l'ajout de participants, l'activation,
les cotisations, la génération des salaires et la consultation de `/api/salaries/me`.

Postman conserve automatiquement le cookie de session : jouer d'abord une requête *Login*,
puis les dossiers dans l'ordre. La variable `demoPassword` doit correspondre à
`APP_SEED_PASSWORD`.

---

## 14. Base de données

| Table | Contenu |
|---|---|
| `users` | Comptes : rôle, statut de validation et salaire de base fictif |
| `tontines` | Tontines : cotisation par tour, cadence, date de début, nombre de places et statut |
| `tontine_members` | Participation **acceptée** d'un utilisateur, avec son ordre de passage |
| `tontine_join_requests` | Demande d'adhésion : distincte de la participation, elle n'entre dans aucun calcul tant qu'elle n'est pas acceptée |
| `contributions` | Cotisation d'un participant pour un mois donné |
| `salary_records` | Salaire simulé d'un participant pour un mois donné |
| `audit_logs` | Journal des actions sensibles |

Les invariants sont garantis par la base et pas seulement par le code Java :

- `users.email` unique ;
- un utilisateur ne peut figurer qu'une fois dans une tontine ;
- un ordre de passage est unique au sein d'une tontine, et supérieur ou égal à 1 ;
- une cotisation est unique par (tontine, utilisateur, **tour**) ;
- un salaire simulé est unique par (utilisateur, tontine, **tour**) : un employé cotisant à
  plusieurs tontines a une ligne par tontine et par tour, consolidées au niveau du mois ;
- une cadence `CUSTOM` porte obligatoirement sa durée en jours, les autres ne la portent jamais :
  deux sources de vérité pour la même valeur finiraient par diverger ;
- un employé n'a qu'une demande d'adhésion par tontine ;
- une décision d'adhésion porte toujours sa date, ou aucun des deux champs ;
- le nombre de places, s'il est renseigné, vaut au moins 2 ;
- les montants sont contraints (`monthly_amount > 0`, salaires `>= 0`) ;
- les mois respectent le format `YYYY-MM` (contrainte `CHECK` par expression régulière).

Tous les montants utilisent `NUMERIC(15,2)` en base et `BigDecimal` en Java. Aucun type
flottant n'intervient dans un calcul monétaire.

---

## 15. Sécurité

- Mots de passe hachés avec **BCrypt** (coût 12) ; aucun mot de passe n'est stocké en clair,
  et aucune réponse de l'API n'expose l'empreinte.
- **Personne ne connaît le mot de passe d'autrui**, pas même un administrateur : l'employé le
  choisit à l'inscription et ne peut le changer qu'en fournissant l'actuel. Aucun endpoint ne
  permet de définir le mot de passe d'un tiers.
- Le **statut du compte est revérifié à chaque requête** : un compte refusé perd l'accès
  immédiatement, sans attendre l'expiration de son jeton.
- Jeton **JWT signé en HMAC-SHA** (HS256, HS384 ou HS512 selon la longueur de la clé)
  transporté dans un cookie **`HttpOnly`, `SameSite=Lax`**, `Secure` en
  production via `JWT_COOKIE_SECURE`. Le jeton n'est jamais placé dans `localStorage` et reste
  inaccessible au JavaScript de la page.
- Clé JWT issue exclusivement de l'environnement ; le démarrage échoue si elle est absente ou
  fait moins de 32 caractères.
- API **stateless**, autorisations par rôle au niveau de la configuration et via
  `@PreAuthorize` sur les endpoints sensibles.
- L'identité de l'appelant provient toujours du contexte de sécurité, jamais d'un paramètre
  client : `/api/salaries/me` ne peut pas être détourné pour lire le salaire d'autrui.
- CORS restreint à l'origine `APP_FRONTEND_URL`.
- Journal d'audit dépourvu de tout secret : ni mot de passe, ni hash, ni jeton, ni credential
  n'y est écrit (une assertion de test le vérifie).
- Erreurs génériques côté client, détails techniques conservés dans les logs serveur.

---

## 16. Scénario de démonstration

Avec le seed activé et la pile démarrée :

1. Se connecter en **ADMIN** (`admin@salarytontine.test`). Dans *Comptes*, cliquer sur
   **Nouveau compte** : une fenêtre modale demande nom, email, mot de passe, rôle et salaire de
   base. Créer un employé avec un salaire non nul.
2. Se déconnecter, puis se connecter avec ce nouveau compte **EMPLOYEE**. Dans `/tontines`,
   la section *Tontines ouvertes aux inscriptions* affiche **Tontine Équipe A** avec ses places
   occupées et sa fin de cycle prévisionnelle. Cliquer sur **Rejoindre**, laisser un message,
   envoyer. La demande passe *En attente*.
3. Se connecter en **COMPTABLE** (`comptable@salarytontine.test`). Le menu latéral affiche une
   pastille sur *Demandes* : trois demandes attendent (Khady, Aliou et le nouveau compte).
4. Dans *Demandes*, accepter les demandes. Laisser l'ordre de passage vide pour que chacun
   prenne la place suivante, ou en imposer un.
5. Dans *Employés et salaires*, vérifier que chaque participant a un salaire de base non nul :
   sans cela, l'activation échouera.
6. Ouvrir **Tontine Équipe A** dans `/tontines`. Avant d'activer, tester le départ : depuis un
   compte employé, le bouton **Quitter cette tontine** libère la place et renumérote les ordres
   de passage restants. Puis, en comptable, cliquer sur **Activer la tontine** et confirmer. Les
   inscriptions se ferment, le départ n'est plus possible, et la fin de cycle devient définitive.
7. Dans *Génération mensuelle*, choisir le premier mois du cycle et **générer les cotisations**,
   puis **générer les salaires**.
8. Vérifier dans le calendrier que le bénéficiaire du premier mois est bien celui dont l'ordre
   de passage vaut 1, et que son salaire simulé inclut toute la cagnotte.
9. Passer au mois suivant et répéter l'étape 7 : le bénéficiaire est le suivant dans l'ordre.
10. Tester le cumul : créer une seconde tontine et y faire entrer le même employé. Tant que la
    somme des cotisations tient dans son salaire de base, c'est accepté ; au-delà, le serveur
    refuse en indiquant le montant atteint. Après génération des deux tontines pour le même
    mois, `/my-salary` affiche **un seul** salaire final, consolidé.
11. Se reconnecter en **EMPLOYEE** :
    - `/dashboard` affiche son salaire simulé, sa position et la frise du cycle ;
    - `/my-salary` liste uniquement son propre historique ;
    - les entrées *Demandes*, *Employés et salaires*, *Inscriptions*, *Comptes* et *Journal
      d'audit* sont absentes du menu, et y accéder par l'URL renvoie une page « Accès refusé ».
12. Se reconnecter en **ADMIN** et ouvrir *Journal d'audit* : chaque action de la démonstration
    y figure, avec son auteur. Les générations déclenchées par la tâche planifiée y
    apparaîtraient sous l'auteur « Système ».

---

## 17. Structure du dépôt

```
salary-tontine/
├── backend/
│   ├── pom.xml
│   ├── mvnw, mvnw.cmd, .mvn/
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/salarytontine/
│       │   │   ├── config/       controller/   dto/{request,response}/
│       │   │   ├── entity/       enums/        exception/
│       │   │   ├── mapper/       repository/   security/     service/
│       │   │   └── SalaryTontineApplication.java
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/
│       └── test/java/com/salarytontine/
│           ├── integration/  security/  service/  support/
├── frontend/
│   ├── package.json, vite.config.ts, tsconfig*.json
│   ├── Dockerfile, nginx.conf
│   └── src/
│       ├── api/  components/  context/  hooks/  layouts/
│       ├── pages/{auth,account,dashboard,tontines,salary,employees,
│       │           requests,registrations,users,audit}/
│       ├── routes/  test/  types/  utils/
│       ├── App.tsx  main.tsx  styles.css
├── docs/
│   └── SalaryTontine.postman_collection.json
├── .env.example
├── .gitignore
├── docker-compose.yml
└── README.md
```

---

## 18. Limites connues

- Les demandes d'adhésion ne sont **pas notifiées** : le comptable les découvre en consultant
  sa file d'attente, et l'employé suit l'état de la sienne sur son tableau de bord.
- Une fois la tontine activée, plus aucune adhésion ni aucun départ n'est possible. Rouvrir un
  cycle en cours casserait l'équité entre les tours déjà joués.
- Le plafond de cotisation est le salaire de base entier. Aucune marge de sécurité n'est
  imposée : un employé peut théoriquement engager la totalité de son salaire.
- Le traitement mensuel automatique passe une fois par jour et rattrape le mois courant. Il ne
  rattrape pas un mois antérieur laissé de côté : celui-ci reste à générer manuellement.
- Le statut `CANCELLED` d'une tontine existe dans le modèle mais aucun endpoint ne l'utilise
  encore.
- Les salaires simulés ne sont pas recalculables : une génération est définitive pour un mois
  donné, ce qui préserve l'historique.
