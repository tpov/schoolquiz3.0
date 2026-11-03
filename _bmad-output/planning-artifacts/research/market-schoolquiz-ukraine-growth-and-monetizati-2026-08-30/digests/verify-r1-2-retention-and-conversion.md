# Verifier digest — round 1, #2: retention and payer conversion

Scope: load-bearing claims #48 (education retention) and #45 (freemium conversion).
Firewall: verifier had brief only, no project context.

## Claim #48 — "Education D30 often below 3%, against a 5–7% cross-vertical median"

**Verdict: verified on direction, medium confidence. Independence not fully established.**

- Independent publisher: Business of Apps, "Education App Benchmarks (2026)", accessed
  2026-08-31, https://www.businessofapps.com/data/education-app-benchmarks/ — "Retention rate for
  education apps was 2% by day 30, which is one of the lowest rates across all app sectors."
  Different publisher from Adjust/Pushwoosh/Plotline, agreeing on magnitude and direction.
- **Caveat the verifier raised itself:** the page would not open (HTTP 403), so what *it* cites
  upstream is unknown. The phrase "D30 often falls below 3%" recurs verbatim across several
  secondary sites, which is the signature of a citation chain re-quoting one upstream number
  rather than several independent measurements.
- No vendor with a distinct disclosed methodology (AppsFlyer, Sensor Tower, Similarweb) was found
  publishing its own education-specific D30.

**Consequence:** treat as probably real, not triple-sourced. The revenue model that rests on it
should be read as directionally sound with a soft floor, not as a precise 2–3%.

## Claim #45 — "Freemium converts 2.1% download-to-paid by day 35 vs 10.7% hard paywall"

**Verdict: verified against the primary source — with a definition finding that matters more
than the number.**

- Primary, fetched directly and consistently twice: RevenueCat, *State of Subscription Apps 2026
  — Education*, https://www.revenuecat.com/state-of-subscription-apps-2026-education, accessed
  2026-08-31: "Hard paywalls crush freemium on conversion: Apps that ask for money upfront convert
  5x better than freemium (10.7% vs. 2.1%)." The metric is explicitly defined there as **"the
  share of installs that result in at least one paid subscription within 35 days of the install
  date"**.
- **Conflation finding.** RevenueCat's own blog summary presents the same two figures under the
  heading "Day 35 Trial-to-Paid Conversion" — contradicting the primary report's own definition.
  The *number* is stable across both pages; the *label* is not. Anyone citing this figure needs to
  know which metric they are quoting.
- Independent publisher: First Page Sage, "SaaS Freemium Conversion Rates: 2026 Report", accessed
  2026-08-31, https://firstpagesage.com/seo-blog/saas-freemium-conversion-rates/ — freemium-to-paid
  averages 3.7%, EdTech specifically 2.6%. Same order of magnitude, same direction, **but a
  different population**: B2B SaaS free-tier conversion over months, not mobile install-to-paid
  in 35 days. Soft corroboration, not replication.

**A second conflation, this one in the import rather than in RevenueCat.** The RevenueCat figure
measures conversion to a **paid subscription**. A coin-and-charges economy monetised by
consumable IAP and rewarded video is a different mechanism with a different conversion curve.
Carrying a subscription benchmark into a consumable-IAP model is a category error, and the import
made it silently.

## Ukraine / tier-2-3 payer conversion: NOT FOUND

No publisher with a Ukraine-specific payer-conversion or ARPU figure. Closest proxy — Admiral
Media's "Mobile App Marketing Benchmarks 2026" (search snippet only) — states tier-1 CPI runs 3–8×
tier-3 and tier-1 LTV runs 4–12× tier-3, which supports "lower monetisation outside tier-1"
directionally but names no percentage and does not classify Ukraine into a tier.

## Leads

- RevenueCat's raw 2026 dataset/PDF would settle the label discrepancy at source and may carry a
  CEE line item.
- Sensor Tower, data.ai/Similarweb, or AppsFlyer State of App Marketing 2026 for an education
  retention figure with disclosed methodology — would give genuine cross-publisher independence.
- Adapty's education-app subscription benchmarks post carries pricing medians; worth a fetch for
  retention and conversion rather than pricing alone.
