ROOT = "/workspace/projects/schoolquiz3.0/"

def edit(path, old, new, must=True):
    src = open(ROOT + path).read()
    if old not in src:
        assert not must, (path, old[:80])
        return
    open(ROOT + path, "w").write(src.replace(old, new, 1))
    print("edited", path, "::", old.strip().splitlines()[0][:60])

# ── arena-entry-runtime.test.js ──
edit("functions/arena-entry-runtime.test.js",
"""    "admin/review/lessons/lesson": {ownerUid: "author", questId: "quest", submissionId: "submission",
      status: "PUBLISHED", sourceLanguages: ["en"]},
    "admin/review/lessons/lesson/reviews/tester": {kind: "TESTING", reviewerUid: "tester",
      acceptedByServer: true, score: 3, reviewerLevelAtSubmit: 100, submissionId: "submission"},
    "admin/review/lessons/lesson/reviews/logic": {kind: "LOGIC", reviewerUid: "reviewer",
      acceptedByServer: true, score: 3, reviewerLevelAtSubmit: 100, submissionId: "submission"},
""",
""")
edit("functions/arena-entry-runtime.test.js",
"""    [`lesson_ratings/${id}`]: {ratingSum, ratingCount, ratingVersion},
    [`admin/review/lessons/${id}`]: {ownerUid: `author-${id}`, questId: `quest-${id}`,
      submissionId: `submission-${id}`, status: "PUBLISHED", sourceLanguages: ["en"]},
    ...Object.fromEntries(["TESTING", "LOGIC"].map((kind) => [`admin/review/lessons/${id}/reviews/${kind}`, {
      kind, reviewerUid: `${kind}-${id}`, acceptedByServer: true, score: 3, reviewerLevelAtSubmit: 100,
      submissionId: `submission-${id}`,
    }])),
  };""",
"""    [`lesson_ratings/${id}`]: {ratingSum, ratingCount, ratingVersion},
  };""")

# ── arena-lesson-catalog.test.js ──
edit("functions/arena-lesson-catalog.test.js",
"""    [`admin/review/lessons/${lessonId}`]: {ownerUid: "author", questId: `quest-${lessonId}`, submissionId: `submission-${lessonId}`,
      status: "PUBLISHED", sourceLanguages: ["en"]},
    ...Object.fromEntries(["TESTING", "LOGIC"].map(kind => [`admin/review/lessons/${lessonId}/reviews/${kind}`, {
      kind, reviewerUid: kind, acceptedByServer: true, score: 3, reviewerLevelAtSubmit: 100, submissionId: `submission-${lessonId}`,
    }])),
  };""",
"""  };""")

# ── arena-lifecycle-views-handler.test.js ──
edit("functions/arena-lifecycle-views-handler.test.js",
"""    [`admin/review/lessons/${lessonId}`]: {ownerUid: authorUid, questId: ids.quest,
      submissionId: ids.submission, status: "PUBLISHED", sourceLanguages: ["en"]},
    [`admin/review/lessons/${lessonId}/reviews/testing-${lessonId}`]: {kind: "TESTING",
      reviewerUid: `PRIVATE_TESTER_${lessonId}`, acceptedByServer: true, score: 3,
      reviewerLevelAtSubmit: 100, submissionId: ids.submission},
    [`admin/review/lessons/${lessonId}/reviews/logic-${lessonId}`]: {kind: "LOGIC",
      reviewerUid: `PRIVATE_LOGIC_${lessonId}`, acceptedByServer: true, score: 3,
      reviewerLevelAtSubmit: 100, submissionId: ids.submission},
""",
"")

# ── sync-changes.test.js: the review journal is gone ──
T = "functions/sync-changes.test.js"
edit(T, "  ADMIN_REVIEW_SYNC_CHANGES,\n", "")
edit(T, "  JOURNAL_REVIEW,\n", "")
edit(T, "  adminSyncChangePath,\n", "")
edit(T,
"""  writeSyncChanges(batch, db, {kind: "review_assignment", id: "sub-1_lesson-1"}, NOW);
""", "")
edit(T,
"""  // По одному свежезаписанному документу на каждый из четырёх путей.""",
"""  // По одному свежезаписанному документу на каждый из трёх путей.""")
edit(T,
"""    assert.deepStrictEqual([...journalsSeen].sort(), [...JOURNALS].sort(), "все четыре журнала получили запись");""",
"""    assert.deepStrictEqual([...journalsSeen].sort(), [...JOURNALS].sort(), "все три журнала получили запись");""")
edit(T,
"""    assert.strictEqual(db.read(publicCatalogSyncChangePath("cat-1", "quest", "q-1")).id, "q-1");
    assert.strictEqual(db.read(adminSyncChangePath("sub-1_lesson-1")).id, "sub-1_lesson-1");
    // Журнал ревью раньше писал тело без `type` — теперь оно есть.
    assert.strictEqual(db.read(adminSyncChangePath("sub-1_lesson-1")).type, "review_assignment");""",
"""    assert.strictEqual(db.read(publicCatalogSyncChangePath("cat-1", "quest", "q-1")).id, "q-1");""")
edit(T,
"""  assert.strictEqual(privateSyncChangePath("u-1", "cat-1", "q-1"), "private/u-1/sync_changes/cat-1_q-1");
  assert.strictEqual(adminSyncChangePath("sub-1_l-1"), "admin/review/sync_changes/sub-1_l-1");""",
"""  assert.strictEqual(privateSyncChangePath("u-1", "cat-1", "q-1"), "private/u-1/sync_changes/cat-1_q-1");""")
edit(T,
"""
  const reviewPlan = planJournalBackfill(JOURNAL_REVIEW, [
    {id: "sub-1_l-1", data: {id: "sub-1_l-1", assignmentId: "sub-1_l-1", lessonId: "l-1", changedAtMs: NOW}},
  ]);
  assert.deepStrictEqual(reviewPlan.writes, [
    {id: "sub-1_l-1", data: {type: "review_assignment", id: "sub-1_l-1", changedAtMs: NOW}},
  ]);
  assert.deepStrictEqual(reviewPlan.deletes, []);""",
"")
edit(T,
"""  assert.strictEqual(journalOfCollectionPath("private/u-1/sync_changes"), JOURNAL_PRIVATE);
  assert.strictEqual(journalOfCollectionPath(ADMIN_REVIEW_SYNC_CHANGES), JOURNAL_REVIEW);""",
"""  assert.strictEqual(journalOfCollectionPath("private/u-1/sync_changes"), JOURNAL_PRIVATE);""")
