# Epic 7 Context: Store, premium, boxes, purchases and refunds

<!-- Generated from planning artifacts. Regenerate with compile-epic-context if planning docs change. -->

## Goal

This epic delivers the commerce layer on the restored candidate: a store surface priced by server-owned gold prices (money prices belong to Play), premium state computed from server grants so expiry and both doubled regeneration effects are exact, gift boxes where the ten-day streak gift is automatic and the Premium daily gift is an explicit no-ad claim, and Play purchase/restore/refund flows with server-side provenance so pending or unverified purchases grant nothing and refunds touch only the original grant. The ТЗ10B dependency audit lands here as its own dependency-only commit. Commerce is financial fact — the epic is done only when grant/refund integrity is proven on a real Play sandbox, making the owner's boxes/premium acceptance matrix runnable.

## Stories

- Story 7.1: External owner input — record Play Console inventory (SKUs, money prices, license tester, billing setup); no invented SKUs or prices
- Story 7.2: Store surface on server-owned gold prices; normalized premium comparison; unconfigured products visibly unavailable
- Story 7.3: Premium from server-owned grants — expiry and both doubled regeneration effects, holds/overlaps, independent remaining time
- Story 7.4: Boxes — automatic ten-day streak grant, explicit Premium daily gift claim, separate source limits over common stock
- Story 7.5: Online box opening survives loss/restart by returning the same stored reward
- Story 7.6: Real Play sandbox matrix — purchase/restore/refund in both orders and concurrently, partial premium refund, foreign/duplicate rejection, restore with sales off
- Story 7.7: Server-side purchase provenance and enforcement — verification before grant, token fence, immutable refund receipts
- Story 7.8: Dependency audit as a separate dependency-only commit with current checks green

## Requirements & Constraints

**Store and pricing**
- Gold prices are server-owned constants; money prices, currency and availability come from Play ProductDetails, never hardcoded.
- Show full prices; premium durations may be normalized per 30 days for comparison only.
- Unconfigured/missing SKU or price renders as explicitly unavailable with a reason — never a silently purchasable item or invented placeholder.
- Changed payable terms require fresh consent before a new purchase.
- Client double-tap protection on all purchase/claim actions.

**Premium**
- Premium state (active, expired, unknown freshness, pending purchase/refund) derives from server grants; the client's hasPremium flag or countdown animation is presentation, never the source of truth.
- Both regeneration effects are doubled over valid grant intervals, including holds, overlaps and each grant's independent remaining time; server computes with real intervals and saved remainders.

**Boxes and gifts**
- One box type with a common stock. The ordinary ten-day streak grant is automatic on entry and independent of Premium. The Premium daily gift requires an explicit claim without watching an ad; entering the app never triggers it.
- Each source keeps its own day keys, limits and receipts; other awards do not consume these limits; Premium grants no unlimited repeats.
- Box opening is online only: exactly one result and receipt is fixed before the reward is shown; replay after response loss, process death or restart returns the same stored reward — no re-roll, no second credit.

**Purchases and refunds**
- Authoritative Play verification binding account, app, product, order and quantity must precede any grant; pending or unverified purchases (including ambiguous re-verification) stay pending and grant nothing.
- A permanent token-level fence prevents re-grant; refund state is cumulative immutable receipts with monotonic refundable quantity — not a boolean flag.
- RTDN/push notifications trigger re-verification; they are never trusted as a money command by themselves.
- A premium refund removes only the unused remainder of the specific grant; already-used time, parallel grants and gifts are preserved (no global premiumUntil reset).
- Foreign-account and duplicate grants are rejected; account binding is checked server-side, recovery on another UID does not adopt the purchase.
- Disabling new sales never disables query/recovery/ACK/replays/refunds of already-paid purchases.
- Never log purchase tokens, order IDs or personal data; use redacted identifiers/hashes in evidence.

**Acceptance reality**
- Fake Billing is tested separately from real Play. Real sandbox cases require the story 7.1 inventory and owner devices; unavailable Play/device checks stay explicitly NOT_RUN — never simulated with an emulator result.
- The dependency audit is a separate commit: current audit, minimal compatible updates (no force-fix, no blind major jumps), clean install from lock, then current Node/HTTP/Firebase/rules gates green — dependency-only changes never ride along with financial logic changes.

## Technical Decisions

- Purchase integrity: verification-before-grant with the token fence, cumulative refund receipts and RTDN-as-trigger is the adopted architecture; existing server routes (`applyShopPurchase`, `verifyPurchase`, purchase-*, `premium-grants`, box services, RTDN handler) already exist — the work is integration and hardening on top of them, not new clean cores. A state/transition table with the actual proof source is required before changing refund logic.
- Premium and boxes are computed from provenance and server time: server-owned grants/used/remaining define premium intervals and both regenerations. Old rules (general cap of 2 boxes, box-for-five-ads, ad-gated premium gift) are dropped and must not reappear.
- Offline daily streak observations are not unconditional proof of a visit; timezone changes, clock tampering and repeated events follow existing anti-cheat constraints.
- Operations follow the shared money contract: stable operation identity fixed before dispatch, retry of a known key returns the stored authoritative result, one ledger effect per original operation, non-negative balances and conservation under races; receipts report facts, the client never commands credits. Feature intents go through the single outbox; no direct queue/Firestore writes.
- Prices and terms are consumed from a consistent frozen config snapshot; a failed refresh preserves last-known values, never a zero price; accepted operations keep their captured terms.
- Updates are additive: migrations preserve tables, data, pending intents and old receipts; no destructive reset or uninstall fallback; rollback never replays an old database over newer financial facts.
- Every claim needs real evidence: real device/server results, demo fixtures only create preconditions, statuses PASS/FAIL/NOT RUN preserved.

## UX & Interaction Patterns

- Premium screen: full prices visible, per-30-day normalization labeled as comparison; clear durations and payment methods (RU/UK/EN).
- Premium gift UI: an explicit «Получить подарок» button on the existing screen, without an ad; no auto-claim on entry.
- Typed states, not spinners-or-success: unavailable-with-reason, insufficient funds, pending purchase/refund, expired, unknown freshness are each distinguishable; balance and rewards are confirmed by the server (a pending animation may show, but the server confirms the grant).
- In UI diagnostics and shared logs, tokens, original order IDs and private payloads are excluded.

## Cross-Story Dependencies

- Story 7.1 is an external owner (hitl) input; its inventory gates the real sandbox cases of 7.6 — without it those cases are NOT_RUN, while the server/gold stories do not wait on it.
- 7.6 also waits on 7.7 (server provenance enforcement) and epic-1's identified candidate and devices.
- 7.5 builds on 7.4's box sources and limits.
- All server/gold stories rest on epic-2 foundations (config consistency CAP-16, wallet/ledger/receipts); fresh-consent behavior in 7.2 depends on the CAP-16 terms handling.
- 7.8 must land as its own commit, verified on the candidate's actual base from epic-1, separate from all financial changes.
- Out of scope here: wallet history projections (epic-social-features), Arena pricing (epic-arena-completion), Play Console setup inventory (epic-candidate-rig; sandbox cases shared with epic-final-acceptance).
