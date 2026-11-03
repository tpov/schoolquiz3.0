# Purchase verification

Companion to `SPEC.md` (CAP-6). The contract for the Cloud Function that stands between a Play
receipt and a balance change. It does not exist yet; this is what it must do.

## The order of operations, and why it is not negotiable

```
pay → token → server verifies with Play → server credits → server says "credited" → client consumes
```

Two reorderings are tempting and both lose money:

- **Credit on the device, verify later.** Gold is minted by anyone who can edit a request. This is
  the standard in-app-purchase fraud, not an edge case.
- **Consume before the server credits.** Play does not return a consumed token. Any failure between
  consume and credit takes a paying customer's money and leaves no way to recover it — not for
  support, not for the customer.

## What the function receives and must establish

Input: the purchase token, the SKU, and the caller's authenticated uid.

It must establish, in this order, refusing at the first failure:

1. **The caller is authenticated.** An unauthenticated call has no account to credit.
2. **Play agrees the token exists**, is for this package, matches the SKU claimed, and is in the
   purchased state — established by calling the Play Developer API, never by trusting the payload.
3. **The token has not already been settled.** Keyed on the token itself, which is what makes the
   whole path idempotent.
4. **The SKU is one this server sells.** An unknown SKU is refused rather than credited at zero.

Only then does it credit, from the server-owned constants table, in the same transaction that
records the token as settled. Credit and settle-record must not be two transactions: a crash
between them either double-credits or loses the purchase, depending on which order they were in.

## Idempotency

The token is the idempotency key. The same token presented ten times credits once and returns the
same success answer each time — because it will be presented more than once. Play re-delivers
unconsumed purchases on every connection, which is exactly the mechanism that makes "pay, then the
process dies, then still get your gold" work.

## Pending purchases

Play returns PENDING for payment methods that settle later. A pending purchase grants nothing and
is not an error. It resolves later through the same path when Play reports it as purchased, and
the client must not treat pending as either outcome — that is the single most common way a store
integration both loses money and gives goods away.

## Refunds and revocation

A refunded or revoked purchase must claw back the grant, or the refund is a free gold generator.
Play's real-time developer notifications are the signal. Whether a claw-back may drive a balance
negative, and what happens if the gold has already been spent, is a decision this spec does not
make — it is a real gap and belongs in the open questions when the function is designed.

## Audit

Every settlement writes a record naming the uid, the token, the SKU, the amount credited, and the
constants-table version the amount came from. The version matters: a price change must never be
retroactive, and without recording which table a decision was made under there is no way to prove
that afterwards.

## What the client is allowed to conclude

Nothing, until the server answers. The client's own view of a purchase is a claim; the server's
answer is the fact. On success the client consumes the purchase and refreshes the balance from the
server rather than adding the amount it thinks it bought.
