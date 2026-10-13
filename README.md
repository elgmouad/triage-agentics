# TriageAI — Triage intelligent de tickets de support

> Projet de la matière **Cloud Computing: Fundamentos e Infraestructuras**
> Máster Universitario en Ingeniería Informática — ETSIIT, Universidad de Granada (curso 2026-27)

> ⚠️ **Brouillon de travail en français.** La version finale sera traduite (espagnol ou anglais) avant la remise du Hito 1.

---

## Table des matières

1. [Description du domaine](#1-description-du-domaine)
2. [Règles métier](#2-règles-métier)
3. [Cas d'utilisation](#3-cas-dutilisation)
4. [Justification NIST SP 800-145](#4-justification-nist-sp-800-145)
5. [Mapping des acteurs NIST SP 500-292](#5-mapping-des-acteurs-nist-sp-500-292)
6. [Architecture](#6-architecture)
7. [Spécification de l'API REST](#7-spécification-de-lapi-rest)
8. [Gestion du projet](#8-gestion-du-projet)
9. [État du projet](#9-état-du-projet)

---

## 1. Description du domaine

### 1.1 Le problème métier

Les équipes de support technique des PME reçoivent chaque jour un grand volume de demandes (*tickets*) : incidents, questions, réclamations. Aujourd'hui, **le tri de ces demandes est fait manuellement**, ce qui entraîne :

- **Perte de temps** : un agent humain doit lire chaque ticket pour en décider la catégorie et la priorité avant même de commencer à le résoudre.
- **Priorisation incohérente** : deux agents peuvent classer différemment le même problème ; un incident critique peut rester bloqué derrière des questions mineures.
- **Non-respect des délais** : les entreprises s'engagent sur des délais de réponse (*SLA*). Sans suivi automatique, ils sont dépassés sans que personne ne s'en aperçoive.
- **Réponses répétitives** : beaucoup de tickets concernent des problèmes déjà connus, mais chaque réponse est rédigée à partir de zéro.
- **Charge très irrégulière** : lors d'une panne ou d'un lancement de produit, le volume de tickets peut être multiplié par dix en quelques heures, puis redevenir normal.

### 1.2 La solution proposée

**TriageAI** est une application *cloud-native* basée sur des microservices qui automatise la première étape du support. À chaque nouveau ticket :

1. un **agent d'intelligence artificielle** analyse le texte, consulte l'historique du client (via un *outil*) et détermine la **catégorie**, la **priorité**, un **résumé** et un **niveau de confiance** ;
2. il propose un **brouillon de réponse** à l'agent humain ;
3. le système **suit automatiquement les délais SLA** et **escalade** les tickets à risque.

**Principes clés :**

- **Human-in-the-loop** : l'IA n'envoie jamais de réponse seule au client ; un agent humain valide toujours la proposition.
- **Résilience multi-fournisseur** : le système bascule automatiquement entre deux fournisseurs de LLM (**Ollama** en conteneur local ↔ **API Anthropic**), protégés par un *circuit breaker*.
- **Dégradation contrôlée** : si les deux fournisseurs sont indisponibles, un tri par **règles simples** est appliqué et le tri IA est relancé plus tard. Aucun ticket n'est jamais perdu.

### 1.3 Acteurs

| Acteur | Description |
|---|---|
| **Client** | Utilisateur final qui signale un problème et suit l'avancement de sa demande |
| **Agent de support** | Employé humain qui valide le tri, répond et résout les tickets |
| **Superviseur** | Responsable de l'équipe : surveille les délais, les escalades et les métriques |
| **Agent IA** | Composant automatique qui trie les tickets et propose des réponses |
| **Planificateur** (système) | Tâche automatique qui vérifie les SLA et relance les tris en attente |

---

## 2. Règles métier

Ces règles constituent la logique métier de l'application (au-delà d'un simple CRUD).

### RN-01 — Cycle de vie du ticket (machine à états)

```mermaid
stateDiagram-v2
    [*] --> NOUVEAU
    NOUVEAU --> TRIE : tri IA réussi
    NOUVEAU --> EN_ATTENTE_DE_TRI : IA indisponible
    EN_ATTENTE_DE_TRI --> TRIE : nouvel essai réussi
    TRIE --> EN_COURS : un agent prend le ticket
    EN_COURS --> RESOLU : solution apportée
    RESOLU --> EN_COURS : réouverture par le client (≤ 7 jours)
    RESOLU --> FERME : 7 jours sans réouverture
    FERME --> [*]
```

- Seules les transitions représentées sont autorisées ; toute autre transition est refusée par l'API.
- Un ticket `RESOLU` peut être rouvert par le client pendant 7 jours, puis passe automatiquement à `FERME`.

### RN-02 — Délais SLA selon la priorité

| Priorité | Première réponse | Résolution |
|---|---|---|
| CRITIQUE | 1 h | 8 h |
| HAUTE | 4 h | 24 h |
| MOYENNE | 24 h | 3 jours |
| BASSE | 72 h | 7 jours |

Valeurs indicatives, **configurables** via le Config Server.

### RN-03 — Escalade automatique

Lorsqu'un ticket atteint **80 % de son délai SLA** sans réponse, il est marqué **« escaladé »** et le superviseur est alerté. L'escalade est un indicateur posé sur le ticket, pas un état.

### RN-04 — Seuil de confiance de l'IA

L'IA renvoie un niveau de confiance entre 0 et 1. En dessous de **0,7**, le ticket est marqué **« révision manuelle requise »**.

### RN-05 — Validation humaine obligatoire

Un brouillon de réponse généré par l'IA ne peut jamais être envoyé au client sans validation d'un agent de support.

### RN-06 — Basculement et dégradation contrôlée

1. Appel au fournisseur **primaire** (configurable, par défaut Ollama en local).
2. En cas d'échec (timeout, erreur de connexion, HTTP 401/403/429/5xx, JSON invalide) → fournisseur **secondaire** (Anthropic).
3. Un **circuit breaker** (états `CLOSED` / `OPEN` / `HALF_OPEN`) évite d'appeler un fournisseur déjà en panne.
4. Si les deux échouent → **tri par règles** (mots-clés), état `EN_ATTENTE_DE_TRI`, nouvel essai planifié.

### RN-07 — Traçabilité de l'IA

Chaque tri enregistre le **fournisseur utilisé**, le **nombre de tokens** consommés et le **temps de réponse**.

---

## 3. Cas d'utilisation

### 3.1 MVP

| Code | Cas d'utilisation | Acteur | Description | Règles |
|---|---|---|---|---|
| **CU-01** | Créer un ticket | Client | Le client décrit son problème (titre, description) | RN-01 |
| **CU-02** | Consulter ses tickets | Client | Voir l'état et l'historique de ses demandes | — |
| **CU-03** | Trier automatiquement un ticket | Agent IA | Catégorie, priorité, résumé, confiance et brouillon de réponse, en consultant l'historique du client | RN-04, RN-06, RN-07 |
| **CU-04** | Valider le tri et répondre | Agent de support | Consulter la proposition de l'IA, la corriger si besoin, envoyer la réponse | RN-05 |
| **CU-05** | Changer l'état d'un ticket | Agent de support | Faire avancer le ticket dans son cycle de vie | RN-01 |
| **CU-06** | Surveiller les SLA et escalader | Planificateur | Vérifier les délais et marquer les tickets à risque | RN-02, RN-03 |
| **CU-07** | Relancer les tris en attente | Planificateur | Retenter le tri IA des tickets `EN_ATTENTE_DE_TRI` | RN-06 |

### 3.2 Extensions (optionnelles)

| Code | Cas d'utilisation | Acteur |
|---|---|---|
| **CU-08** | Consulter les métriques (tickets par état, respect des SLA, consommation par fournisseur LLM) | Superviseur |
| **CU-09** | Rouvrir un ticket résolu | Client |
| **CU-10** | Configurer les délais SLA | Superviseur |

### 3.3 Hors périmètre

- Réception de tickets par e-mail, chat ou réseaux sociaux (l'API REST suffit).
- Gestion avancée des comptes et des droits.
- Interface web complète (Swagger UI / Postman pour les démonstrations).

---

## 4. Justification NIST SP 800-145

> Référence : P. Mell, T. Grance, *The NIST Definition of Cloud Computing*, NIST SP 800-145, 2011. https://doi.org/10.6028/NIST.SP.800-145

Le NIST définit le cloud computing par **5 caractéristiques essentielles**. Cette section montre comment TriageAI les exploite et pourquoi le problème métier (section 1) a **besoin** du cloud.

### 4.1 Synthèse

| Caractéristique NIST | Besoin métier de TriageAI | Mécanisme technique | Hito où elle sera démontrée |
|---|---|---|---|
| **Autoservice à la demande** (*Autoservicio bajo demanda*) | Ajouter de la capacité ou un environnement sans intervention humaine du fournisseur | Infrastructure décrite en code (IaC), configuration externalisée | Hito 3, Hito 4 |
| **Large accès réseau** (*Acceso amplio a la red*) | Clients et agents de support dispersés, sur tout type d'appareil | API REST sur HTTPS via un point d'entrée unique | Hito 1 (API), Hito 4 (déploiement) |
| **Mutualisation des ressources** (*Asignación común de recursos*) | Servir plusieurs entreprises clientes avec la même plateforme | Modèle multi-locataire, ressources physiques partagées du fournisseur | Hito 3 |
| **Élasticité rapide** (*Rápida elasticidad*) | Absorber des pics de tickets ×10 lors d'une panne ou d'un lancement | Services sans état réplicables, file d'événements Redis Streams | Hito 2, Hito 5 (tests de charge) |
| **Service mesuré** (*Servicio medido*) | Connaître et maîtriser le coût de chaque ticket trié par IA | Traçabilité des tokens et latences (RN-07), métriques Actuator | Hito 1 (conception), Hito 5 (observabilité) |

### 4.2 Autoservice à la demande

**Définition NIST (résumée) :** le consommateur peut obtenir lui-même des capacités de calcul (serveurs, stockage…) automatiquement, **sans interaction humaine** avec le fournisseur.

**Application à TriageAI :**

- Toute l'infrastructure (conteneurs, réseau, base de données, cache) sera décrite par du code et créée par une simple commande, sans ticket adressé à un administrateur (Hito 3).
- Un nouvel environnement complet (test, démonstration, production) peut être obtenu à la demande à partir des mêmes fichiers.
- Côté IA, l'accès à l'API Anthropic s'obtient en libre-service : création d'une clé et facturation à l'usage, sans contrat ni matériel.
- Les paramètres métier (délais SLA, ordre des fournisseurs LLM, seuil de confiance) se modifient dans le dépôt de configuration, sans redéploiement du code.

### 4.3 Large accès réseau

**Définition NIST (résumée) :** les capacités sont disponibles sur le réseau et accessibles par des mécanismes standards, depuis des clients hétérogènes (ordinateurs, mobiles, tablettes…).

**Application à TriageAI :**

- Toutes les fonctionnalités sont exposées par une **API REST standard** (HTTP, JSON, OpenAPI) derrière `api-gateway` : n'importe quel client (application web, mobile, outil interne, intégration d'un autre logiciel) peut l'utiliser.
- Les clients signalent leurs problèmes depuis n'importe où ; les agents de support peuvent travailler à distance.
- Le contrat de l'API est documenté et versionné (`/api/v1`), ce qui facilite l'intégration par des tiers.

### 4.4 Mutualisation des ressources

**Définition NIST (résumée) :** les ressources du fournisseur sont regroupées pour servir plusieurs consommateurs selon un **modèle multi-locataire**, et réaffectées dynamiquement selon la demande. Le consommateur ne connaît pas l'emplacement exact des ressources.

**Application à TriageAI :**

- **Côté fournisseur cloud :** les conteneurs de TriageAI s'exécutent sur une infrastructure physique partagée avec d'autres clients du fournisseur ; TriageAI ne gère ni serveurs ni centre de données.
- **Côté TriageAI :** la plateforme est conçue pour servir **plusieurs entreprises clientes** (PME) avec les mêmes instances de services, la même base de données et le même cache. La séparation est logique (identifiant du client sur chaque ticket), pas physique.
- **Côté IA :** l'API Anthropic est elle-même un service mutualisé entre de nombreux consommateurs ; TriageAI n'en paie que la part utilisée.

### 4.5 Élasticité rapide

**Définition NIST (résumée) :** les capacités peuvent être augmentées ou réduites rapidement, parfois automatiquement, en fonction de la demande. Pour le consommateur, elles semblent illimitées.

**Application à TriageAI :** c'est la caractéristique la plus importante pour le projet, car la charge de support est **très irrégulière** (section 1.1).

- **Services sans état :** `servicio-tickets` et `servicio-agente-triaje` ne gardent aucune donnée en mémoire ; on peut donc lancer 1, 3 ou 10 répliques selon la charge.
- **File d'événements :** lors d'un pic, les tickets sont enregistrés immédiatement et les événements `ticket.creado` s'accumulent dans Redis Streams. Les répliques de l'agent IA, membres du même groupe de consommateurs, se **partagent le travail** automatiquement.
- **Indicateur d'élasticité :** la longueur de la file `ticket-events` est une métrique naturelle pour déclencher l'ajout ou la suppression de répliques.
- **Élasticité de l'IA :** si le fournisseur local (Ollama) est saturé, le circuit breaker bascule vers l'API Anthropic, qui absorbe la charge supplémentaire.

**Illustration (hypothèses chiffrées à titre d'exemple) :** une charge normale demande 1 réplique de l'agent IA et un pic en demande 10 pendant 5 % du temps.

| Approche | Capacité payée | Équivalent machines sur l'année |
|---|---|---|
| Serveurs propres dimensionnés pour le pic | 10 répliques en permanence | 10 |
| Cloud élastique | 1 réplique × 95 % + 10 répliques × 5 % | **1,45** |

Dans cet exemple, l'élasticité réduit la capacité payée d'environ **85 %**. Les vrais chiffres seront mesurés lors des tests de charge du Hito 5.

### 4.6 Service mesuré

**Définition NIST (résumée) :** l'usage des ressources est automatiquement **mesuré, contrôlé et rapporté**, ce qui assure la transparence pour le fournisseur et le consommateur (paiement à l'usage).

**Application à TriageAI :**

- **Mesure de l'IA (RN-07) :** chaque appel LLM enregistre le fournisseur, le modèle, les tokens d'entrée et de sortie, la latence et le résultat (table `llamada_llm`). Le **coût de chaque ticket trié** est donc calculable.
- **Mesure de l'infrastructure :** chaque service expose ses métriques (Spring Boot Actuator, Micrometer) : requêtes, erreurs, temps de réponse, consommation mémoire.
- **Contrôle :** le rate limiting du gateway et le choix du fournisseur LLM (local gratuit en priorité) limitent la consommation.
- **Rapport :** l'endpoint `GET /api/v1/triaje/metricas` (CU-08) fournit la consommation par fournisseur et par période. Il servira de base à une **facturation par ticket** des entreprises clientes.

### 4.7 Modèles de service et de déploiement

Le NIST SP 800-145 définit aussi des modèles de service et de déploiement. TriageAI s'y positionne ainsi :

| Point de vue | Modèle |
|---|---|
| TriageAI vu par les entreprises clientes | **SaaS** (*Software as a Service*) : application complète consommée via l'API |
| Ce que TriageAI consomme pour s'exécuter | **IaaS / PaaS** d'un fournisseur cloud public (à choisir au Hito 3) |
| Ce que TriageAI consomme pour l'IA | **SaaS / API** : modèle de langage d'Anthropic, facturé à l'usage |
| Modèle de déploiement | **Cloud public**, avec exécution locale possible du LLM (Ollama) |

---

## 5. Mapping des acteurs NIST SP 500-292

> Référence : F. Liu et al., *NIST Cloud Computing Reference Architecture*, NIST SP 500-292, 2011. https://doi.org/10.6028/NIST.SP.500-292

L'architecture de référence du NIST définit **5 acteurs**. Pour TriageAI, il faut distinguer deux niveaux : TriageAI **fournit** un service à des entreprises, et TriageAI **consomme** lui-même des services cloud.

### 5.1 Vue d'ensemble

```mermaid
flowchart LR
    subgraph CONS["Consommateurs (Consumidor)"]
        PME["Entreprises clientes (PME)<br/>et leurs agents de support"]
        CLI["Clients finaux<br/>qui créent des tickets"]
    end

    subgraph PROV["Fournisseurs (Proveedor)"]
        TAI["TriageAI<br/>fournisseur SaaS"]
        IAAS["Fournisseur cloud public<br/>IaaS / PaaS"]
        ANT["Anthropic<br/>API LLM"]
    end

    BRK["Intermédiaire (Intermediario)<br/>LlmRouter : arbitrage<br/>Ollama / Anthropic"]
    AUD["Auditeur (Auditor)<br/>RGPD · sécurité · SLA"]
    CAR["Opérateur réseau (Operador)<br/>FAI · réseau du fournisseur · Internet"]

    CLI --> PME
    PME -- "consomme le SaaS" --> TAI
    TAI -- "s'exécute sur" --> IAAS
    TAI --> BRK
    BRK -- "arbitre entre" --> ANT
    AUD -. "audite" .-> TAI
    CAR -. "transporte" .-> PME
    CAR -. "transporte" .-> ANT
```

### 5.2 Détail des rôles

| Rôle NIST | Acteur(s) dans TriageAI | Activités dans le projet | Impact économique |
|---|---|---|---|
| **Consommateur** (*Cloud Consumer* / *Consumidor*) | ① Les **entreprises clientes** (PME) et leurs agents de support, qui consomment TriageAI en SaaS. ② **L'équipe TriageAI**, qui consomme de l'IaaS/PaaS et l'API Anthropic | Utiliser l'API, valider les tris, suivre les SLA ; pour l'équipe TriageAI : déployer et payer les ressources consommées | Les PME passent d'un **coût fixe** (agents qui trient à la main) à un **coût variable** par ticket ; l'équipe TriageAI n'a **aucun investissement matériel** (CapEx → OpEx) |
| **Fournisseur** (*Cloud Provider* / *Proveedor*) | ① **TriageAI** (fournisseur SaaS). ② Le **fournisseur cloud public** (IaaS/PaaS, choisi au Hito 3). ③ **Anthropic** (API LLM) | TriageAI : déploiement, orchestration, gestion du service, sécurité et confidentialité des données de tickets. Fournisseur cloud : calcul, stockage, réseau. Anthropic : inférence du modèle | TriageAI se rémunère **par ticket traité** ou par abonnement ; ses coûts sont l'infrastructure + les tokens. Le fournisseur cloud et Anthropic facturent **à l'usage** (heures de calcul, tokens) |
| **Intermédiaire** (*Cloud Broker* / *Intermediario*) | Le composant **`LlmRouter`** de `servicio-agente-triaje` remplit les fonctions d'un broker entre fournisseurs LLM. Un broker externe (place de marché, agrégateur d'API LLM) pourrait aussi jouer ce rôle | **Intermédiation** : interface unique (Spring AI) quel que soit le fournisseur. **Arbitrage** : choix dynamique Ollama ↔ Anthropic selon disponibilité et coût (circuit breaker, ordre configurable). **Agrégation** : un seul service de tri bâti sur plusieurs fournisseurs | Évite la **dépendance à un fournisseur unique** (*vendor lock-in*) ; privilégie le fournisseur gratuit (local) et n'utilise le fournisseur payant qu'en secours ; le cache Redis évite de repayer des tris identiques |
| **Auditeur** (*Cloud Auditor* / *Auditor*) | Un **auditeur externe indépendant** : délégué à la protection des données, cabinet d'audit de sécurité, ou l'entreprise cliente qui vérifie les SLA | **Audit de confidentialité** : les tickets contiennent des données personnelles (RGPD, et en Espagne la LOPDGDD) ; envoi de données à une API externe. **Audit de sécurité** : gestion des secrets, surface d'exposition. **Audit de performance** : respect des SLA et taux d'escalade | La **traçabilité intégrée** (historique des états, RN-07, métriques) réduit le coût d'un audit ; la conformité évite des sanctions et rassure les clients. Le mode Ollama local permet de **ne pas faire sortir les données** si un client l'exige |
| **Opérateur réseau** (*Cloud Carrier* / *Operador*) | Les **fournisseurs d'accès à Internet** des clients, le **réseau du fournisseur cloud**, et Internet entre la plateforme et l'API Anthropic | Transporter les requêtes HTTPS entre les clients et `api-gateway`, et entre `servicio-agente-triaje` et Anthropic | Coût du **trafic sortant** (*egress*) facturé par le fournisseur cloud ; la latence réseau s'ajoute à celle du LLM. Les payloads JSON compacts et le cache limitent ce trafic |

### 5.3 Analyse de l'impact économique

**1. Passage de CapEx à OpEx** (*inversión de capital → gasto operativo*)
Aucun achat de serveurs ni de GPU : toutes les ressources sont louées et payées à l'usage. Le risque financier au lancement du service est donc faible.

**2. Coût d'un ticket trié** — le service mesuré (section 4.6) permet de le calculer :

```
Coût par ticket = Coût LLM + Coût infrastructure / nombre de tickets

Coût LLM (Anthropic) = tokens_entrée × prix_entrée + tokens_sortie × prix_sortie
Coût LLM (Ollama)    = 0 € d'API, mais du temps de calcul (CPU/GPU) facturé par le fournisseur cloud
```

Les prix unitaires seront relevés sur les pages tarifaires officielles au moment du déploiement ; les volumes de tokens seront mesurés grâce à RN-07.

**3. Leviers de réduction des coûts intégrés à l'architecture**

| Levier | Effet |
|---|---|
| Fournisseur local Ollama en priorité | Pas de coût par token pour le cas nominal |
| Cache Redis des tris | Un ticket identique (ex. panne massive) n'est payé qu'une fois |
| Élasticité (section 4.5) | On ne paie la capacité de pointe que pendant les pics |
| Rate limiting au gateway | Empêche un client d'épuiser le budget d'API |
| Tri par règles en dernier recours | Le service continue sans aucun coût d'IA |

**4. Retour sur investissement pour les entreprises clientes**
Si un agent humain passe en moyenne *t* minutes à trier un ticket, l'économie mensuelle est d'environ `tickets_par_mois × t × coût_horaire_agent / 60`, à comparer au prix payé à TriageAI. La validation humaine reste obligatoire (RN-05), mais elle est plus rapide qu'un tri complet.

## 6. Architecture

### 6.1 Style et principes architecturaux

TriageAI suit une **architecture de microservices** (*arquitectura de microservicios*) orientée cloud-native :

| Principe | Application dans TriageAI |
|---|---|
| **Découpage par responsabilité métier** | Un service pour le cycle de vie des tickets, un service pour l'agent IA |
| **Point d'entrée unique** (*API Gateway*) | Seul le gateway est exposé à l'extérieur ; les autres services restent sur le réseau interne |
| **Services sans état** (*stateless*) | Aucun état en mémoire : les données vivent dans PostgreSQL et Redis, ce qui permet de répliquer les services |
| **Base de données par service** (*database per service*) | Chaque service possède son propre schéma ; aucun service ne lit les tables d'un autre |
| **Communication asynchrone pour les tâches lentes** | Le tri IA (plusieurs secondes) est déclenché par un événement, pas par un appel bloquant |
| **Configuration externalisée** | Ordre des fournisseurs LLM, timeouts, délais SLA : lus depuis le Config Server, modifiables sans recompiler |
| **Conception pour la défaillance** (*design for failure*) | Circuit breaker, basculement entre fournisseurs LLM, dégradation contrôlée par règles |

### 6.2 Diagramme de composants

![Diagramme de composants TriageAI](docs/img/arquitectura-componentes.svg)

> Le **plan de contrôle** (découverte de services et configuration) est représenté séparément en 6.3 pour garder ce diagramme lisible.

### 6.3 Plan de contrôle : découverte et configuration

```mermaid
flowchart LR
    subgraph SVC["Services Spring Boot"]
        GW["api-gateway"]
        TK["servicio-tickets"]
        AG["servicio-agente-triaje"]
    end

    DS["<b>discovery-server</b><br/>Eureka :8761"]
    CS["<b>config-server</b><br/>Spring Cloud Config :8888"]
    GIT[("<b>config-repo</b><br/>dépôt Git<br/>application.yml par service")]

    SVC -- "① HTTP · lecture de la config au démarrage" --> CS
    CS -- "HTTPS · git clone / pull" --> GIT
    SVC -- "② HTTP · enregistrement + heartbeat (30 s)" --> DS
    GW -. "③ résolution lb://nom-du-service" .-> DS
```

**Ordre de démarrage :** `config-server` → `discovery-server` → `servicio-tickets` / `servicio-agente-triaje` → `api-gateway`.

**Exemples de propriétés centralisées :**

```yaml
# config-repo/servicio-agente-triaje.yml
llm:
  proveedor-primario: ollama        # anthropic en production
  proveedor-secundario: anthropic
  timeout-segundos: 60
  umbral-confianza: 0.7             # RN-04
resilience4j.circuitbreaker.instances:
  ollama:    { failureRateThreshold: 50, waitDurationInOpenState: 60s }
  anthropic: { failureRateThreshold: 50, waitDurationInOpenState: 60s }
```

### 6.4 Flux principal : création et tri d'un ticket

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant GW as api-gateway
    participant TK as servicio-tickets
    participant RD as Redis
    participant AG as servicio-agente-triaje
    participant OL as Ollama
    participant AN as API Anthropic

    C->>GW: POST /api/v1/tickets
    GW->>TK: POST /tickets
    TK->>TK: validation · état NOUVEAU · calcul des échéances SLA
    TK->>RD: XADD ticket-events {ticket.creado}
    TK-->>GW: 201 Created {id, estado: NOUVEAU}
    GW-->>C: 201 Created

    RD-->>AG: XREADGROUP → ticket.creado
    alt Circuit Ollama fermé (CLOSED)
        AG->>OL: POST /api/chat (ticket + définition des outils)
        OL-->>AG: demande d'outil : obtenerHistorialCliente
        AG->>TK: GET /internal/clientes/{id}/historial
        TK-->>AG: historique du client
        AG->>OL: POST /api/chat (résultat de l'outil)
        OL-->>AG: JSON {categoria, prioridad, resumen, confianza, borrador}
    else Ollama en échec (timeout · erreur · JSON invalide) ou circuit ouvert
        AG->>AN: POST /v1/messages (même boucle de tool calling)
        AN-->>AG: JSON {categoria, prioridad, resumen, confianza, borrador}
    end

    alt Tri obtenu
        AG->>TK: PUT /internal/tickets/{id}/triaje {modo: IA, ..., trazabilidad}
        TK->>TK: NOUVEAU → TRIÉ · révision manuelle si confiance < 0,7
    else Les deux fournisseurs ont échoué
        AG->>TK: PUT /internal/tickets/{id}/triaje {modo: REGLAS}
        TK->>TK: tri par mots-clés · NOUVEAU → EN_ATTENTE_DE_TRI
    end
    AG->>RD: XACK (événement traité)
```

**Pourquoi un tri asynchrone ?** Un appel LLM peut prendre de 2 à 60 secondes. Le client reçoit immédiatement `201 Created` ; le tri se fait en arrière-plan. Si `servicio-agente-triaje` est arrêté, les événements restent dans le stream Redis et seront traités à son redémarrage : **aucun ticket n'est perdu**.

### 6.5 Catalogue des composants

| Composant | Type | Technologie | Port | Exposé | Responsabilité |
|---|---|---|---|---|---|
| `api-gateway` | Infrastructure | Spring Cloud Gateway | 8080 | ✅ | Routage, rate limiting, point d'entrée unique |
| `discovery-server` | Infrastructure | Spring Cloud Netflix Eureka | 8761 | ❌ | Registre de services |
| `config-server` | Infrastructure | Spring Cloud Config | 8888 | ❌ | Configuration centralisée depuis Git |
| `servicio-tickets` | Métier | Spring Boot, Spring Data JPA | 8081 | ❌ | Cycle de vie, SLA, escalade, tri par règles, tâches planifiées |
| `servicio-agente-triaje` | Métier | Spring Boot, Spring AI, Resilience4j | 8082 | ❌ | Agent IA, tool calling, routage et basculement LLM |
| `postgres` | Données | PostgreSQL | 5432 | ❌ | Persistance relationnelle |
| `redis` | Données | Redis | 6379 | ❌ | Cache, rate limiting, bus d'événements (Streams) |
| `ollama` | IA | Ollama | 11434 | ❌ | Inférence LLM locale |
| API Anthropic | IA externe | SaaS | 443 | — | Inférence LLM de secours |

### 6.6 Protocoles de communication

| Source → Destination | Protocole | Mode | Format | Usage |
|---|---|---|---|---|
| Client → `api-gateway` | HTTPS | Synchrone | REST / JSON | Toutes les requêtes externes |
| `api-gateway` → services métier | HTTP | Synchrone | REST / JSON | Routage via `lb://` (Eureka) |
| `servicio-tickets` → Redis Streams | RESP | **Asynchrone** | Événement JSON | Publication de `ticket.creado` |
| Redis Streams → `servicio-agente-triaje` | RESP | **Asynchrone** | Événement JSON | Consommation par groupe de consommateurs |
| `servicio-agente-triaje` → `servicio-tickets` | HTTP | Synchrone | REST / JSON | Outils de l'agent + enregistrement du tri |
| `servicio-agente-triaje` → Ollama | HTTP | Synchrone | JSON (`/api/chat`) | Inférence primaire |
| `servicio-agente-triaje` → Anthropic | HTTPS | Synchrone | JSON (Messages API) | Inférence secondaire |
| Services → PostgreSQL | JDBC | Synchrone | SQL | Persistance |
| Services → `config-server` | HTTP | Au démarrage | JSON / YAML | Lecture de la configuration |
| Services → `discovery-server` | HTTP | Périodique | JSON | Enregistrement et heartbeat |

### 6.7 Stockage des données

| Stockage | Propriétaire | Contenu principal | Persistance |
|---|---|---|---|
| PostgreSQL — schéma `tickets` | `servicio-tickets` | `ticket`, `historial_estado`, `triaje`, `respuesta` | Permanente (volume Docker) |
| PostgreSQL — schéma `agente` | `servicio-agente-triaje` | `llamada_llm` : fournisseur, modèle, tokens, latence, succès/échec (RN-07) | Permanente (volume Docker) |
| Redis — `ticket-events` | `servicio-tickets` (producteur) | Événements `ticket.creado` | Jusqu'à acquittement (`XACK`) |
| Redis — cache des tris | `servicio-agente-triaje` | Résultat du tri indexé par empreinte du texte (doublons, ex. panne massive) | Temporaire (TTL) |
| Redis — rate limiting | `api-gateway` | Compteurs de requêtes par client | Temporaire |
| Volume `ollama-models` | `ollama` | Poids des modèles téléchargés | Permanente |

> Une seule instance PostgreSQL avec **un schéma par service** : l'isolation logique respecte le principe *database per service* tout en limitant la consommation de ressources.

### 6.8 Résilience et sécurité

| Aspect | Mesure |
|---|---|
| Panne d'un fournisseur LLM | Circuit breaker Resilience4j par fournisseur (`CLOSED` → `OPEN` → `HALF_OPEN`) + timeout configurable |
| Panne des deux fournisseurs | Tri par règles + état `EN_ATTENTE_DE_TRI` + nouvel essai planifié (RN-06) |
| Panne du service agent | Événements conservés dans le stream Redis jusqu'au redémarrage |
| Surcharge / abus | Rate limiting au gateway (Redis) |
| Secrets | `ANTHROPIC_API_KEY` et mots de passe en variables d'environnement, jamais dans Git (`.env` dans `.gitignore`) |
| Surface d'exposition | Seul `api-gateway` publie un port ; les autres services sont uniquement sur le réseau interne |
| Supervision | Spring Boot Actuator (`/actuator/health`) sur chaque service — base de l'observabilité du Hito 5 |

### 6.9 Décisions d'architecture

| Décision | Alternatives considérées | Justification |
|---|---|---|
| **Redis Streams** comme bus d'événements | RabbitMQ, Kafka | Redis est déjà présent (cache, rate limiting) : un composant de moins à exploiter ; volume d'événements modeste |
| **Un PostgreSQL, un schéma par service** | Une instance par service | Isolation logique suffisante pour le projet, consommation mémoire réduite |
| **Tri asynchrone** | Appel REST synchrone gateway → agent | Latence LLM élevée et variable ; absorbe les pics de tickets |
| **Eureka + Config Server** | Découverte et configuration natives de Kubernetes | Indépendant de la plateforme de déploiement ; à réévaluer au Hito 3 (Spring Cloud Kubernetes) |
| **Deux fournisseurs LLM** | Un seul fournisseur | Continuité de service et maîtrise des coûts (local gratuit / API payante à l'usage) |

## 7. Spécification de l'API REST

### 7.1 Conventions générales

| Élément | Convention |
|---|---|
| **URL de base (externe)** | `http://localhost:8080/api/v1` — toutes les requêtes externes passent par `api-gateway` |
| **Versionnement** | Dans le chemin (`/api/v1`) ; une évolution incompatible donnera `/api/v2` |
| **Format** | JSON (`Content-Type: application/json`), encodage UTF-8 |
| **Dates** | ISO 8601 en UTC (`2026-10-13T14:05:00Z`) |
| **Identifiants** | UUID |
| **Pagination** | `?page=0&size=20` ; la réponse contient `contenido`, `pagina`, `tamano`, `totalElementos` |
| **Erreurs** | Format standard *Problem Details* (RFC 9457, `application/problem+json`), pris en charge nativement par Spring Boot |
| **Documentation vivante** | Générée par **springdoc-openapi** : Swagger UI sur `/swagger-ui.html`, contrat OpenAPI sur `/v3/api-docs` |
| **Authentification** | Hors périmètre du MVP ; l'identifiant du client est transmis dans le corps de la requête |

**Routage du gateway :** `api-gateway` retire le préfixe `/api/v1` avant de transmettre la requête.

| Chemin externe | Service cible | Chemin interne |
|---|---|---|
| `/api/v1/tickets/**` | `servicio-tickets` | `/tickets/**` |
| `/api/v1/triaje/**` | `servicio-agente-triaje` | `/triaje/**` |

Les chemins `/internal/**` ne sont **jamais routés** par le gateway : ils ne sont accessibles que depuis le réseau Docker interne.

### 7.2 Valeurs énumérées

| Champ | Valeurs possibles |
|---|---|
| `estado` | `NOUVEAU`, `EN_ATTENTE_DE_TRI`, `TRIE`, `EN_COURS`, `RESOLU`, `FERME` (RN-01) |
| `prioridad` | `CRITIQUE`, `HAUTE`, `MOYENNE`, `BASSE` (RN-02) |
| `categoria` | `INCIDENT`, `FACTURATION`, `COMPTE`, `QUESTION`, `AUTRE` |
| `modo` (du tri) | `IA` (tri par LLM), `REGLAS` (tri de secours par mots-clés) |
| `proveedor` | `OLLAMA`, `ANTHROPIC`, `NINGUNO` (tri par règles) |

### 7.3 Vue d'ensemble des endpoints

#### API publique — `servicio-tickets` (via le gateway)

| Verbe | Route | Description | Cas d'utilisation | Réponses |
|---|---|---|---|---|
| `POST` | `/api/v1/tickets` | Créer un ticket | CU-01 | `201`, `400` |
| `GET` | `/api/v1/tickets` | Lister les tickets (filtres : `estado`, `prioridad`, `categoria`, `escalado`, `revisionManual`, `clienteId`) | CU-02, CU-04 | `200` |
| `GET` | `/api/v1/tickets/{id}` | Consulter un ticket, son tri et ses échéances SLA | CU-02 | `200`, `404` |
| `GET` | `/api/v1/tickets/{id}/historial` | Historique des changements d'état | CU-02 | `200`, `404` |
| `PATCH` | `/api/v1/tickets/{id}/triaje` | Corriger le tri proposé par l'IA (catégorie, priorité) | CU-04 | `200`, `404`, `409` |
| `POST` | `/api/v1/tickets/{id}/respuesta` | Envoyer une réponse validée par un agent humain | CU-04 | `201`, `404`, `409` |
| `PATCH` | `/api/v1/tickets/{id}/estado` | Changer l'état du ticket | CU-05 | `200`, `404`, `409` |
| `POST` | `/api/v1/tickets/{id}/reapertura` | Rouvrir un ticket résolu (≤ 7 jours) | CU-09 *(extension)* | `200`, `404`, `409` |

#### API publique — `servicio-agente-triaje` (via le gateway)

| Verbe | Route | Description | Cas d'utilisation | Réponses |
|---|---|---|---|---|
| `GET` | `/api/v1/triaje/proveedores` | État des fournisseurs LLM et de leurs circuit breakers | CU-08 | `200` |
| `POST` | `/api/v1/triaje/tickets/{id}/reintento` | Relancer manuellement le tri IA d'un ticket | CU-07 | `202`, `404`, `409` |
| `GET` | `/api/v1/triaje/metricas` | Consommation par fournisseur : appels, tokens, latence, taux d'échec (`?desde=&hasta=`) | CU-08 *(extension)* | `200` |

#### API interne — `servicio-tickets` (réseau interne uniquement)

Endpoints utilisés par l'agent IA, soit comme **outils** (*tool calling*), soit pour enregistrer le résultat.

| Verbe | Route | Appelant | Description | Réponses |
|---|---|---|---|---|
| `GET` | `/internal/tickets/{id}` | Agent IA | Lire le ticket à trier | `200`, `404` |
| `GET` | `/internal/clientes/{clienteId}/historial` | Agent IA (outil `obtenerHistorialCliente`) | Derniers tickets du client et leur résolution | `200` |
| `PUT` | `/internal/tickets/{id}/triaje` | Agent IA | Enregistrer le résultat du tri (IA ou échec → règles) | `200`, `404`, `409` |

#### Contrat asynchrone — Redis Streams

| Stream | Événement | Producteur | Consommateur (groupe) |
|---|---|---|---|
| `ticket-events` | `ticket.creado` | `servicio-tickets` | `servicio-agente-triaje` (groupe `triaje`) |

#### Endpoints techniques (non exposés)

`GET /actuator/health` sur chaque service : utilisé pour la supervision et les *health checks* des conteneurs.

### 7.4 Détail des endpoints principaux

#### `POST /api/v1/tickets` — Créer un ticket (CU-01)

Requête :

```json
{
  "clienteId": "c2a9f1d4-7b3e-4c1a-9e55-0d2f8b6a4e10",
  "titulo": "Impossible de me connecter à mon compte",
  "descripcion": "Depuis ce matin, j'obtiens l'erreur 500 après avoir saisi mon mot de passe. Toute l'équipe est bloquée."
}
```

Réponse `201 Created` (en-tête `Location: /api/v1/tickets/8b1d3c52-4e7a-4f0e-9a51-2c7d9e6f1a30`) :

```json
{
  "id": "8b1d3c52-4e7a-4f0e-9a51-2c7d9e6f1a30",
  "estado": "NOUVEAU",
  "creadoEn": "2026-10-13T14:05:00Z"
}
```

Validation : `titulo` obligatoire (5 à 150 caractères), `descripcion` obligatoire (10 à 5 000 caractères), `clienteId` obligatoire.
Effet de bord : publication de l'événement `ticket.creado` ; le tri se fait ensuite en arrière-plan.

#### `GET /api/v1/tickets/{id}` — Consulter un ticket (CU-02)

Réponse `200 OK` (après le tri par l'IA) :

```json
{
  "id": "8b1d3c52-4e7a-4f0e-9a51-2c7d9e6f1a30",
  "clienteId": "c2a9f1d4-7b3e-4c1a-9e55-0d2f8b6a4e10",
  "titulo": "Impossible de me connecter à mon compte",
  "descripcion": "Depuis ce matin, j'obtiens l'erreur 500 après avoir saisi mon mot de passe. Toute l'équipe est bloquée.",
  "estado": "TRIE",
  "categoria": "INCIDENT",
  "prioridad": "CRITIQUE",
  "escalado": false,
  "revisionManual": false,
  "sla": {
    "primeraRespuestaAntes": "2026-10-13T15:05:00Z",
    "resolucionAntes": "2026-10-13T22:05:00Z"
  },
  "triaje": {
    "modo": "IA",
    "proveedor": "OLLAMA",
    "confianza": 0.91,
    "resumen": "Erreur 500 à la connexion bloquant toute une équipe depuis ce matin.",
    "borradorRespuesta": "Bonjour, nous avons bien identifié le problème de connexion et notre équipe technique est mobilisée…"
  },
  "creadoEn": "2026-10-13T14:05:00Z",
  "actualizadoEn": "2026-10-13T14:05:07Z"
}
```

> Le brouillon (`borradorRespuesta`) n'est **jamais** envoyé automatiquement au client (RN-05).

#### `PATCH /api/v1/tickets/{id}/triaje` — Corriger le tri (CU-04)

Requête (agent de support) :

```json
{
  "categoria": "COMPTE",
  "prioridad": "HAUTE",
  "motivo": "Problème limité à un seul compte, pas un incident global."
}
```

Réponse `200 OK` : le ticket complet, avec `triaje.corregidoPorHumano: true`. Les délais SLA sont recalculés selon la nouvelle priorité.

#### `POST /api/v1/tickets/{id}/respuesta` — Envoyer une réponse validée (CU-04)

Requête :

```json
{
  "agenteId": "a71e0c3b-2f4d-4e8a-b6c9-5d1e3f7a9b20",
  "texto": "Bonjour, nous avons réinitialisé votre session. Pouvez-vous réessayer de vous connecter ?",
  "basadoEnBorrador": true
}
```

Réponse `201 Created` :

```json
{
  "respuestaId": "f3b8a2d1-6c4e-4b7f-8a9d-1e2c3b4a5f60",
  "ticketId": "8b1d3c52-4e7a-4f0e-9a51-2c7d9e6f1a30",
  "estado": "EN_COURS",
  "enviadaEn": "2026-10-13T14:20:00Z"
}
```

Règles : autorisé uniquement si le ticket est `TRIE` ou `EN_COURS`, sinon `409`. La première réponse arrête le chronomètre SLA de « première réponse ».

#### `PATCH /api/v1/tickets/{id}/estado` — Changer l'état (CU-05)

Requête :

```json
{
  "estado": "RESOLU",
  "comentario": "Session réinitialisée, le client confirme que l'accès fonctionne."
}
```

Réponse `200 OK` : le ticket complet avec son nouvel état.

Exemple d'erreur `409 Conflict` (transition non autorisée par RN-01) :

```json
{
  "type": "https://triageai.example/errores/transicion-invalida",
  "title": "Transition d'état non autorisée",
  "status": 409,
  "detail": "Impossible de passer de NOUVEAU à RESOLU.",
  "instance": "/api/v1/tickets/8b1d3c52-4e7a-4f0e-9a51-2c7d9e6f1a30/estado",
  "transicionesPermitidas": ["TRIE", "EN_ATTENTE_DE_TRI"]
}
```

#### `PUT /internal/tickets/{id}/triaje` — Enregistrer le résultat du tri (interne)

Cas 1 — tri réussi par l'IA :

```json
{
  "modo": "IA",
  "categoria": "INCIDENT",
  "prioridad": "CRITIQUE",
  "confianza": 0.91,
  "resumen": "Erreur 500 à la connexion bloquant toute une équipe depuis ce matin.",
  "borradorRespuesta": "Bonjour, nous avons bien identifié le problème…",
  "trazabilidad": {
    "proveedor": "OLLAMA",
    "modelo": "<modèle configuré>",
    "tokensEntrada": 812,
    "tokensSalida": 164,
    "latenciaMs": 6430,
    "intentos": 1
  }
}
```

Cas 2 — les deux fournisseurs ont échoué :

```json
{
  "modo": "REGLAS",
  "motivoFallo": "OLLAMA: timeout ; ANTHROPIC: HTTP 429",
  "trazabilidad": { "proveedor": "NINGUNO", "intentos": 2 }
}
```

Réponse `200 OK`. Le service applique RN-04 (révision manuelle si `confianza < 0,7`) ou RN-06 (tri par mots-clés, état `EN_ATTENTE_DE_TRI`). Les valeurs `tokens` et `latenciaMs` alimentent RN-07.

#### `GET /internal/clientes/{clienteId}/historial` — Outil de l'agent

Réponse `200 OK` (5 derniers tickets) :

```json
{
  "clienteId": "c2a9f1d4-7b3e-4c1a-9e55-0d2f8b6a4e10",
  "totalTickets": 7,
  "ticketsRecientes": [
    { "id": "1c7e…", "titulo": "Facture en double", "categoria": "FACTURATION", "prioridad": "MOYENNE", "estado": "FERME", "creadoEn": "2026-09-28T09:12:00Z" }
  ]
}
```

#### `GET /api/v1/triaje/proveedores` — État des fournisseurs LLM

Réponse `200 OK` :

```json
{
  "orden": ["OLLAMA", "ANTHROPIC"],
  "proveedores": [
    { "nombre": "OLLAMA",    "circuito": "CLOSED", "tasaFallos": 0.04, "latenciaMediaMs": 5200 },
    { "nombre": "ANTHROPIC", "circuito": "CLOSED", "tasaFallos": 0.00, "latenciaMediaMs": 1900 }
  ]
}
```

#### Événement `ticket.creado` (Redis Streams)

```json
{
  "eventoId": "e4d2c1b0-9a8f-4e7d-8c6b-5a4f3e2d1c00",
  "tipo": "ticket.creado",
  "version": 1,
  "ticketId": "8b1d3c52-4e7a-4f0e-9a51-2c7d9e6f1a30",
  "ocurridoEn": "2026-10-13T14:05:00Z"
}
```

L'événement ne transporte que l'identifiant : l'agent lit le contenu à jour via `GET /internal/tickets/{id}`. Le champ `version` permet de faire évoluer le format sans casser le consommateur.

### 7.5 Codes de réponse utilisés

| Code | Signification | Exemple |
|---|---|---|
| `200 OK` | Lecture ou modification réussie | `GET /tickets/{id}` |
| `201 Created` | Ressource créée (en-tête `Location`) | `POST /tickets` |
| `202 Accepted` | Traitement accepté, exécuté en arrière-plan | `POST /triaje/tickets/{id}/reintento` |
| `400 Bad Request` | Données invalides (validation) | `titulo` manquant |
| `404 Not Found` | Ressource inexistante | identifiant inconnu |
| `409 Conflict` | Opération incompatible avec l'état actuel | transition `NOUVEAU → RESOLU` |
| `429 Too Many Requests` | Limite de débit dépassée (gateway) | trop de requêtes d'un même client |
| `503 Service Unavailable` | Service cible indisponible | service métier arrêté |

## 8. Gestion du projet

> 🚧 *À compléter* — stratégie de branches, convention de commits, GitHub Projects, user stories, templates.

## 9. État du projet

| Hito | Contenu | État |
|---|---|---|
| Hito 1 | Définition, modèle métier, architecture de référence | 🟡 En cours |
| Hito 2 | Empaquetage cloud native, environnement local, CI | ⚪ |
| Hito 3 | Provisionnement IaC, Reconciler Pattern | ⚪ |
| Hito 4 | Déploiement continu, réconciliation, sécurité | ⚪ |
| Hito 5 | Observabilité, tests de charge, release finale | ⚪ |
