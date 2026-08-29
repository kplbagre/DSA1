# Salesforce Signup & ISV Team — What They Build and Why It Matters
### A teach-from-scratch guide so you walk in knowing the team, not just the company

> **Who this is for:** You — a Java/Kafka backend engineer who knows Walmart's systems cold but has never worked with Salesforce products. This file explains what the team builds, what the jargon means, and why your experience maps to it. After reading this, you should be able to hold a conversation about the team's products without sounding like you just read a JD.
>
> **How to read:** Start at §1 (what Salesforce actually is) and go sequentially — each section builds on the previous one. Skip nothing on first read; it's all connected.

---

## 🧾 Table of Contents

1. [What Salesforce actually is (the 60-second version)](#1--what-salesforce-actually-is)
2. [What an "Org" is — the single most important concept](#2--what-an-org-is)
3. [What ISV means and why it matters](#3--what-isv-means)
4. [What AppExchange is (Salesforce's app store)](#4--what-appexchange-is)
5. [What this team builds — product by product, explained](#5--what-this-team-builds)
6. [The trial lifecycle — how a prospect becomes a customer](#6--the-trial-lifecycle)
7. [Scratch orgs and developer experience](#7--scratch-orgs)
8. [The tech under the hood — how it's built](#8--the-tech-under-the-hood)
9. [Scale numbers you should know](#9--scale-numbers)
10. [Why YOUR experience maps to THIS team](#10--why-your-experience-maps)
11. [Questions to ask the HM (informed, not generic)](#11--questions-to-ask-the-hm)

---

## 1. — What Salesforce actually is

Before the team makes sense, you need the product context.

**Salesforce is a cloud platform that businesses use to manage their customers.** Think of it as a giant online system where a company tracks every interaction with every customer — sales calls, support tickets, marketing emails, orders, contracts. It's called a **CRM** (Customer Relationship Management — a system that stores and organizes everything a business knows about its customers).

**The key insight:** Salesforce is NOT software you install on your computer. It runs entirely in the cloud — you open a browser, log in, and it's there. The company never touches a server. This is what "SaaS" means (Software as a Service — software delivered over the internet, not installed locally).

```
🎨 Visual — what Salesforce looks like to its users

  A sales rep at Company X opens their browser:
  ┌──────────────────────────────────────────────────┐
  │  Salesforce: Company X's Dashboard                │
  │                                                    │
  │  Today's Leads (15)     Open Deals ($2.3M)        │
  │  ├── John Smith, Acme Corp — called yesterday     │
  │  ├── Lisa Park, TechCo — demo scheduled Friday    │
  │  └── ...                                           │
  │                                                    │
  │  Support Cases (7)      Emails Sent Today (340)    │
  │                                                    │
  │  Everything here is Company X's data,              │
  │  running on Salesforce's shared infrastructure.    │
  └──────────────────────────────────────────────────┘

  Company Y logs into the SAME Salesforce system,
  but sees ONLY their own data. They never see Company X's.
  This is multi-tenancy.
```

**Why this matters for you:** Salesforce serves **hundreds of thousands of companies** on shared infrastructure. Every company thinks they have their own private system, but underneath, they're all running on the same servers, same databases, same code. **The engineering challenge is isolation at massive scale** — which is exactly what your multi-market/multi-tenant MCSE experience maps to.

---

## 2. — What an "Org" is

**An "org" (organization) is one company's private space inside Salesforce.** It's the single most important concept for this team.

When Company X signs up for Salesforce, they get an **org**. That org contains:
- Their data (customers, deals, support tickets)
- Their customizations (custom fields, workflows, automation)
- Their users (sales reps, managers, admins)
- Their installed apps (from AppExchange — explained in §4)

**The technical reality:** an org is NOT a separate database or server. It's a **logical partition** inside a shared database. All orgs on one database instance share the same Oracle tables — they're distinguished by an **OrgID** column. One database instance holds ~8,000–10,000 orgs.

```
🎨 Visual — orgs share infrastructure (multi-tenancy)

  Oracle Database Instance #47
  ┌──────────────────────────────────────────────┐
  │  Table: Accounts                              │
  │  ┌────────┬──────────┬───────────────────┐   │
  │  │ OrgID  │ AcctName │ Phone             │   │
  │  ├────────┼──────────┼───────────────────┤   │
  │  │ 00D1   │ Acme     │ 555-0100          │   │  ← Company X's data
  │  │ 00D1   │ TechCo   │ 555-0200          │   │  ← Company X's data
  │  │ 00D2   │ BigBank  │ 555-0300          │   │  ← Company Y's data
  │  │ 00D2   │ MedCorp  │ 555-0400          │   │  ← Company Y's data
  │  └────────┴──────────┴───────────────────┘   │
  │                                                │
  │  Company X (OrgID=00D1) can ONLY see rows     │
  │  where OrgID=00D1. Company Y sees only 00D2.  │
  │  Same table, same database, complete isolation.│
  └──────────────────────────────────────────────┘

  KEY INVARIANT:
     Multi-tenancy = shared infrastructure, isolated data.
     The OrgID column is the tenant boundary.
     (This is conceptually identical to your BU/market isolation in MCSE.)
```

**Types of orgs** (this team provisions ALL of them):

| Org type | What it is | Analogy |
| --- | --- | --- |
| **Production org** | A paying customer's live environment | Your production MCSE deployment |
| **Trial org** | A free trial for a prospect (14–30 days, then expires or converts to paid) | A staging/canary environment that auto-deletes |
| **Developer org** | A free org for developers to build and test on | Your dev/test namespace |
| **Scratch org** | A temporary, disposable org spun up from CLI for development; destroyed after use | A throwaway Docker container for local testing |
| **Sandbox org** | A copy of a production org for testing (admins clone their prod) | Your stage environment cloned from prod |
| **Partner org** | An ISV partner's development org for building apps | — |

**The team's core job:** when *any* of these orgs is created, **this team's code runs**. They decide which database instance it lands on, insert the tenant metadata, configure it, and manage its lifecycle (creation → active use → expiration/conversion/deletion).

---

## 3. — What ISV means

**ISV = Independent Software Vendor** — an **external company** (NOT Salesforce) that builds software products that run *on top of* Salesforce.

> ⚠️ **Critical clarification:** the Salesforce ISV **Tools** team does NOT build the ISV apps. DocuSign's engineers build DocuSign. Veeva's engineers build Veeva. This team builds the **infrastructure and tooling** that those external companies use to build, test, package, distribute, and manage their apps on Salesforce. Think of the difference between Apple building Xcode (developer tools) vs Apple building Instagram (they don't).

Think of it like the iPhone:

```
🎨 Visual — who builds what (the critical distinction)

  ┌──────────────────────────────────────────────────────────────────┐
  │  Apple builds:                  Salesforce builds:               │
  │    iOS (the platform)             CRM platform                   │
  │    App Store (marketplace)        AppExchange (marketplace)      │
  │    Xcode (developer tools)        ISV Tools ← THIS TEAM         │
  │    TestFlight (testing)           Trialforce (trial creation)    │
  │                                                                  │
  │  Apple does NOT build:          Salesforce does NOT build:       │
  │    Instagram                      DocuSign                       │
  │    Uber                           Veeva                          │
  │    WhatsApp                       Conga                          │
  │    (those companies build         (those companies build         │
  │     their own apps using           their own apps using          │
  │     Apple's tools)                 Salesforce's tools)           │
  └──────────────────────────────────────────────────────────────────┘

  KEY INSIGHT:
     This team builds the TOOLS, not the apps.
     6,700+ ISV companies build their own apps.
     This team provides the infrastructure they ALL depend on.
```

```
🎨 Visual — Salesforce platform with ISV apps on top

  Salesforce (the platform):
  ┌─────────────────────────────────────────────┐
  │  Core CRM: accounts, contacts, deals,       │
  │  reports, dashboards, workflows              │
  │                                               │
  │  ┌─────────┐ ┌─────────┐ ┌──────────┐       │
  │  │ DocuSign│ │  Veeva  │ │  Conga   │       │
  │  │ (built  │ │ (built  │ │ (built   │       │
  │  │  by     │ │  by     │ │  by      │       │
  │  │ DocuSign│ │  Veeva  │ │  Conga   │       │
  │  │  Inc.)  │ │  Inc.)  │ │  Inc.)   │       │
  │  └─────────┘ └─────────┘ └──────────┘       │
  │                                               │
  │  ──── What does the ISV Tools team do? ────  │
  │  They build the INFRASTRUCTURE that lets      │
  │  DocuSign/Veeva/Conga:                        │
  │   • create developer orgs to build on         │
  │   • create trial orgs for their prospects     │
  │   • package their app for distribution        │
  │   • list it on AppExchange                    │
  │   • track licenses (trial vs paid)            │
  └─────────────────────────────────────────────┘
```

**The Walmart analogy that makes it click:** You don't build the shopping website — you build the promise & sourcing engine that the shopping website depends on. Similarly, this team doesn't build DocuSign — they build the org provisioning and ISV tooling that DocuSign depends on. **Both are backend infrastructure teams that power the platform for others.**

**Real-world ISV examples** (these are external companies, NOT Salesforce employees):
- **DocuSign** — electronic signatures inside Salesforce
- **Veeva** — pharma industry CRM built on Salesforce
- **Conga** — document generation from Salesforce data
- **Copado** — DevOps tools for Salesforce development

**Why ISVs matter to this team:** ISVs need **tooling** from Salesforce to build, test, package, and distribute their apps. This team builds that tooling:
- ISVs need **trial orgs** so prospects can try their app → this team's Trialforce system creates them
- ISVs need **developer orgs** to build on → this team provisions them
- ISVs need to **track licenses** (who has a trial, who's paid) → this team's License Management App
- ISVs need to **list on AppExchange** → this team's infrastructure powers it

**The one-liner for the room:** *"ISVs are third-party companies that build apps on top of Salesforce and sell them through AppExchange. This team builds the infrastructure those ISVs depend on — from provisioning their development environments to managing the trial-to-paid lifecycle of their customers."*

---

## 4. — What AppExchange is

**AppExchange is Salesforce's app marketplace** — like the App Store for iPhone, but for business software.

- **6,700+** apps listed
- **9 million+** installs
- Ecosystem valued at **$1 billion+**

When a Salesforce customer wants to add a capability (e.g., "I need e-signatures in my CRM"), they go to AppExchange, find an app (e.g., DocuSign), and **install it into their org**. The app then has access to their data (with permission) and adds new functionality.

**What this team owns in AppExchange:**
- The **infrastructure** that lets ISVs **package** their app and **publish** it to AppExchange
- The **installation** mechanism (when a customer clicks "Get It Now," this team's code installs the package into their org)
- The **listing** infrastructure across multiple Salesforce technologies (Force.com, Mulesoft, Tableau, Slack — not just CRM anymore)

**Why it maps to you:** AppExchange package installation is a **backward-compatible contract change** — installing an ISV app into a customer's org must never break their existing configuration. That's exactly the challenge you solved with multi-slot (adding `slots[]` without breaking four existing consumers).

---

## 5. — What this team builds — product by product

Now that you know what orgs, ISVs, and AppExchange are, here's what the team actually owns:

### 5.1 Org Provisioning System

**What it does:** When anyone — a free trial prospect, an ISV developer, a CLI command, a paying customer — creates a new Salesforce org, **this system runs**. It:
1. Decides which database instance the new org goes to (capacity planning / routing)
2. Inserts the tenant metadata into the shared Oracle tables
3. Configures the org (edition, features, limits)
4. Makes it available to the user

**The engineering challenge:** This isn't "create a database." It's "insert rows into a shared database that already holds 8,000 other tenants, configure the right feature flags, and make it available — without affecting any existing tenant." At scale, with millions of orgs.

**Your bridge:** This is conceptually similar to your Kafka ingestion tier — ingest a new entity (org / offer), route it to the right store, configure it, make it available on the read path.

### 5.2 Trialforce (trial org creation system)

**What it does:** Lets ISV partners create **pre-configured trial orgs** for their prospects.

**How it works:**
```
Step 1: ISV sets up a "source org" — installs their app, adds sample data,
        configures it exactly how they want a prospect to experience it.

Step 2: ISV creates a "template" from that source org — a snapshot.

Step 3: When a prospect clicks "Start Free Trial" on the ISV's website:
        → The template is used to provision a new trial org
        → The prospect gets a fully configured environment with the app
           already installed and sample data already loaded.
        → Trial has an expiration date (14-30 days).
```

### 5.3 SignupRequest API

**What it does:** The **programmatic interface** for creating orgs. Instead of clicking buttons in a UI, an ISV's code calls this API to provision trial orgs automatically.

**Use case:** An ISV's website has a "Start Free Trial" button. When clicked, their backend calls `SignupRequest API → Salesforce provisions a trial org → returns the login URL to the prospect`. All automated, no human involved.

There's also **Proxy Signup** — a "headless" variant that creates an org without sending any emails, and returns an OAuth token. This lets ISVs use Salesforce behind the scenes (the end user might not even know Salesforce is involved).

### 5.4 Environment Hub

**What it does:** A **central console** for managing all your orgs. ISV partners might have dozens — a production org, several developer orgs, test orgs, scratch orgs. The Environment Hub is where they create, connect, and manage all of them.

### 5.5 License Management App (LMA)

**What it does:** Tracks the lifecycle of every license — trial vs paid, which customer has which version, when trials expire, which prospects to follow up with. Auto-generates a **lead** (a "potential customer" record) every time someone requests a trial.

**Why it matters:** This is how ISVs know their conversion funnel — how many trials started, how many converted to paid, how many churned. The data flows through this team's system.

### 5.6 Scratch Org Infrastructure

**What it does:** Powers the creation of **scratch orgs** — temporary, disposable development environments that developers create from the command line (`sf org create scratch`), use for development/testing, then throw away.

**Why it's different from a trial org:** A scratch org is developer-tooling (created from CLI, configured via a JSON definition file, short-lived). A trial org is customer-facing (created via a website, pre-configured via a template, lasts 14-30 days). Different audience, different lifecycle, potentially different provisioning code paths.

---

## 6. — The trial lifecycle

This is a core flow the team owns — understand it end to end:

```
🎨 Visual — trial lifecycle (what this team's code manages)

  Prospect clicks "Start Free Trial"
        │
        ▼
  SignupRequest API (or Trialforce template)
        │  → which DB instance? → insert tenant metadata → configure
        ▼
  Trial org provisioned  (Day 0)
  ┌──────────────────────────────────────┐
  │  Prospect explores the app            │
  │  ISV's LMA tracks: trial started      │
  │  LMA auto-generates: lead record      │
  └──────────────────────────────────────┘
        │
        ▼
  Day 14-30: Expiration approaching
        │
   ┌────┴────┐
   ▼         ▼
  CONVERT    EXPIRE
  (prospect  (trial org
   pays →     deleted or
   full org)  archived)

  KEY INVARIANT:
     Every trial is a provisioned org with an expiration.
     The lifecycle (create → use → convert/expire) is this team's domain.
```

**The engineering questions here:**
- How do you handle thousands of trial expirations per day without impacting production orgs on the same database?
- How do you convert a trial to paid without data loss (the prospect's trial data becomes their real data)?
- How do you track trial engagement to predict conversion?

---

## 7. — Scratch orgs

**Scratch orgs** are the developer-tooling side of provisioning. A Salesforce developer:

1. Writes a `project-scratch-def.json` — a config file that says "I want an org with these features enabled"
2. Runs `sf org create scratch` from the CLI
3. Gets a temporary org (lives 1-30 days, default 7)
4. Develops and tests on it
5. Org auto-deletes when it expires

**Why it matters to the team:** Scratch org creation is a high-volume, automated, API-driven provisioning flow — very different from a human clicking "Sign Up." The infrastructure has to handle bursts (a CI/CD pipeline might create 50 scratch orgs in parallel for automated testing) and clean up expired orgs without manual intervention.

**Your bridge:** This is like your one-JAR-18-deployments pattern — same provisioning infrastructure, different configurations via a definition file, different lifecycle per use case.

---

## 8. — The tech under the hood

| Technology | Role | Your equivalent |
| --- | --- | --- |
| **Oracle** | Primary multi-tenant database. All orgs share tables, isolated by OrgID. | Cassandra (multi-tenant by BU/market key) |
| **Kafka** (via internal "Ajna" platform) | Async event processing for provisioning flows, lifecycle events. Trillions of messages/day across Salesforce. | Your 40+ Kafka listeners on the ingestion tier |
| **Java** | Primary backend language | Same |
| **Kubernetes** | Container orchestration (Hyperforce — Salesforce's public cloud infra) | WCNP (Walmart's K8s platform) |
| **Terraform** | Infrastructure-as-code for Hyperforce deployments | KITT YAML for your deployments |
| **Splunk** | Log analysis and observability | OpenObserve / Grafana / Dynatrace |
| **GACK** | Salesforce's internal error tracking system (like a typed error taxonomy) | Your ERR0077/0052/0078/1001 typed error codes |

**The architecture pattern you should recognize:** their provisioning system is likely an **async pipeline** — a signup request arrives, gets queued (Kafka/MQ), processed (route to DB instance, insert metadata, configure), and the result is delivered (org ready, email sent). This is your **ingestion tier** in a different domain: event arrives → process → write to store → make available.

---

## 9. — Scale numbers

Know these — they show you did homework:

| Metric | Number |
| --- | --- |
| Orgs per database instance | ~8,000–10,000 |
| Domains handled by Edge networking | 20 million (on 30GB RAM) |
| Kafka messages/day (Ajna platform) | Trillions |
| Edge traffic | 1.5 trillion requests/month |
| AppExchange listings | 6,700+ |
| AppExchange installs | 9 million+ |
| AppExchange ecosystem value | $1 billion+ |

---

## 10. — Why YOUR experience maps

| Their challenge | Your experience | What to say |
| --- | --- | --- |
| **Org provisioning at scale** (insert tenant into shared DB, configure, make available) | **Offer ingestion at scale** (Kafka → Cassandra upsert, cache hydrate, serve on read path) | *"Both are high-volume ingest → store → serve pipelines with multi-tenant isolation."* |
| **Multi-tenancy** (OrgID isolates tenants in shared Oracle) | **Multi-market isolation** (BU flag isolates markets in shared pods, per-market CCM config) | *"I've built multi-tenant isolation where one tenant's issue can't corrupt another's — same pattern as org isolation."* |
| **Trial lifecycle** (create → use → convert/expire) | **Offer lifecycle** (ingest → cache → serve → age out / replay) | *"Managing entity lifecycles with automated expiration and data consistency."* |
| **MQ infrastructure for async provisioning** | **40+ Kafka listeners, back-pressure, idempotency, blast-radius isolation** | *"This is my strongest overlap — I've built exactly this at similar scale."* |
| **Don't break existing orgs when installing an app** | **Don't break 4 consumers when adding multi-slot** | *"Backward-compatible contract changes in a system where breakage is a revenue incident."* |
| **Typed error tracking (GACK)** | **Typed error taxonomy (4 failure classes, each with triage path)** | *"I've built typed failure taxonomies so on-call can triage by code, not grep."* |
| **Oracle/Postgres (relational DB)** | Cassandra + SQL (honestly bridge) | *"I've done query-first data modeling and relational tuning; Oracle/Postgres specifics I'd ramp on — the principles transfer."* |

---

## 11. — Questions to ask the HM

These show you researched the team, not just the company. Pick 2-3:

1. **"Every Salesforce org starts with your team's code — what's the biggest scaling challenge right now? Is it raw provisioning throughput, or more about the routing/capacity-planning logic that decides which instance an org lands on?"**
   *Why this is good:* Shows you understand provisioning isn't just "insert a row" — there's a routing decision.

2. **"Trialforce templates and scratch org snapshots both create copies of configured environments. Are those converging into shared infrastructure, or are they fundamentally different code paths with different requirements?"**
   *Why this is good:* Shows you think about architecture consolidation vs. intentional separation (your one-JAR-18-deployments thinking).

3. **"The JD mentions MQ processing systems for signup flows — is the team on the Ajna Kafka platform, or a separate queue stack? And is the provisioning flow synchronous or fully async?"**
   *Why this is good:* Shows you know Salesforce has an internal Kafka platform AND that you're thinking about the sync/async design — directly relevant to your experience.

4. **"What does the on-call experience look like for this team? With millions of orgs depending on provisioning, what's the failure mode that keeps the team up — is it more capacity/routing issues or data-consistency issues?"**
   *Why this is good:* Shows you think operationally and care about production reliability — maps to your resilience/failure-mode expertise.

5. **"How is the team thinking about AI in the provisioning and ISV tooling space? The JD emphasizes AI-native engineering — is that more about using AI to improve developer productivity, or building AI features into the provisioning/trial products?"**
   *Why this is good:* Addresses the JD's AI emphasis and maps to your AI project work.

6. **"AppExchange has 6,700+ listings now — as the ecosystem grows, how does the team handle the backward-compatibility challenge of package installation? Is there a contract-validation layer, or is it more about runtime isolation?"**
   *Why this is good:* Shows you think about backward compatibility at scale — your multi-slot contract story.

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Aug 29, 2026 | **File created.** Teach-from-scratch guide covering Salesforce/org/ISV/AppExchange concepts, what the Signup & ISV Tools team builds (6 products explained), trial lifecycle, scratch orgs, tech stack with MCSE bridges, scale numbers, and 6 informed HM questions. Sourced from Salesforce engineering blog, ISVforce documentation, Trailhead, and the JD. |
