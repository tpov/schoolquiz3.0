# Verifier digest — round 1, #1: CPI and Google Play promotion policy

Scope: load-bearing claims #32 (education CPI) and #37 (incentivised-install policy).
Firewall: verifier had brief only, no project context.

## Claim #32 — "Education games CPI $1.09 Android / $3.04 iOS" (AppsFlyer)

**Verdict: unverified.** Confidence downgraded medium → **low**.

- AppsFlyer's own glossary page does carry the figure verbatim — publisher AppsFlyer,
  https://www.appsflyer.com/glossary/cost-per-install/, accessed 2026-08-31. But the page shows
  **no date, no region, no sample methodology**. That is a gap in AppsFlyer's own sourcing.
- Independent check: Business of Apps "Education App Benchmarks (2026)" gives a **blended
  education CPI of $4.70** — roughly 4× higher. Page returned HTTP 403 on fetch, so its period,
  region and upstream sourcing are unconfirmed; blended-vs-Android-only skew cannot explain a 4×
  gap on its own.
- Liftoff general Android CPI figures surfaced inconsistently across aggregators ($1.92 and
  $0.63 for the same period) — neither trustworthy nor education-specific.

**Ukraine CPI $0.20–0.60 (claim #34): unsupported and uncontradicted.** No published Ukraine CPI
figure was located for any category. It remains an unsourced estimate on both sides.

**Consequence for the decision:** the direction of the finding is *robust to this uncertainty*.
Every independent figure found is **higher** than the $1.09 the import used, and a higher CPI only
widens the gap to 100k installs. The arithmetic conclusion survives; the specific install counts
attached to it do not deserve two significant figures.

## Claim #37 — Google Play incentivised-install policy

**Verdict: partially verified.** Core prohibition **verified from primary source**; the
enforcement phrasing and the ads-vs-incentives distinction are **paraphrase, not quotation**.

- Primary, fetched directly: Google Play Console Help, "User Ratings, Reviews, and Installs",
  https://support.google.com/googleplay/android-developer/answer/9898684, accessed 2026-08-31.
  Operative text: developers **"must not attempt to manipulate the placement of any apps on
  Google Play"**, and the policy prohibits inflating ratings, reviews or install counts by
  illegitimate means "such as fraudulent or incentivized reviews and ratings".
- Google Play Console Help, "App Promotion",
  https://support.google.com/googleplay/android-developer/answer/9899004, accessed 2026-08-31:
  disallows promotion practices "that are deceptive or harmful to users or the developer
  ecosystem" and requires "transparent promotion methods that require informed user action".
- **Not found in Google's own text:** the claim that violators are "filtered from top charts or
  removed", and the claim that "paying for ad exposure is permitted". Both are reasonable
  inferences from the two pages, but neither is a quotable Google sentence. Flagged as such.

**Applied to the owner's three planned tactics** (verifier's inference, explicitly not a Google quote):

| Tactic | Reading |
|---|---|
| Paying a Telegram channel owner to run an ad post | Ordinary paid advertising — governed by the transparency/non-deception standard, not the incentivised-install clause, **provided the ad copy itself does not offer users a reward for installing** |
| Paying end users or bots per install | Squarely inside the prohibited "incentivized … installs by illegitimate means" clause |
| Operating one's own network of promotional channels | Not inherently prohibited; becomes a violation if used to fake organic signals, drive bot installs, or incentivise the audience |

## What the verifier could not find

- Any dated, sourced version of AppsFlyer's $1.09 figure (the glossary page is undated).
- Any independent Android-only, education-specific CPI benchmark at matching granularity.
- Any Ukraine-specific CPI figure, for any category, from any publisher.
- Verbatim Google text for the "filtered from charts" and "ad exposure permitted" phrasings.

## Leads

- Google's Developer Policy Center "Store Listing and Promotion Requirements" hub may carry the
  explicit enforcement language the Help-Center subpages lack.
- AppsFlyer's Performance Index / State of App Marketing PDFs likely carry the dated, sourced
  version of the CPI figure with methodology.
- Insider Intelligence/eMarketer has a Ukraine mobile-game CPI forecast page (paywalled).
