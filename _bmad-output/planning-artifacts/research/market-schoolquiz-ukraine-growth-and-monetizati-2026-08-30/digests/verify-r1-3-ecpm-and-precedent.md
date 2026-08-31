# Verifier digest — round 1, #3: rewarded eCPM and the "no precedent" anchor

Scope: load-bearing claims #47 (rewarded eCPM) and #23/#24 (Просте ЗНО listing, no-precedent conclusion).
Firewall: verifier had brief only, no project context.

## Claim #47 — rewarded video eCPM

**Verdict: disputed on the tier-1 range, unverified on everything the model actually uses.**
Confidence downgraded medium → **low**.

- Independent publisher: Playwire, "AdMob eCPM Benchmarks: What Publishers Should Expect",
  published 2025-09-17, accessed 2026-08-31,
  https://www.playwire.com/blog/admob-ecpm-benchmarks-what-publishers-should-expect —
  rewarded video **$15–30** for tier-1 (the import claimed $15–40; Playwire's $40+ applies to
  gaming apps specifically), and a **global average of $8–18**.
- **Tier-2/3 and Eastern Europe: no numeric breakout found anywhere.** AdReact's "App Ad Revenue
  Benchmarks 2026" (published 2026-03-22, accessed 2026-08-31) groups Japan/Korea/UAE/Saudi as
  "tier 2" and SE Asia/India/LatAm/Africa as "tier 3", **mentions Eastern Europe nowhere**, and
  attaches no numbers to either tier.
- **The Ukraine $2–5 figure is not published anywhere the verifier could find** — neither
  supported nor contradicted. It is an estimate the import presented in the same register as its
  sourced numbers.
- The $3.60 (H1 2023) → $3.02 (H1 2025) casual Android decline was **not found** in any
  independent source.
- **Fill rate for the region: not found.** Playwire's 85–95% is explicitly for tier-1 traffic and
  not broken out by format.

**Consequence for the decision.** This is the weakest evidential leg in the whole report, and it
sits under the ad-revenue model. Note the direction of the error, though: the only published
figure near this product's likely mix is a **global average of $8–18**, which is *higher* than the
$3 the import assumed. At $8 eCPM the DAU needed for the ad-only target falls from ~8 300 to
roughly 3 100 — still several times beyond any realistic six-month DAU, so **the conclusion holds
even when the disputed input moves in the favourable direction.**

## Claim #23 — Просте ЗНО listing facts

**Verdict: unverified — could not access.** The Play page is JS-rendered and returned only the
app title on fetch (both `hl=uk` and `hl=en_US`); AppBrain returned HTTP 403. The install count,
rating, review count and dates could not be confirmed or denied. An open verification gap, not a
negative finding.

## Claim #24 — "No precedent for 100k in six months in this niche"

**Verdict: disputed.** A counter-example exists, though it does not match the niche.

- Kvasola agency, "How We Scaled Get Get from 30K to 100K Users in 2 Months", accessed
  2026-08-31, https://kvasola.agency/blog/get-get-case-study-30k-to-100k.html — a Ukrainian app
  reaching 100 000 users in ~60 days, driven largely by organic/UGC/referral mechanics.
  **Caveats that matter:** self-published agency marketing, not independent reporting; the app
  reads as social/lifestyle rather than education; and it started from a 30 000 base, so it is
  70k in 60 days on top of existing traction, not 100k from zero.
- "Mriya", a Ukrainian school-ecosystem app, reportedly reached 100 000 users around September
  2024 — but via institutional B2G rollout across partner schools, not consumer Play growth.
  A weak analog.
- **No same-niche precedent found either way**: no published case of a Ukrainian or Eastern
  European quiz/exam-prep app reaching 100k faster than several years, and no proof none exists.

**Consequence.** The strict framing "rapid growth has no precedent in the Ukrainian market" is
**overstated** and should be softened in the synthesis. The narrower and more defensible claim —
no evidence of a same-niche app doing it, and the one Ukrainian fast-growth case on record came
from organic/referral mechanics rather than a paid budget — actually *supports* the report's own
recommendation to invest in virality rather than in spend.

## Leads

- Appodeal Performance Index (newer edition) is likely the true upstream of the tier-1/2/3 eCPM
  numbers; Liftoff's "In-app advertising in 2026" may carry CEE breakouts.
- Similarweb's Ukraine education top-free ranking would place Просте ЗНО against competitors and
  may expose install trajectories.
- A metadata site that renders (42matters, Sensor Tower app profile) would close the listing gap
  where Play and AppBrain fetches failed.
