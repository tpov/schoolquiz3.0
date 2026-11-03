# Chat contract — the shape without the feature

Companion to `SPEC.md`. The owner's instruction: design moderation against a chat like the legacy
one, but ship no chat. This file is that contract — what a message will look like, so everything
built now (reports, bans, moderator grading) attaches to it unchanged when it lands. No screen, no
delivery, no sync is implied by anything here.

## The message

Legacy's shape (`legacy/network/.../ChatEntity.kt:8-19`), carried over with two corrections:

| Field | Type | Note |
|---|---|---|
| `id` | string | Document id. |
| `authorUid` | string | **Replaces legacy `tpovId`.** Same rule as lesson comments: pinned to the signed-in uid at create, or there is nobody to report or ban. |
| `authorNickname` | string | Display name at post time — same denormalisation as `lessonComments`. |
| `text` | string | Legacy `msg`. Same 1..1000 bound as comments. |
| `createdAtMs` | number | Legacy `time` was a string; a number sorts. |
| `importance` | number | Kept. The one legacy field that was actually read — it coloured the row at `importance == 7`. Server-written only. |

Dropped from legacy, deliberately:

- **`rating`, `reaction`** — the per-message rating slot existed and nothing ever wrote or read it
  in the legacy app's whole life. Moderation does not rate messages; it decides reports about them
  (see below). If message reactions ever become a feature, they are their own design, not a
  resurrected dead column.
- **`icon`** — derivable from the author's profile; denormalising it froze stale avatars.

## What moderation needs from this — and already has

The report object (story 8) names its target as `(targetType, targetId)`. The type set is:

```
COMMENT   -> lessonComments/{id}     (live today)
QUEST     -> quests/{id}             (live today)
USER      -> users/{uid}             (live today)
MESSAGE   -> chat message            (reserved; becomes live when the chat lands)
```

`MESSAGE` is reserved now precisely so the chat's arrival adds a routing case, not a schema
change. The takedown callable for a message mirrors `removeLessonComment` — delete plus an audit
record preserving the text — and the ban check is the same one: a banned user cannot post.

## What the chat must promise when it is built

1. Every message write goes through the same author-pinning rule as comments.
2. A banned user's writes are refused server-side, not hidden client-side.
3. Removal preserves the removed text in the moderation audit, as `removeLessonComment` does.
4. `importance` and any future promotion of a message is server-written; the author cannot
   decorate their own message.

Legacy's cautionary tale, for whoever builds it: the legacy chat screen shipped while `send` had
no callers, the remote listener was a `TODO`, and the new-message check returned `false` forever.
The screen is the last step, not the first.
