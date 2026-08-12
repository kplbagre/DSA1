# Salesforce SMTS Virtual HLD+LLD Round — Deep Research Findings (Aug 2026)

> Companion to `questions-by-frequency.md`. This file captures what the frequency table can't: how the round is *structured*, what interviewers actually evaluate, and how things have shifted over the last 12-18 months. Sourced only from Blind, CodingKaro, GeeksforGeeks, and Glassdoor — the four sites actually reachable this session (see verification table in the frequency file).

---

## 1. Round Structure — What's Confirmed vs. What the Standards File Assumes

Your `solution-notes-standards.md` assumes a **single 90-minute round**: 35 min LLD + 45 min HLD + 10 min buffer, both halves on the *same* problem. The fresh evidence partially supports this but adds real nuance:

- **Combined-round evidence exists and is recent.** CodingKaro, Feb 2025 (SDE-3, 5.5 YOE): *"Round2(LLD + HLD)"* — one round, one problem (Zomato food delivery), both zoom levels. CodingKaro, Apr 2025 (LMTS): *"Covered both LLD + HLD aspects [of a Job Scheduler System]... write LLD code for job creation, persistence, dispatching, and execution flow."*
- **But separate HLD-only and LLD-only rounds are also common**, especially in virtual multi-round loops rather than a single onsite day. Blind (Aug 2025): *"System design was to design a rate limiter and then a query executor"* — two prompts, one round, but not framed as HLD→LLD on the same problem; sounds more like two independent HLD-flavored design discussions. Blind (May 2025): *"there are two system design rounds listed - is one of them low level[l]..."* — direct evidence some loops split HLD and LLD into **separate rounds entirely**, not halves of one round.
- **The GFG article (Feb 2024) shows a 45-minute System Design round labeled "HLD and LLD"** but lists three separate lightweight prompts (TinyURL, LFU cache, coffee ordering) rather than one deep problem explored at both zoom levels — suggesting that specific loop was breadth-over-depth, not the deep dual-zoom format your standards file trains for.
- **Bottom line:** the 90-min combined dual-zoom format your standards file trains for is real and confirmed in multiple recent (2025) reports, but it is **not universal** — expect team-dependent variation between (a) one deep combined HLD+LLD round on one problem, (b) two separate lighter design rounds, and (c) a breadth round with several small prompts. Your prep format is still the right default (deepest, most defensible skill), but don't be thrown if the actual round only asks for HLD, or hands you two unrelated design prompts back to back.

## 2. What Interviewers Actually Evaluate (patterns across first-hand accounts)

- **Resume-architecture deep dives are a real, distinct evaluation axis — not just small talk.** The Apr 2025 Blind HM-round account: interviewer had the candidate whiteboard their *own* current-job architecture in deep detail, then picked one feature off it and had them redesign + code it live. This is different from "design X system" — it's "defend the system you already built." Practice narrating your own resume's architecture out loud with the same rigor as a fresh design problem.
- **Justification under pressure is explicitly tested, not just the final diagram.** CodingKaro (Nov 2025, TicketMaster): *"The interviewer grilled me after every sentence I was speaking... going at the thread level"* on ACID atomicity, and separately drilled into *why* Elasticsearch speeds up queries (inverted index internals) — i.e., interviewers probe underlying mechanism, not just "which tool did you pick."
- **Optimization awareness is checked even in coding-flavored rounds.** GFG (Feb 2024): candidate solved kth-largest in O(k·n); interviewer explicitly "suggested O(n log k)" — the follow-up "what did you do to improve anything?" pattern shows up across multiple sources as a standard probe, independent of whether you got the "right" answer first.
- **Concurrency/race-condition handling is a named, explicit requirement in written prompts now**, not just something you volunteer. CodingKaro's Dec 2025 Meeting Room Reservation prompt spells it out verbatim: *"Concurrency: Handle race conditions where two users try to book the same room at the same time."* This is being written into the problem statement itself — treat concurrency as mandatory, not a bonus point.
- **Behavioral answers inside a technical round can silently sink an otherwise strong loop.** The Apr 2025 HM-round candidate reported acing the whiteboard/design portions, then gave a blunt "I don't like your tech stack" answer to "why are you leaving" — never heard back. Technical strength does not insulate you from a bad behavioral read in the same round.
- **System design performance directly drives leveling, not just pass/fail.** The Apr 2026 downlevel case (10+ YOE Adobe engineer, SMTS→MTS) was explicitly attributed to system design round feedback — confirms the HLD/LLD round(s) are being used as a leveling signal, reinforcing why this round matters disproportionately for SMTS/Staff-track candidates specifically (vs. a generic pass/fail gate).

## 3. Emerging Trend: "Clone a Famous Product" Prompts

This is the most significant miss in the previous version of the frequency file. Six separate 2024-2025 instances of "just design [well-known product]" surfaced this session: Google Docs, Google Calendar, Google Maps, WhatsApp Web, Slack's send-message feature, TicketMaster. Notably, three of these (Maps, WhatsApp Web, Slack) came from a single **frontend-focused SMTS loop** (Nov 2025) — meaning this pattern may correlate with frontend/fullstack SMTS tracks specifically, not just backend. If you're interviewing for a frontend or fullstack SMTS role, prepping generic backend LLD problems (parking lot, elevator) without also having a client-side system design framework (rendering strategy, state management, caching, accessibility) is a real gap.

## 4. Emerging Trend: DSA/LLD Line Blurring

Multiple 2025 sources describe "coding rounds" that are really small LLD exercises: Blind's Top-K query results service (framed as "the coding round," "not leetcode... basically had to implement a service class"), and several CodingKaro entries describing "workable code" + "proper design patterns" expectations inside what's labeled a coding round (e.g., E-Commerce Cart, Jun 2024). Don't assume every non-"System Design"-labeled round is pure algorithmic LeetCode — come ready to structure a small service class cleanly even in a "coding" round.

## 5. What Didn't Change

- **Team/loop-dependent variation was already true in 2022 and still is.** A 2022 Blind reply about SMTS: *"It is team specific... I had both HLD and LLD, but I've heard some get only HLD."* Nothing in the 2024-2026 data contradicts this — if anything, the HLD-only vs. combined vs. two-rounds variation confirmed above is the same team-dependence, just with more recent examples.
- **Behavioral/culture-fit questions ("why Salesforce," "ohana" references) remain a fixture** across both old (2022) and new (2025-2026) sources — still worth a genuine, non-generic answer prepared.
- **The Hiring Manager round is not purely behavioral** — both the 2024 GFG account and the 2025 Blind account show HM rounds routinely including a real technical design component (architecture explain-and-redesign, or a full LLD exercise on the whiteboard). Don't under-prep for the HM round technically.

## 6. Honest Gaps in This Research Pass

- Could not access LeetCode Discuss, 1Point3Acres, Medium, or Reddit at all (Cloudflare bot-detection or network-level blocks) — any prior claims in this KB sourced from those sites are unverified as of this pass, not necessarily false.
- Roundz/Substack (the source of the original "Notification Service, 40-min HLD then class design" story) loads only a paywall/subscribe-gate now — could not re-confirm that specific account.
- Glassdoor's older reviews (2017-2021) skew toward generic "difficult interview, be strong in basics" commentary with few named design problems — the useful signal there is concentrated in the 2020-2024 range, not evenly spread.
- This pass covered text-based forums only. No video/YouTube mock-interview content, no Salesforce's own official careers-blog content, and no course platforms (Educative, etc.) were checked this session.

---

**Recommendation:** Given the "clone a famous product" and "booking/reservation" clusters are now the strongest verified signals, consider creating dedicated solution notes for **one booking/reservation-style problem** (e.g., Meeting Room Reservation — it has the most detailed, concurrency-explicit prompt text available) and **one "clone a product" problem** (Google Calendar or Google Docs are the best-documented) alongside the Elevator LLD problem, before doing more backend-classic problems like Parking Lot that are already well covered by generic prep material.
