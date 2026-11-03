"""Re-derive the initiative kernel from its capability journal and adopted sources.

Unchanged kernel fields use the resumed, preservation-checked September 30
derivation. All amendments below are recorded through the shared journal helper
before the derived SPEC is written. This is a documentation tool, not app code.
"""
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path('/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate')
WORK = ROOT / '_bmad-output/specs/spec-schoolquiz3.0'
HERE = Path(__file__).parent
SOURCE_BASE = Path('/tmp/schoolquiz-spec-refresh-20261001/baseline')
BASE = HERE / 'baseline'
MEMLOG = '/workspace/projects/schoolquiz3.0/_bmad/scripts/memlog.py'

CAPS = {
  2: ('Author and revise lessons',
      'An author can create and correct their own learning material.',
      "Authorship, submitted revisions and change history remain attributable; permitted edits cannot silently overwrite another author's published material. Existing lesson titles stay immutable and lessons have no description. FillBlank and protected translation ranges retain the exact field, revision and text occurrence through editing and undo/redo, without exposing answer metadata to players."),
  3: ('Review and publish approved questions',
      "Independent reviewers can assess submitted content, and authors can see each question's review outcome.",
      'All required directions examine the concrete revision; successful questions can publish independently, affected dependencies are rechecked and unaffected valid evidence is retained. One independent TESTING review per question/revision is sufficient; its configurable pass threshold defaults to 2 on the 1–3 scale (1 fails, 2/3 pass). Review returns identify the failed stage without a mandatory reason field. Translation decisions are explicit per segment and complete before submission; missing text is never auto-approved. Saved review work resumes from confirmed progress, while an active timed question follows the common online timing rule.'),
  4: ('Pay for a review cycle and receive earned review rewards',
      'Authors can submit material at an agreed fee and reviewers can receive the defined reward for completed work.',
      'The accepted tariff is shown and charged once; retry and retained prior evidence create no extra paid work. A normal completed cycle, including justified rejection, conserves fee = reward entitlements + burned remainder under the adopted exact-arithmetic contract; an unfinished cycle is not finally settled.'),
  6: ('Discuss lessons and report errors privately',
      'A learner can open comments or privately report a lesson error, and recipients can find relevant incoming items.',
      "Both post-lesson actions exist; private free text concerns the whole lesson without re-revealing questions or answers and reaches its permitted author. The shared inbox preserves each item's original readers and lesson link; reviewer or curator roles alone grant no access to that private message."),
  11: ('Create funded competitions and qualify participants in a tournament',
       'An organizer can create a funded competition from eligible material and players can complete its qualifying tournament.',
       'The course remains in the library, sufficient content and the full fund are frozen, and retries reuse the event. An ordered, extensible prize list counts every place once. Three distinct EASY attempts feed an immutable top16 calculated from the whole eligible cohort. A private course tournament admits users who downloaded the course or arrive by its link only during qualification; the link never bypasses selection for the championship.'),
  12: ('Play championship matches and settle the competition',
       'Qualified players can agree a championship match time, play their match and see the final bracket and awards.',
       'Proposals and confirmations remain distinct; the agreed slot and both players’ READY states survive re-entry. Opponents receive the same HARD form and authoritative timing under the common online transition rule. Absences, ties, byes and technical outcomes follow the adopted matrix; ranking, awards and returned remainder use the same frozen prize list and conserve the fund. The timing and prize-mapping integration prerequisites remain explicit before their dependent adapters.'),
  14: ('Receive and open boxes',
       'A player can collect boxes from entitled sources and open one for its reward.',
       'The ordinary reward after a ten-day daily streak is granted automatically on entry, independently of Premium. The separate Premium daily gift requires an explicit claim without watching an ad. Both sources retain their own limits and common stock; other awards do not consume those limits, and an online opening survives loss/restart by returning the same stored reward.'),
  17: ('Use the finalized redesigned experience',
       'Users can complete every current MVP journey in the agreed design.',
       'Existing and new screens, including exams and certificates, implement the final DESIGN and EXPERIENCE with actionable loading, empty, failure, offline, insufficient funds, forbidden, pending and recovery states in RU/UK/EN. Shared visual semantics, accessibility and confirmed interactions are preserved. This version contains neither the deferred experience-based guide nor guide placeholders; final UX approval does not replace runtime acceptance.'),
  24: ('Create polls and view the distribution after voting',
       'An author can create a poll series and participants can submit their choices and inspect its results.',
       'Single- or multiple-choice questions have 2–8 options and no correctness or EASY/HARD classification. A nonempty choice set is submitted once; before the participant’s confirmed vote the distribution is hidden. Percentages use distinct respondents per question, so multiple-choice totals may exceed 100%; retry never adds another vote, and series progress and statistics retain their identity.'),
  25: ('Create and take private quizzes',
       'An author can share a HARD-only private quiz with signed-in participants and inspect their attempts.',
       'The category is «Приватные квизы». Authors control attempt limits, result visibility and optional link expiry; defaults are one attempt, hidden result and no expiry. Counted attempts survive limit changes, and closing access does not interrupt a started attempt within its original deadlines. Participants use no charges and cannot defer continuation. Recovery timeout yields a whole-quiz result with remaining questions unanswered. Current visibility governs every own result, including past attempts, while author statistics are retained; all detailed rules in AD-29 remain binding.'),
  26: ('Inspect personal and author-lesson operation history',
       'An account holder can inspect their income and spending, and an author can inspect earnings for their lesson.',
       'General history and lesson earnings show authorized records with consistent identity, date, reason, signed amount, resource and status. Filters, expansion and continued loading preserve context; pending is distinct from confirmed income, unlike resources are not summed, and changing accounts cannot reveal another owner’s records. Reading history creates no financial effect.'),
}

DECISIONS = [
 ('decision', '2026-10-01 adopt final DESIGN.md, EXPERIENCE.md and .working/implementation-handoff.md from ux-schoolquiz3.0-2026-09-28 as mandatory companions alongside MAIN AD1..31. Later explicit owner decisions and their finalized UX/architecture override historical PRD/exam prose on the same point; adopted files remain owned by their originating workflows. Final current UX retires blanket repeated screen-content approval, discovery-shell status and required current guide. FR56 is deferred; current release contains no guide/placeholders. Keep OQ1 retired, retire OQ3 without reusing it.'),
 ('constraint', '2026-10-01 common AD31 timing supersedes independent next-question recovery choices in ALL online runs: original question deadline continues through disconnect; timely accepted answer or original expiry anchors captured server default60s to connect/download the next question. Expiry with no accepted answer is unanswered, retries never reset clocks or replace accepted answers. No extra last-question gap, no delay when ready, initial bounded start retained, ordinary/Arena offline paths retained. ONLINE-O1 competition global cutoff reconciliation and ONLINE-O2 shared delivery/ready-ACK remain technical prerequisites; do not shorten60s or silently extend stages.'),
 ('constraint', '2026-10-01 review admission is per question/revision: one independent TESTING reviewer, server threshold default2 on1..3, no extra quorum/assignment average; other directions remain. Translation tri-state decisions bind source/target/question/segment/revision, missing text cannot be accepted, current-question completeness gates Next and full completeness gates final submit. Evidence-save pause does not pause an active timed TESTING question. Normal complete review cycle includes justified rejection; OQ2 only concerns a separately added permanent partial-cycle termination.'),
 ('constraint', '2026-10-01 editor annotations bind field/revision/exact occurrence and preserve text plus marks through edit/undo/redo; protected range, blank and correctness differ. FillBlank uses1–3 distinct blanks and5/10 unique candidates without visible marker syntax/duplicate answer list. Empty new slot differs from cleared existing question; invalid drafts remain saveable. Shared serialization/index/migration O6 precedes independent adapters; player/exam projections redact answer-revealing metadata.'),
 ('constraint', '2026-10-01 competition terms: турнир is first qualification, чемпионат is entire second final bracket/match part; technical tournament/event IDs unchanged. Dynamic ordered prizes count each row once, no doubled third place; one canonical rank/tie/technical-outcome mapping is required before CREATE/reserve. PROPOSE differs from CONFIRM; individual confirmations/READY and immutable agreed slot recover from authorized projections. Course download OR valid link gives admission only in [qualification.start,end); no organizer whitelist or direct championship bypass.'),
 ('constraint', '2026-10-01 economy inheritance: exam entry STANDARD-only100/200 points (1/2 full ordinary charges); legacy final300 is implementation drift. No plasma entry split. Ordinary ten-day streak grant is automatic; Premium gift is a separate explicit no-ad claim with independent source keys/limits and shared stock. Own operation history and author lesson earnings are scoped read projections, pending distinct from confirmed, resources distinct, no financial effect on reads.'),
 ('constraint', '2026-10-01 private quiz owner choices fully closed in UX-O4/AD29: category Приватные квизы, flat HARD, SchoolQuiz account, no charges or paid helper actions, no inferred replacement fee. Defaults1attempt/hidden/noexpiry. Author may set expiry or disable early; starts check current enabled/expiry/limit atomically. Limit changes retain startedCount, active attempts retain original completion rights, no reset/extension/deferred resume. Current show-result applies to all own attempts including completed; server read/retry redacts with current permission and stale data cannot restore a hidden projection. Author stats unchanged. Recovery timeout retains accepted answers and calculates whole frozen quiz with remaining unanswered and terminal timeout reason. Link closure/limit reduction do not abort already started attempts. No future rotation/reactivation/guest/passfail policy is added.'),
 ('decision', '2026-10-01 kernel coverage adds CAP24 polls, CAP25 private quizzes, CAP26 own/lesson operation history after unchanged IDs1..23. Amend CAP2/3/4/6/11/12/14/17 and charge wording of CAP21/22 only; preserve remaining capabilities and detailed assessment rewards/corrections/certificates/coverage, Arena proof, ordinary offline, migration/account isolation and full acceptance via existing adopted companions.'),
 ('question', 'OQ-6 (technical): agree ONLINE-O1 competition total/stage cutoff with the complete60s transition window before the affected competition adapter, and ONLINE-O2 shared delivery/ready-ACK identity/receipt/window-completion/answer-start contract before independent next-question adapters. Owner timing policy is settled; existing runtime compatibility is not assumed.'),
 ('question', 'OQ-7 (technical): satisfy named UX integration contracts before dependent adapters: O2 canonical dynamic prize mapping before CREATE/reserve, O3 meeting read/ACK, O4 private admission/config/result contracts, O6 annotation serialization/migration, O7 vote identity/statistics/series, O8 real inbox/Play/exam adapters, O10 scoped projections/history continuation and O12 independent gift receipts. UX-O1/O4/O5 product choices are closed; HTML is not backend evidence.'),
 ('question', 'OQ-8 (future scope): only before separately enabling FR56 define experience-guide copy, triggers, placement, dismissal/repetition and persisted/migrated seen state. Current scope explicitly has no guide or placeholders; this is not a blocker for the guide-free candidate.'),
 ('note', 'Wrapper-only content on October1 derive: omit coaching/reviewer chatter, HTML publishing workflow, infrastructure URLs/accounts, fixture counts/people and duplicate intermediate alternatives. Preserve actual owner rules, authority/acceptance boundaries and technical prerequisites through mandatory companions. SPEC-REVIEW.html is a local derived view, not a contract companion; no publish or stories in this update.'),
]

def append(kind, text):
    subprocess.run(['uv', 'run', '--cache-dir', '/tmp/schoolquiz-bmad-uv', MEMLOG,
                    'append', '--workspace', str(WORK), '--type', kind, '--text', text],
                   check=True, capture_output=True, text=True)

def capabilities():
    result = {}
    for line in (WORK / '.memlog.md').read_text().splitlines():
        match = re.match(r'^- \(capability\) CAP-(\d+)(?: retained)? — (.*?)\. Intent(?:: (.*?)| unchanged)\. Success: (.*)$', line)
        if not match:
            continue
        number = int(match[1])
        intent = match[3] + '.' if match[3] is not None else result[number]['intent']
        result[number] = {'title': match[2], 'intent': intent,
                          'success': match[4].split(' Binds:')[0]}
    return result

def baseline_sections():
    raw = (BASE / 'SPEC.md').read_text()
    parts = re.split(r'^## (.*?)\n', raw, flags=re.M)
    return {parts[i]: parts[i+1].strip() for i in range(1, len(parts), 2)}

def record():
    BASE.mkdir(exist_ok=True)
    for source in SOURCE_BASE.iterdir():
        target = BASE / source.name
        assert not target.exists(), 'record is a one-time operation'
        shutil.copy2(source, target)
    for kind, text in DECISIONS:
        append(kind, text)
    for number, (title, intent, success) in CAPS.items():
        append('capability', f'CAP-{number} retained — {title}. Intent: {intent} Success: {success}'
               if number <= 23 else f'CAP-{number} — {title}. Intent: {intent} Success: {success}')
    # The already adopted assessment clauses are preserved verbatim, with charge
    # clarified from their current authoritative architecture.
    existing = capabilities()
    for number, charge in [(21, '100 STANDARD points (one full ordinary charge)'),
                           (22, '200 STANDARD points (two full ordinary charges)')]:
        c = existing[number]
        success = c['success'] + f' Entry costs {charge}; PLASMA cannot pay admission.'
        append('capability', f"CAP-{number} retained — {c['title']}. Intent: {c['intent']} Success: {success}")
    append('event', '2026-10-01 refresh decisions and capability amendments recorded; deriving kernel and owned companions next. CAP1..23 IDs retained; CAP24..26 newly allocated. No assumptions or new product choices introduced.')
    print(f'Recorded accepted amendments and {len(CAPS) + 2} capability entries through shared memlog helper')

def derive():
    base = baseline_sections()
    caps = capabilities()
    assert sorted(caps) == list(range(1, 27))
    constraints = base['Constraints'].split('\n')
    replacements = {
      '- The scope is every PRD': '- Scope covers PRD FR-1–55/NFR-1–8, EXAM:CAP-1–21 and the finalized October 1 UX additions. FR-56 remains a named future capability, deferred from this version. Mandatory companions retain detailed numbers, formulas, acceptance cases and failure matrices; short summaries do not narrow them. Qualified IDs and source precedence are in contract-map.md.',
      '- The finalized architecture fixes': '- The finalized architecture fixes ownership and boundaries. Preserve KMP/Clean Architecture, Decompose/Compose/Koin and separate ordinary, Arena, exam and competition lifecycles. Shared AD-31 timing does not introduce a common mode controller; technical tournament/event identifiers remain stable.',
      '- Independent checks approve': '- Independent checks approve a concrete immutable revision; preserve valid unaffected evidence and frozen issued banks. TESTING is per question with one independent reviewer and a server threshold defaulting to 2 of 3; other directions and explicit translation completeness remain mandatory. Online evidence writes pause on connection loss, but an active timed question continues under AD-31. Approved ordinary/Arena offline paths remain; no pretest or statistical admission gate is added.',
      '- Freeze accepted price': '- Freeze accepted prices, banks and rule terms; server/UTC govern deadlines and seasons, with local timezone/DST presentation. Every online question keeps its original deadline through disconnect. A timely accepted answer or original expiry starts the captured default60-second next-question connection/download window; retry never resets it or overwrites an accepted answer. Ready content appears immediately, no window follows the last question, and the separate bounded initial start remains. Offline ordinary/Arena behavior is retained. ONLINE-O1/O2 are explicit integration prerequisites.',
      '- Screen content requires': '- Final DESIGN.md and EXPERIENCE.md dated 2026-10-01, their implementation handoff and later accepted owner decisions govern the current visuals, content, actions and states. They supersede obsolete discovery, blanket repeated screen-content approval and old HTML values. Current code, production data, permissions and accessibility still require implementation and acceptance; final UX is not runtime proof.',
      '- Theme tests and final exams use': '- Theme tests and final exams use ordinary HARD answer time for every question, including the five EASY theme questions, without the extra0.75 factor. Preserve difficulty, composition, first-acknowledgement deadline and frozen timer rules; subsequent online transitions obey AD-31. Theme/final entry costs100/200 STANDARD points only; the old final300 configuration is implementation drift, not a reopened tariff choice.',
    }
    for prefix, replacement in replacements.items():
        matches = [i for i, line in enumerate(constraints) if line.startswith(prefix)]
        assert len(matches) == 1, prefix
        constraints[matches[0]] = replacement
    constraints += [
      '- Product terms distinguish «турнир» (qualification) from «чемпионат» (the complete final bracket/match part); a competition contains both. Private course access is by downloaded course or link only within the qualification interval. Prize rows each count once; a shared rank/tie/technical-outcome mapping precedes CREATE/reserve, and confirmation/READY never follows merely from a local selection.',
      '- Private quiz rules in AD-29 are settled: signed-in HARD-only, no charge use, one continuous sitting, current result visibility for all own attempts, retained author statistics and started-attempt history. New starts require enabled, unexpired access and startedCount < currentLimit; neither link closure nor a limit change revokes an active attempt’s original completion rights. Defaults are one attempt, hidden result and no expiry. Recovery timeout scores the whole quiz with remaining questions unanswered; no new pass/fail gate or replacement fee is inferred.',
      '- Poll votes and private quiz results remain separate from scored lesson progress and its awards. Preserve the final category matrix, question schemas, per-occurrence editor annotations and player/exam redaction; adopted UX and shared serialization/admission contracts prevent feature-local alternatives.',
      '- Ordinary ten-day streak grants are automatic; Premium gifts require an independent explicit no-ad claim. Keep source-specific limits/receipts and shared stock. Personal operation history and author-lesson earnings are authorized read projections with distinct resources and pending states, never commands that create money.',
    ]
    non_goals = base['Non-goals'].split('\n') + [
      '- The experience-based user guide or guide placeholders in this version; FR-56 can be enabled only as a separately specified future change.',
      '- Guest/password-based private quizzes, private deferred continuation, charge-funded private helper actions, inferred replacement fees, or unrequested link rotation/reactivation.',
    ]
    success = base['Success signal']
    success = success.replace('Author/review/publication, ordinary learning, Arena, the complete tournament event, commerce, configuration, feedback and exam-to-public-certificate journeys',
        'Author/review/publication, ordinary learning, Arena, tournament-to-championship, polls, private quizzes, operation history, commerce, configuration, feedback and exam-to-public-certificate journeys')
    success += '\n- Exercise the accepted question-timeout/next-download boundary without timer resets, extra answer time or a final-question wait. Prove one-reviewer TESTING and translation completeness; vote replay uniqueness; private link/limit changes with retained attempts, current visibility of old results and whole-quiz timeout outcomes; prize conservation, restored meeting confirmations, independent gift claims and scoped history.'
    questions = [line for line in base['Open questions'].split('\n') if not line.startswith('- **OQ-3:**')]
    questions += [
      '- **OQ-6 — technical integration:** Resolve MAIN ONLINE-O1 (competition total/stage cutoff versus the full next-question window) before the affected competition adapter, and ONLINE-O2 (one delivery/ready-ACK receipt, replay and answer-start contract) before independent online transition adapters. The common timing policy is settled.',
      '- **OQ-7 — implementation contracts:** Complete the named UX handoff contracts before their dependent adapters: prize mapping before CREATE/reserve, meeting read/ACK, private settings/admission/results, annotation serialization/migration, poll votes/series/statistics, inbox/Play/exam operations, scoped projections/history continuation and separate gift receipts. Closed product choices UX-O1/O4/O5 are not new approval requests; use the owners and gates in MAIN Deferred.',
      '- **OQ-8 — future scope:** Only before separately enabling FR-56 define guide copy, experience triggers, placement, dismissal/repetition and persisted/migrated seen state. This version has no guide or placeholders; these future choices do not block its acceptance.',
    ]
    raw = (BASE / 'SPEC.md').read_text()
    header = raw.split('---', 2)[1].strip().replace("updated: '2026-09-30'", "updated: '2026-10-01'")
    adopted = '\n'.join('  - ../../planning-artifacts/ux-designs/ux-schoolquiz3.0-2026-09-28/' + name
                         for name in ['DESIGN.md', 'EXPERIENCE.md', '.working/implementation-handoff.md'])
    header = header.replace('sources: []', adopted + '\nsources: []')
    why = 'Deliver one complete, reviewable Android MVP in which learners study, demonstrate course knowledge and earn certificates; authors publish independently checked material; and ordinary learning, Arena, competitions, polls, private quizzes and commerce work together. “Remaining” means missing implementation or current acceptance, not a request to recreate existing work.'
    cap_text = '\n\n'.join(f"- **CAP-{n} — {c['title']}**\n  - **intent:** {c['intent']}\n  - **success:** {c['success']}" for n,c in sorted(caps.items()))
    fields = [('Why',why), ('Capabilities',cap_text), ('Constraints','\n'.join(constraints)),
              ('Non-goals','\n'.join(non_goals)), ('Success signal',success),
              ('Open questions','\n'.join(questions))]
    output = f'---\n{header}\n---\n\n# SchoolQuiz — full Android MVP\n\n' + '\n\n'.join(f'## {name}\n\n{body}' for name,body in fields) + '\n'
    for old, new in [('default60-second', 'default 60-second'), ('extra0.75', 'extra 0.75'),
                     ('costs100/200', 'costs 100/200'), ('final300', 'final 300')]:
        output = output.replace(old, new)
    (WORK / 'SPEC.md').write_text(output)
    print(f'Re-derived SPEC.md from {len(caps)} latest capability records and logged source amendments')

if __name__ == '__main__':
    if sys.argv[1:] == ['record']:
        record()
    elif sys.argv[1:] == ['derive']:
        derive()
    else:
        raise SystemExit('use record once, then derive')
