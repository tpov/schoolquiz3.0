# Play Console inventory (story 7.1) — browser session as oleg12794 — 2026-10-19

Access: oleg12794@gmail.com is already signed in in the ZCode in-app browser (second Google
account alongside tpov4oleg@gmail.com); it has developer access to the **TPOV** developer
account (id 6672082928150299984). No passwords were needed.

## BLOCKER LIFTED (the 16.10 one)

The internal-testing opt-in link
(https://play.google.com/apps/internaltest/4701382105842016287) under oleg12794 now shows
**«You're a tester for School Quiz (com.tpov.schoolquiz)»** — the 48h invitation window from
16.10 has passed and oleg12794 is an ACTIVE internal tester. Screenshot in this directory.
Unblocks: 7.6 sandbox purchases on the Pixel (the Play build 40000/4.0.0 is already installed
there per the 16.10 notes).

## In-app product inventory (Монетизація → Продукти → one-time products): 6 items

| product id | name | created | status |
|---|---|---|---|
| gold_pack_small | Gold Pack Small — 10 gold | 10.10.2026 | buy option `gold-pack-small-buy` ACTIVE, 174 countries |
| gold_pack_medium | Gold Pack Medium — 60 gold | 10.10.2026 | 1 buy option |
| gold_pack_large | Gold Pack Large — 150 gold | 10.10.2026 | 1 buy option |
| donate_1 | Coffe | 03.10.2023 | 1 buy option |
| donate_5 | Cake | 03.10.2023 | 1 buy option |
| donate_20 | TeamMeal | 03.10.2023 | 1 buy option |

Prices (gold_pack_small, from the purchase-option price table): **Ukraine 11,99 UAH;
United States 0,19 USD** (no VAT, +7 territories with other values). Medium/large price rows
not opened yet (same table pattern, one navigation each).

The three gold packs (10.10.2026) map exactly to the locked store cards
(Small/Medium/Large gold pack) the device run saw in 2026-10-18-pixel-commerce-hitl.

## Subscriptions: 4 (created 11.10.2026)

premium_1d, premium_7d, premium_365d — **0 active base plans** (not purchasable);
premium_30d — 1 active base plan + 1 offer. The store's Premium card can only ever sell
1-month until the other base plans are activated (a console action).

## License testing (from the 16.10 shift, unchanged)

oleg12794@gmail.com on the License testers list; Google Group SchoolQuiz3.0 (5 members);
License testing ENABLED; RTDN verified end-to-end; SA key rotated (d6fcf09c in /etc/tpov).

## Store health findings (outside 7.1 but recorded)

- «Деякі останні зміни відхилено» — production update REJECTED 15.10:
  **invalid privacy policy** (users data policy section) — the listing's policy URL is invalid;
  plus API-target violations on the live production 3010 (Apr 2024, pre-API-35) which restrict
  availability for new users. The 40000 build targets SDK 36, so the fix is the privacy policy
  URL (owner must provide a hosted valid policy) + resubmit (1 unsubmitted change is already
  staged: the «Фінансові функції» declaration was updated).
- The internal track itself is unaffected (the 16.10 edit completed; opt-in now open).

## Verdict

7.1's external input (Play Console inventory) is RECORDED: SKUs, names, active purchase
options, reference prices, license testers, billing setup (RTDN/SA key per 16.10). What
remains external: the privacy-policy URL for the production resubmission, and a decision on
activating premium_1d/7d/365d base plans. 7.6 (sandbox purchase/restore/refund on the Pixel)
is UNBLOCKED and ready to run.
