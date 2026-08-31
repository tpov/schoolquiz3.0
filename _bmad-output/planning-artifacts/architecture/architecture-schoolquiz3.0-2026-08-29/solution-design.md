# Solution design — theme tests, the final exam, and the certificate

The prose companion to `ARCHITECTURE-SPINE.md`. The spine is the contract; this explains how the pieces move and why they are shaped the way they are. Where the two disagree, the spine wins.

## The one idea everything follows from

The app is offline-first. A lesson is played on the device, scored on the device, and uploaded later; the server re-checks the arithmetic but never sees the play. That model is correct for learning and wrong for assessment, because a score the device computes is a score the device can forge — and a certificate is worth exactly as much as the hardest thing to forge behind it.

So this feature runs a second model beside the first, and the boundary between them is a single physical fact: **is the correct answer on the device?**

Easy questions reveal their answer during play, so hiding it is theatre. They stay entirely in the old model — synced with their answer, scored on the device, immediate result.

Hard questions never reveal anything. So their answers are not published at all. The device gets the question and nothing else, collects what the player picked, and learns the outcome when the server says. That one change turns hard play into something the device cannot fake, and it is what makes an exam drawn from hard questions meaningful.

Everything else in the design is downstream of that sentence.

## What a sitting actually looks like

A sitting is a conversation with the server, and the server holds every piece of state that matters.

The player taps start. The server checks, in order: is every lesson of this theme passed; is the ladder rung reached; has the cooldown for *this exam* run out; is there enough charge; is the scorable pool big enough to draw from. Only after all five does it draw — and only then does it take the charge, so a refused start never costs anything.

The draw is twenty questions, uniformly at random, from an index built when the course was published rather than assembled on the spot. A few unscored pretest questions ride along, indistinguishable from the rest.

Each question goes out in two parts: the image first, then the text. Text is the fast half, and its arrival is what the client acknowledges. The per-question clock starts at that acknowledgement, not at issue, so a slow connection costs the player nothing — and the acknowledgement itself is bounded, so a slow connection cannot be claimed as a way to buy time. Go quiet for longer than the gap allows and the sitting ends: the remaining questions score as wrong, the sitting is recorded, and it consumes its cooldown. Abandoning a bad run buys nothing.

Nothing comes back after an answer except an acknowledgement. Not right, not wrong, not a running total. The acknowledgement is deliberately identical every time, because in total silence any pixel that varies reads as a verdict.

At the end the server scores what it stored and returns the percent, the pass or fail, and a ranked list of lessons to revisit. Never which question was missed — that is the difference between a retake that is study and a retake that is search.

## Why the result names lessons and not questions

This is the load-bearing piece of the whole design and it is easy to erode by accident.

The pass mark holds because the player never learns which items they failed. Show a per-question verdict and unlimited retakes stop being persistence and become a search with feedback: answer blind, see which eight were wrong, change eight guesses, sit again. Three sittings and the bar is gone.

But a result that is only a number is a dead end, and a dead end twenty-three hours long is where people close the app for good. So the result names topics: which lessons the wrong answers came from, ranked. The player learns where they are weak without learning which item betrayed them, and tomorrow becomes preparation instead of waiting.

The one trap is arithmetic. One wrong answer produces one named lesson, and if that lesson contributed exactly one question to the draw, naming it *is* naming the item. So the advice is fixed-length, it appears at every score including a perfect one, and a lesson that contributed too few items is never named.

## Admission, and why it is one small record

The final exam admits a player who has passed every theme test of the course. That could have been a chain of events, aggregates and completion effects — and the first draft of this design assumed it would be. It turned out to be one row.

Per player, per theme: the best easy percent, the best hard percent, and whether the theme is passed. Written by the same call that scores the sitting, in the same transaction, so there is never a moment where a result exists and its admission does not. Values only improve, which makes the write idempotent under replay and out-of-order delivery — and the theme's stars are read from the same row, so the aggregate is not new at all. It is the thing the theme screen was already drawing, finally given a name.

Admission then compares the set of passed theme ids against the course's non-archived themes at the player's course version. A set, not a count, because an author adding a theme moves a count and would admit whoever slipped through first. Non-archived, because an author retiring a theme must not permanently lock out everyone who passed it.

## Who catches a wrong question

Once no player ever learns which item they failed, a mis-keyed hard question becomes invisible. It quietly costs everyone marks and nobody can contest it. This is the hole that opens the moment the previous section's rule is enforced, and it has to be closed in the same design.

Certification exams solved it long ago: a new question rides in live sittings **unscored** until it has proven itself. It looks like every other question, the player answers it, and it contributes to nobody's score. What it contributes is data.

The signal is measurable without anyone seeing an answer. A working question is answered more often by players who did well overall than by players who did badly. A mis-keyed one inverts that — the strong get it wrong, because they know the material and the key does not. That inversion is the detector.

A confirmed bad question is quarantined rather than deleted: it leaves the draw pool but stays in the counts, so removing it can never drop a course below its certification threshold and strand a player mid-course. Every sitting it touched is rescored, in both directions — a wrong key marks some players wrong who were right and some right who were wrong, and fixing only the first half is not fixing it. Because rescoring can lower a value, it is the single authorised exception to the admission record's monotonicity, and afterwards that record is recomputed from the player's sittings rather than merged.

Players can also flag a question as unclear. They are told nothing about correctness in return.

## The certificate

One per course, issued only by the final exam's terminal state, signed by the server and verified only by the server. It carries the sitting that earned it, the percent, the date, the pass mark in force, and the size of the bank it was drawn from — because the course keeps changing and the verification page does not. Without that snapshot the page would eventually assert something that was true and stopped being true.

It is valid for a year. That is not a limitation bolted on; it is what makes the claim honest. With unlimited retakes at one a day, "passed this course" means the holder passed once and kept coming back. "Passed this course within the last year, at this percent, against a bank of this size" is a claim the design can actually support, and it is what real certifications say.

Two things it does not claim. It attests an account, not a person — there is no proctoring, and pretending otherwise would be the dishonest part. And it depends on the server being alive: verification runs through us, so a certificate outlives the domain by exactly zero days. Packing the signed payload into the link would let the page survive as a static file, and that is written down as deferred rather than discovered later from a user's email.

## What the author sees

A course certifies at twelve theme tests, each backed by a hundred easy and a hundred hard questions. That is twenty-four hundred questions, and it is deliberately a lot: the draw is twenty, and a bank five times the draw is what keeps a sitting from being the whole pool.

The failure mode to design against is not the number. It is a person spending six months on a course, pressing publish, and being told they have 183 questions and no certificate. So it is not a check at all — it is a line present from the course's first minute showing distance: *147 of 200*. A counter, not a verdict, with one sentence saying why the number exists. A course below the bar publishes normally as a full course that simply awards no certificate, never as something defective.

The count itself is subtler than it looks. Publication sees one submission at a time and re-publishes edited questions through the same path, so anything counting by increment would credit a sixty-question course with two hundred after four edits. It counts sets of ids, and it counts only the questions that can actually be examined on — promoted, not quarantined — so the line can never read "200 of 200" while no sitting can open.

And once a course is certifiable, its author cannot break it. The publication pass refuses an update that would drop it below the bar. That is what makes the whole frozen-versus-live eligibility question disappear: there is nothing to adjudicate, because the degradation never happens.

## Where the code goes

The session machinery — lifecycle, delivery, deadlines, terminal states — lives in `shared/core/online-session` and knows nothing about exams. Theme tests and the final exam are its first callers, passing in their draw scope and pass rule. Tournaments are meant to be the second; today they are not, and if that intent is dropped the machinery should collapse back into the feature rather than sit in core on a promise.

The scoring arithmetic moves out of `lesson-runner` into `shared/core/scoring`, taking its value types with it, because four callers now need it. On the server there is exactly one JavaScript scorer, and it absorbs the existing verification arithmetic rather than sitting beside it — two implementations that drift by one tenth would show different star counts on the screen the certificate chain hangs off.

The exam result is rendered exactly as it arrives. The client does not recompute stars from the percent, however tempting it is now that the formula sits in core.

## What this design does not defend against

A player who can automate reading answers out of the local database can equally point a language model at the question. That is unsolved everywhere, and the honest position is to say so rather than to build theatre against it. What helps is content design — options that are all plausible with only the taught one more likely, and texts short enough that the answer window stays small — and that belongs to quest authoring, not here.

What this design does defend is narrower and real: the score cannot be forged, the answers to hard questions are not on the device, a mis-keyed question is caught before it costs anyone, and the certificate says exactly what it can support.
