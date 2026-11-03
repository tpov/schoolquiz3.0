# Private Arena catalog HTTP evidence

`scripts/mvp-arena-catalog-emulator-test.js` is a bounded, opt-in synthetic harness,
not an application startup or production deployment script. Read its fixed target,
source-hash assertions and mutation guards before running it. Each run creates and
retains up to two anonymous emulator accounts and exactly 29 new marked documents.
It changes one own lesson title using frozen create/update-time preconditions.
It never deletes data, invokes a financial callable, reserves tickets, or runs a worker.

Root-reviewed first execution: `arena-catalog-http-r1.log`, private
`demo-schoolquiz-mvp`, run `ac_0ecb34aeec8b4557b13a390f10b3a548`.
Six assertion groups passed in 9.125 seconds, 16 HTTP requests, peak RSS 99,964 KiB.
The source/rules hashes matched before and after every phase.

- The real callable denied unauthenticated access and unexpected body fields.
- The actual root eligibility loader and catalog returned the same baseline snapshot;
  its successful read-only transaction consumed 13 reads for one raw placement.
- Both fresh accounts received identical safe cards: three new lessons plus the one
  existing eligible lesson. Equal exact ratings had equal prices; the unrated price was 1.
- All four direct authenticated reads of the new raw placement/private history returned 403.
- A single own title change invalidated the old cursor; a fresh read saw the new title.
- Five before/after proofs included exact data, createTime and updateTime, and absence
  of own financial rows. No money, ticket, receipt, quota or premium effect occurred.

The root launcher verified an internal Docker network, sole Firebase network membership
and no actual published ports. The test used pinned Node 22.23.1, 192 MiB memory,
256 MiB memory+swap, 0.5 CPU, read-only repository/script mounts and no cloud credentials.
It preserved every synthetic fixture. Repeating it requires a new explicitly reviewed run;
never weaken source hashes or repair old data automatically after a failed assertion.

This proves the small actual HTTP fixture, not 1,000-lesson capacity, multi-page production
performance, a usable Android Arena screen, the author review workflow or public readiness.
The existing raw-placement influence on prepare/catalog prices remains a known release gap.
