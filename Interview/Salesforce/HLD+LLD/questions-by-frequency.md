# Salesforce SMTS — HLD + LLD Questions Ranked by Frequency

> **Research date:** Aug 12, 2026 — this is a full re-research pass, not a touch-up. The previous version of this file cited LeetCode Discuss, Glassdoor, Roundz/Substack, 1Point3Acres, and Medium as sources, but those claims could not be re-verified this session (see "Access & Verification Status" below). This version only ranks items that come from content actually loaded and read this session, with verbatim quotes and dates. Where an older claim is now contradicted or unconfirmed, that's called out explicitly rather than silently dropped.

---

## FINAL PREP PRIORITY — Top 7 for a Confirmed 90-Min Combined Round

> **HR confirmed the round is 90 minutes** — matching exactly the 35 min LLD + 45 min HLD + 10 min buffer format in `solution-notes-standards.md` / `format.md`. This table is the actionable synthesis: it cross-references the old archived frequency file (`questions-by-frequency-ARCHIVE-pre-2026-08-research.md`, 8-source triangulation, unverified this session) against the new verified research below, prioritizing problems most likely to be explored at **both zoom levels in one sitting** — not just "frequently mentioned" in general.

| # | Problem | Notes file | Why it ranks here |
|---|---|---|---|
| **1** | **Notification Service** | **`notification-service.md`** | Strongest pick in *both* files. Old file: #1 HLD, #1 LLD, cited as a 90-min combined round. New file: independently re-confirmed via CodingKaro (Jan 2025) with a prompt spanning "high-level architecture... low-level implementation (class diagrams, APIs, edge cases)" in one sentence — the best combined-format evidence found this session. **Already built** — see `notification-service.md`. |
| **2** | **Job Scheduler (Distributed / Cron-based)** | **`job-scheduler.md`** | #2 in the old HLD list; independently the #3 confirmed-combined pick in new research with an explicit quote: *"Covered both LLD + HLD aspects"* (CodingKaro, Apr 2025). Natural LLD extension: Cron Job Parser (Jun 2025). Two independent research passes agree — rare, valuable convergence. |
| **3** | **Rate Limiter** | **`rate-limiter.md`** | #3 in both old HLD and old LLD lists; also #3 in the new HLD list (Blind, Aug 2025). Not proven as one combined problem this session (once appeared paired with a *different* design question), but structurally a textbook dual-zoom candidate — HLD: token-bucket/sliding-window + distributed coordination; LLD: the algorithm class itself. |
| **4** | **Booking / Reservation System** (Meeting Room as the concrete example) | **`booking-system.md`** | Newest, strongest signal — #1 in the new HLD list (5 instances: cab, meeting room, hotel, vaccination slot, scheduler) and #5 in the new LLD list with a fully detailed concurrency spec verbatim (CodingKaro, Dec 2025). Not on the old file's radar at all. HLD = availability/search/booking architecture, LLD = the exact overlap/race-condition handling quoted. |
| **5** | **Parking Lot** | **`parking-lot.md`** | #2 old LLD, #3 new LLD, and the most "textbook dual-zoom" problem in existence — naturally spans HLD (multi-level garage, pricing service, entry/exit sensors) and LLD (spot allocation, ticket lifecycle). Cross-validated across both passes even without a single source showing it asked at both levels in one sitting this session. |
| **6** | **LRU/LFU Cache** | *(skipped by request)* | #4 old LLD, #2 new LLD — and the one item that sits *inside* a source explicitly labeled "HLD and LLD" (GFG, Feb 2024: TinyURL + LFU cache + Coffee ordering, one 45-min block). Concurrency-friendly, well-scoped — good if the interviewer runs short on time. |
| **7** | **Sign-Up / Login System at Scale** | **`signup-login-system.md`** | Newest addition, but has the single most explicit combined-format quote in the new research: *"Design a high-level and low-level design for a sign-up and login page system that can handle a billion users"* (CodingKaro, Jul 2025) — HLD and LLD named in the same sentence by the candidate. |

**Honorable mentions, lower priority for this round shape:**
- **Elevator System** — most-repeated *named* LLD prompt in the new data, but every source treats it as LLD-only with no HLD pairing evidence. Good backup, not top-7 for a combined round.
- **"Clone a famous product"** (Google Calendar, Docs, Maps, WhatsApp Web, Slack) — real and growing (6 instances, 2024-2025), but skews toward frontend-specific loops. Worth knowing if frontend/fullstack track, lower priority otherwise.

**BUILD STATUS (Aug 2026): 6 of 7 complete.** Every notes file listed above exists and meets the same bar - noun-from-requirement derivation table, explicit composition-vs-aggregation calls with reasoning, alternatives-considered against the *strongest* alternative (never a strawman), per-section follow-up Q&A scripts, staged HLD evolution (naive -> quantified breaking points -> fixed), and a full SQL data model with justified schema decisions. LRU/LFU Cache is intentionally skipped - it is the best-covered problem in generic prep material.

---

## Access & Verification Status (read this first)

| Source | Status this session | Usable? |
|---|---|---|
| **Blind (teamblind.com)** | Loaded, searched, read multiple threads verbatim | Yes — primary source |
| **CodingKaro** | Loaded all 57 stories, extracted 29 design-relevant entries verbatim | Yes — primary source |
| **GeeksforGeeks** | Loaded the one confirmed SMTS article (Feb 2024) verbatim | Yes — primary source |
| **Glassdoor** | Loaded, 12 of 19 reviews render full text (rest gated behind login) | Yes — but sparse on named design problems |
| **LeetCode Discuss** | Blocked — Cloudflare bot-challenge, never rendered real content | No — could not verify any prior claim sourced from here |
| **1Point3Acres** | Blocked — Cloudflare bot-challenge | No |
| **Medium** | Blocked — Cloudflare "you have been blocked" | No |
| **Reddit** | Blocked — network-level block | No |
| **Roundz/Substack** | Loads, but only a subscribe-wall, no post content visible | No — cannot confirm the "Notification Service, 40min HLD then class design" story from the old file anymore |

**Implication:** the previous file's #1 ranking of "Notification Service" leaned heavily on Roundz and LeetCode — both unverifiable this session. Notification Service *is* real (confirmed once directly via CodingKaro, Jan 2025, plus thematically via Push Notification System and a notification sub-question inside a Zomato prompt) but the fresh evidence does not support calling it the single highest-probability question anymore. Rankings below reflect what's actually countable in verified sources.

---

## Confirmed Combined HLD+LLD (Same Problem, Same Round) — Ranked by Evidence Strength

> **Important distinction:** appearing in both the HLD table and LLD table below does NOT mean a problem was tested at both zoom levels in one round — it usually just means different candidates hit different instances of the same *category* (e.g. Booking systems tops the HLD list and Meeting Room Reservation tops the LLD list, but those are different loops, not one candidate doing HLD-then-LLD on one problem). The table below only includes problems where a source explicitly states both zoom levels were tested on the **same problem in the same round** — this is the actual format your `solution-notes-standards.md` trains for, so these are the highest-value prep targets.

| Rank | Problem | Evidence it's actually combined | Source / Date |
|---|---|---|---|
| **#1** | **Notification Service** | Prompt itself spans both zoom levels in one sentence: *"covering high-level architecture, components, database design, low-level implementation (class diagrams, APIs, edge cases), and relevant design patterns"* — the single strongest combined-format piece of evidence found | CodingKaro, Jan 2025 (MTS II, Bangalore) |
| **#2** | **Zomato / Food Delivery App** | Round explicitly labeled *"Round2(LLD + HLD)"* — candidate did LLD of the app, then order-tracking schema (HLD) and proximity-service HLD in the same round | CodingKaro, Feb 2025 (SDE-3) |
| **#3** | **Job Scheduler System** | *"Covered both LLD + HLD aspects. Focus was on write LLD code for job creation, persistence, dispatching, and execution flow"* | CodingKaro, Apr 2025 (LMTS) |
| **#4** | **Sign Up / Login System (billion users)** | *"Design a high-level and low-level design for a sign-up and login page system that can handle a billion users"* — HLD+LLD stated explicitly in the same prompt | CodingKaro, Jul 2025 (MTS I) |
| **#5** | **TinyURL + LFU Cache + Coffee Ordering** | Round explicitly labeled *"HLD and LLD"* but this is breadth-style — three separate light prompts in 45 min, not one problem explored deeply at both levels. Weaker/different shape of "combined" | GeeksforGeeks, Feb 2024 |

**Does NOT qualify as confirmed-combined (even though it looks combined at a glance):**
- **Rate Limiter + Query Executor** (Blind, Aug 2025) — two separate design prompts back-to-back in one round, not explicitly HLD-then-LLD on one problem
- **Booking/Reservation systems** and **Meeting Room Reservation** — top both ranked lists below, but that's category overlap across different candidates/loops, not one candidate doing both zoom levels on one problem
- Some loops run HLD and LLD as **separate rounds entirely** — Blind, May 2025: *"there are two system design rounds listed — is one of them low level[l]..."*

**Prep priority implication:** you already have Notification Service done. **Job Scheduler is the next best pick** — most explicit "both LLD+HLD" evidence after Notification Service, plus a natural companion LLD prompt (Cron Job Parser, Jun 2025) if you want to extend it.

---

## HLD Questions — Ranked by Frequency (verified, 2024–2026 window)

| Rank | Problem | Signal | Sources (verbatim-quoted, dated) |
|---|---|---|---|
| **#1** | **Booking / Reservation systems** (cab, meeting room, meeting scheduler, hotel, vaccination slot) | 5 mentions, most consistent cluster across 2023–2025 | CodingKaro: Cab Booking (Dec 2025), Meeting Room Reservation (Dec 2025), Meeting Scheduler (Oct 2025), Vaccination Slot Booking (2023), Hotel Booking (2023) |
| **#2** | **"Clone a well-known product"** (Google Docs, Google Calendar, Google Maps, WhatsApp Web, Slack, TicketMaster) | 6 mentions, all 2024–2025, growing pattern | CodingKaro: Google Docs (Jun 2024), Google Calendar (Apr 2024), Google Maps/WhatsApp Web/Slack (Nov 2025, frontend loop), TicketMaster (Nov 2025) |
| **#3** | **Rate Limiter** | 2 direct + implied common | CodingKaro: "Design API Rate Limiter" (Dec 2025); Blind: "System design was to design a rate limiter" (Aug 2025 confirmed after-the-fact report) |
| **#4** | **Notification / Push Notification systems** | 3 mentions (thematic cluster, not identical wording each time) | CodingKaro: "Design a Notification Service" HLD+LLD (Jan 2025); "Design a Push Notification System" APNS/FCM scale (Aug 2025); notification sub-question inside Zomato HLD (Feb 2025) |
| **#5** | **Job / Task Scheduler (cron-based)** | 3 mentions, same general hiring window (Jun 2025) | CodingKaro: Cron-Scheduling (Jun 2025), Job Scheduler System covering LLD+HLD (Jun 2025), Cron Job Parser (Jun 2025) |
| **#6** | **Recommendation System** | 1 mention | CodingKaro: "Recommendation System HLD" (Jun 2024) |
| **#7** | **Property/Marketplace Management (cloud migration)** | 1 mention | CodingKaro: Property Management System HLD (Aug 2025) |
| **#8** | **Real-time / streaming systems** (live bidding, live scores) | 1–2 mentions | CodingKaro: Real-time Bidding/Auction System (Jun 2025); (older, 2023: live match scores/Hotstar-style) |
| **#9** | **Login / Sign-up at scale** | 2 mentions | CodingKaro: "Sign Up and Login for Billion Users" (Jul 2025); "design a login page" in HM round (Dec 2025) |
| **#10** | **TinyURL (URL Shortener)** | 1 mention, but classic/well-known | GFG SMTS article (Feb 2024): "Design Tiny Url" |
| **#11** | **Campaign / Marketing systems** | 2 mentions | CodingKaro: Campaign Emails System (Aug 2025); Marketing Campaign System w/ Saga pattern discussion (Nov 2025) |

---

## LLD Questions — Ranked by Frequency (verified, 2024–2026 window)

| Rank | Problem | Signal | Sources (verbatim-quoted, dated) |
|---|---|---|---|
| **#1** | **Elevator System** | 3 mentions — the single most-repeated *named* LLD prompt found this session | CodingKaro: "Low-Level Design of an Elevator System" (Dec 2025); "Elevator Service LLD" w/ escalator direction (Nov 2025); Blind (2023, older): "Elevator" named as classic prompt |
| **#2** | **Cache Design (LFU/LRU)** | 1 direct + well-established classic | GFG SMTS article (Feb 2024): "Design caching with the Least frequently used" |
| **#3** | **Parking Lot** | 1 direct this session (Blind search itself returned zero hits — see note) | CodingKaro: "Design a parking lot on a whiteboard and provide a valid code implementation" (Oct 2025) |
| **#4** | **Connection Pool** | 1 mention, detailed spec | CodingKaro: "Design a Connection Pool" — 1000-2000 connections, FREE/BLOCKED/CLOSED states, timeout/reject logic (Nov 2025, LMTS loop) |
| **#5** | **Meeting Room / Booking LLD** (concurrency-heavy) | 1 mention, very detailed | CodingKaro: "Meeting Room Reservation Platform" — full entity list, overlap/concurrency handling spec given verbatim (Dec 2025) |
| **#6** | **Cron Job Parser** | 1 mention | CodingKaro: parse `"0/5 8,12 1 * 1-5"` into Minute/Hour/Day/Month/DayOfWeek (Jun 2025) |
| **#7** | **Infinite-board Tic-Tac-Toe (winning length N)** | 1 mention, full first-hand account | Blind: HM round, full LLD + winner-detection code on whiteboard (Apr 2025) |
| **#8** | **E-Commerce Shopping Cart** | 1 mention | CodingKaro: "implement a cart in any e-commerce website... proper design patterns" (Jun 2024) |
| **#9** | **Zomato / Food Delivery App** | 1 mention, combined w/ HLD | CodingKaro: LLD + proximity-service HLD discussion (Feb 2025) |
| **#10** | **Chess** | 1 mention (older) | Blind (2023): "Chess, elevator for LLD" as historical reply |
| **#11** | **Top-K Query Results Service** | 1 mention | Blind: "implement a service class for finding top K results of some queries" — reported as the "coding round," blurring the DSA/LLD line (Aug 2025) |

---

## Key Patterns From the Verified Data

**Booking/reservation systems are the most durable cluster, not a single named problem.** Across 2023–2025, Salesforce interviewers keep returning to some flavor of "book a resource, handle overlap/concurrency" — cabs, meeting rooms, hotel rooms, vaccination slots. If you only deep-prep one *category*, this is it — because the concurrency/overlap-handling core is reusable across all of them.

**"Clone a famous product" is a real, growing pattern that earlier prep lists (including the old version of this file) completely missed.** Google Docs, Google Calendar, Google Maps, WhatsApp Web, Slack, TicketMaster — six separate confirmed instances in 2024–2025, several from a single frontend-focused SMTS loop (Nov 2025). If you're prepping generic "textbook" LLD problems only (parking lot, elevator, cache), you may be blindsided by "just design Google Calendar" with no scaffolding.

**Notification Service is real but was likely over-ranked in the previous version of this file.** It shows up thematically (notification service itself, push notifications, a notification sub-question inside another HLD) but at roughly the same frequency as Rate Limiter, Job Scheduler, and Booking systems — not clearly ahead of them. Keep your existing `notification-service.md` prep — it's still a legitimate, real, recently-asked (Jan 2025) question — just don't treat it as uniquely more likely than the others.

**Elevator is the most-repeated single named LLD prompt in the verified 2024–2026 data** — worth having a dedicated notes file for it, more so than the previous file suggested.

**Job/Task Scheduler questions cluster tightly around one hiring window (Jun 2025)** — three separate named variants (cron-scheduling, job-scheduler LLD+HLD, cron-parser) came from candidates interviewing in the same ~month, which may indicate a specific team/loop rotates this topic rather than it being company-wide. Worth prepping, but don't assume it's evenly distributed across all Salesforce teams.

**The DSA/LLD line is blurring.** Multiple 2025 reports (Blind's "Top-K query results service," CodingKaro's several "write workable code" LLD rounds) describe what used to be pure coding rounds now asking for a small service class with clean design — i.e., LLD-lite inside a "coding" round label. Don't assume the coding round is LeetCode-only.

**System design performance is a real downlevel risk, not just a rejection risk.** A first-hand Apr 2026 Blind post describes a 10+ YOE Adobe staff engineer downleveled from SMTS to MTS specifically because of "system design round in on-site" feedback — reinforcing that the HLD/LLD round(s) carry outsized weight in leveling decisions, not just pass/fail.

---

## Sources (only sources actually loaded and read this session)

- [Blind — teamblind.com](https://www.teamblind.com) — searched "Salesforce SMTS," "Salesforce Staff Software Engineer interview," "Salesforce HLD LLD interview," "Salesforce virtual onsite system design," "Salesforce notification service design," "Salesforce parking lot interview" (Aug 12, 2026)
- [CodingKaro — 57 Salesforce interview stories](https://www.codingkaro.in/jobs-internships/leetcode-interview-experience/Salesforce) (Aug 12, 2026)
- [GeeksforGeeks SMTS article](https://www.geeksforgeeks.org/interview-experiences/salesforce-interview-experience-for-smts/) — last updated 19 Feb 2024 (Aug 12, 2026)
- [Glassdoor SMTS Interview Questions](https://www.glassdoor.com/Interview/Salesforce-SMTS-Software-Engineer-Interview-Questions-EI_IE11159.0,10_KO11,33.htm) — 12 of 19 reviews readable without login (Aug 12, 2026)

**Not usable this session (blocked/gated — do not cite as current evidence until re-verified):** LeetCode Discuss, 1Point3Acres, Medium, Reddit, Roundz/Substack.
