# Digest — round 2: gap closure

Workflow `deep-recon-gap-closure`, run `wf_38cf004a-895`. 8 gap researchers + 14 adversarial
refuters, 22 agents, 0 errors. Every finder ran behind the research firewall. **8 of 14 findings
were refuted at least partially** — the refutations are recorded here alongside what survived.

---

## G1 — «Просте ЗНО» listing · status: partial

| Field | Prior claim | Round 2 | Source |
|---|---|---|---|
| Installs | 100 000+ | **100 000+ — confirmed** | APKCombo mirror |
| Rating | 4.8 | **4.9** (drift, not error) | APKCombo |
| Reviews | ~6 270 | **5 835** — prior overstated ~7% | APKCombo |
| Last updated | 2026-08-01 | **2026-08-04**, v5.7.1 | APKCombo + APKPure |
| First published | 2021 | **NOT OBTAINED** | — |
| Category rank | — | #9 Ukraine Education, #40 Education overall | Similarweb |

**The load-bearing failure is the first-publish date.** No source states it. APKPure's version
history bottoms out at 5.6.7 (2026-05-20); Similarweb's release-date field is paywalled; the Play
page needs a JS renderer. **The "took ~5 years to reach 100k" anchor therefore has no evidential
basis at all** — it was inferred from an unverified 2021 start.

Caveat the researcher raised itself: every number except the category rank traces to one mirror
(APKCombo), and two scrapers over one upstream origin is corroboration of the scrape, not two
measurements.

## G2 — Ukrainian Telegram advertising prices · status: partial · **one real data point recovered**

The round-1 proxy (Russian-segment CPM) is replaced by a directly Ukrainian figure — and the
adversarial pass is what produced it.

- **Netpeak (Альбіна Федотова), "10 самых дорогих рекламных мест в Telegram", published
  2025-01-07, pricing collected 2024-09/10.** Ranked table entry: channel «Реальний Київ»,
  Ukraine, ER 31.17%, **CPM 84 ₴**, post price **$824 / 34 000 ₴**.
  The round-1 researcher had dismissed this as "search-summary only"; the refuter fetched the
  page on first attempt and found the figure in the article body with a named author and date.
- **Netpeak (official Telegram Ads reseller in Ukraine):** Telegram Ads from **€0.2 per 1000
  views** — the official CPM platform, a different product from a paid channel post.

**REFUTED and struck: the OTW Agency niche CPM bands** ($2–4 low-competition, $4–8 mid,
higher for finance/crypto). The refuter established that `otwagency.co` is a Vite SPA serving a
**byte-identical 4 881-byte shell for every path** — including two nonsense paths the refuter
invented — MD5 `32a102e4d715baf2965a9c4488fe2b0f`. A first WebFetch had "confirmed" the claim
with fluent text. **This was a hallucinated source, caught only because the adversarial pass
existed.** It must not enter the report.

**Not found:** any published CPM or post price for a Ukrainian *education/parenting/school*
channel. Ukrainian sources segment by finance/crypto/lifestyle/news and stop. Nor any
post→install conversion figure for app promotion via a channel post — the 2–8% and 0.3–1.5%
figures circulating are for Telegram Ads click-to-bot and Mini Apps, a different funnel, from
affiliate marketing content with no disclosed sample.

## G3 — Rewarded eCPM for Ukraine / Eastern Europe / CIS · status: **open**

The round-1 negative is **confirmed and strengthened**, with verified negatives rather than
failed searches:

- **Playwire's Publisher Earnings Index** — a primary source built on Playwire's own network
  data — **publishes no geographic segmentation whatsoever**. Not "Ukraine is missing"; there is
  no geo dimension in the product.
- **Appodeal's eCPM Report (Q4 2024 data, 100 000+ apps)** is email-gated; the public landing
  page exposes no country or region values and does not list which countries are covered.
  Verified directly, not refuted.
- An article literally titled *"Average eCPM by Country: Tier 1 vs Tier 3 Benchmarks (2025)"*
  contains **no eCPM value for any Eastern European or CIS country**; its only number is a
  generic "$2–5 depending on format".

**Zero.** No rewarded eCPM for Ukraine on Android 2024–2026 from any publisher, and no fill rate
for Ukraine, Eastern Europe or CIS for any format, any year.

**Lead (partially refuted description, but the tool is real):** Appodeal Benchmarks at
`appodeal.com/benchmarks` — free public tool, expanded from ~20 to 120+ countries, covers
rewarded video. Values were not extracted within budget. This is the single most likely route to
an actual Ukrainian eCPM number.

## G4 — Published CPI for Ukraine · status: **open**

Third consecutive empty search. The researcher's conclusion — that this is a **structural
absence, not bad luck** — held up under refutation.

- **Mapendo's country CPI report: NOT REFUTED.** The refuter pulled raw HTML by curl, ran
  deterministic greps and visually inspected every in-article image. The report contains no
  country-level CPI values and does not mention Ukraine. It tiers the world and names **Poland as
  its only CEE market**; its one vertical-level chart is US-only, sourced to Singular.
- **Linkrunner's CPI tool: core NOT REFUTED.** Ukraine is not an individual country in it; its
  geo list collapses to "Europe (Western and Southern/Eastern)". No disclosed provider, no
  collection period — not a citable benchmark even for its European grouping.

**Leads not retrieved:** eMarketer holds a dataset literally titled *"Mobile Game App Cost per
Install (CPI), by Platform, Ukraine"* — the only Ukraine-labelled CPI series found anywhere;
the URL 301s to emarketer.com and then 404s. **AppBrain publishes average per-country Android
CPI from its own SDK** ("tens of thousands of installs"), which would be a primary source.

## G5 — Education retention from a methodology-disclosing vendor · status: partial · **the framing is contested**

- **Amplitude, verbatim: "The month 1 retention rate for education companies is 1% lower than the
  month 1 retention rate across all companies."** Sample disclosed: 2 600 companies, 10 600
  digital products, 17 industries, 102 countries. Report data period September 2023 – September 2024.
- **This does not say what the round-1 material said.** A 1-point gap below the all-company
  average is a mild penalty, not the catastrophic underperformance implied by "education D30 ~2%
  against a 5–7% median".
- **But the two are not comparable**, and the researcher said so: Amplitude measures product-
  analytics return behaviour across web and app; the "2% D30" figure comes from MMP install
  cohorts. Different denominators, different populations.
- **GameAnalytics Q1'24** (10 000+ projects, 2.7bn MAU) gives, for *games*: median D1 **22.91%**,
  D7 **4.2%**, D28 **0.85%** — i.e. *worse* than the education figure the report treats as the
  pessimistic case.
- **AppsFlyer's retention page could not be read** — figures render client-side. **Adjust returned
  HTTP 429** on four attempts. Neither MMP was actually reached.

**Refutations that landed:** the claim that Amplitude's report was unreachable (the PDF exists),
and the characterisation of its sample disclosure. Also: GameAnalytics has **newer public
reports** — "2026 Mobile & PC Gaming Benchmarks" with a free public page — so the Q1'24 figures
used above are already superseded.

**Honest conclusion: public retention benchmarks cannot settle this.** Three methodology-
disclosing sources give 0.85%, 2%, and "1 point below average", measuring three different things.
Retention must be measured in-product, not looked up.

## G6 — NMT calendar and seasonality · status: partial · **the cohort figure is now newer and larger**

**NOT REFUTED — verified against the primary source.** UTsOYaO, "НМТ-2026: деякі підсумки
основних сесій", published 2026-07-03:

- Main sessions ran **20 May – 25 June 2026**, across 17 testing days.
- **354 463 registered; 324 284 participated (91.49%); 324 003 received scores.**
- Results published in personal accounts 2026-07-03; additional session 17–24 July 2026.

This supersedes the import's NMT-2025 figures (317 091 registered / 283 653 sat) — **the cohort
grew roughly 12% year over year.**

**NMT-2027 calendar is not published.** Osvita.ua (2026-08-12): exact registration dates are not
announced and are expected to be set in winter, once UTsOYaO and MON approve the calendar.
For reference, NMT-2024 registration ran 14 March – 11 April.

**Consequence for launch timing:** preparation season for the next cycle begins in autumn and
registration falls in spring. An autumn 2026 launch lands **at the start of the preparation
season**, not outside it.

## G7 — Consumable-IAP conversion and ARPU · status: partial · **denominator mismatch confirmed**

**The category error round 1 identified is real and worse than stated.** No source publishes
"share of installs that ever make a consumable purchase". Every retrieved conversion figure uses
a **different denominator** — active players (DAU/MAU) paying within a period, not installs-ever.
So RevenueCat's 2.1% and the game-industry 0.2–1% figures are not merely different categories;
they are not commensurable at all.

- **GameAnalytics, hypercasual (timing genre): IAP conversion 0.94%, ARPDAU $0.15, ARPPU $42.**
  Verified against GameAnalytics' own page, not just the aggregator. **Caveat that survived
  refutation: the sample is the top 5% of games** — best-in-class, not median.
- **Appodeal Mobile Casual Benchmarks 2025** (data 2024-06 to 2025-01, **US Android installs
  only**): blended ARPU by genre — hypercasual $0.86, running $2.34, slicing $2.19, match $2.99,
  party $4.90, luck battle $12.23, merge-3 $14.83.
- **Liftoff's 2024 Casual Gaming Apps Report contains no payer-conversion, ARPU or ARPDAU
  benchmark at all** — full PDF text extracted; it is a UA/creative report plus qualitative
  IAP-design trends.

## G8 — Ukrainian localisation · status: partial · **the finding of the whole round**

### It is a legal requirement, not only a reputational one

**NOT REFUTED. Verified character-for-character against the primary consolidated text**
(`zakon.rada.gov.ua/laws/show/2704-19/print`, accessed 2026-08-31), and re-verified independently
by the lead.

Law 2704-VIII of 2019-04-25, Article 27 — *"Державна мова у сфері користувацьких інтерфейсів
комп'ютерних програм та веб-сайтів"*:

- **Part 1** — a computer program with a user interface sold in Ukraine must have a UI in the
  state language and/or English or another official EU language. *(English satisfies this.)*
- **Part 7** — mobile applications of state bodies, local government, registered media, **and
  business entities that supply goods and services in Ukraine, must have a user-interface version
  in the state language.** *(English does not satisfy this. Ukrainian specifically.)*

Administrative liability for violations of the law took effect **16 July 2022**; the State
Language Protection Commissioner states a first-violation fine of **UAH 3 400 – 8 500**. The
refuter attacked this figure against a law firm's differing upper bound, and the regulator's own
page prevailed.

**Applicability is a legal question, not a research finding.** Whether a solo developer selling
in-app purchases through Google Play is a *суб'єкт господарювання, що реалізує товари і послуги в
Україні* is for a lawyer to determine, not for this report to assert.

### The localisation-lift numbers everyone quotes are folklore

Every percentage circulating for "localisation lifts installs by N%" traces back to a small set
of upstream claims — principally a **Distimo study from around 2012**, since absorbed into
App Annie/data.ai — republished across vendor and agency blogs (Smartling, AppFollow, Appinventiv,
Storemaven and others). No usable modern figure. **Treat as folklore, not data.**

### Review-bombing over language: NOT FOUND

Two queries, Ukrainian and English, returned zero reporting on Ukrainian users downrating apps for
offering Russian but not Ukrainian. The round-1 assumption that this is a live risk is
**unsupported by evidence** — though absence of reporting is not absence of the phenomenon.

### The mechanism to settle it yourself

**Google Play Console store-listing experiments** run native A/B tests splitting traffic between
a control listing and variants, reporting which wins on install conversion rate — and they
support **localised** experiments. The question does not need a benchmark; it needs an experiment.
