const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_dev_123";
const BOB_UID = "bob_dev_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any user collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Intruder",
      preferredLanguage: "th",
      xp: 0,
      streakDays: 0,
      totalExecutions: 0,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("UserProfile: owner create, partial update, cross-user denial, and future timestamp rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Valid create (matches Kotlin toCreateMap without @DocumentId)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Dev",
      email: "alice@example.com",
      preferredLanguage: "th",
      xp: 120,
      streakDays: 3,
      totalExecutions: 10,
      favoriteLanguages: ["Kotlin", "Python"],
      achievements: ["first_run"],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      xp: 150,
      preferredLanguage: "en",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user read/write fails
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      xp: 999999,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Ghost field & immutable createdAt mutation fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Future timestamp rejected
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      preferredLanguage: "th",
      xp: 0,
      streakDays: 0,
      totalExecutions: 0,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Projects and ProjectFiles: owner CRUD, parent existence check, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  const projectRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1");
  await assertSucceeds(
    projectRef.set({
      userId: ALICE_UID,
      name: "My Portfolio",
      description: "Responsive portfolio website",
      templateType: "website",
      primaryLanguage: "HTML",
      fileCount: 2,
      gitBranch: "main",
      createdAt: created,
      updatedAt: created,
    })
  );

  // List subcollection works even on empty files subcollection
  await assertSucceeds(projectRef.collection("files").get());

  // File creation succeeds when parent project exists
  const fileRef = projectRef.collection("files").doc("file_index_html");
  await assertSucceeds(
    fileRef.set({
      userId: ALICE_UID,
      projectId: "proj_1",
      path: "index.html",
      name: "index.html",
      language: "HTML",
      content: "<h1>Hello CodeVerse</h1>",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial file update works
  await assertSucceeds(
    fileRef.update({
      content: "<h1>Hello Updated</h1>",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject extra `id` field
  await assertFails(
    projectRef.collection("files").doc("file_extra_id").set({
      id: "file_extra_id",
      userId: ALICE_UID,
      projectId: "proj_1",
      path: "app.js",
      name: "app.js",
      language: "JavaScript",
      content: "console.log(1);",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Orphan file without parent project fails
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("missing_proj")
      .collection("files")
      .doc("file_1")
      .set({
        userId: ALICE_UID,
        projectId: "missing_proj",
        path: "main.py",
        name: "main.py",
        language: "Python",
        content: "print('hi')",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Bob cannot list or read Alice's projects or files
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1").collection("files").get()
  );
});

test("LearningProgress and DailyActivity: owner CRUD & validation bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  const progressRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("learningProgress")
    .doc("course_python");

  await assertSucceeds(
    progressRef.set({
      userId: ALICE_UID,
      courseId: "course_python",
      completedLessonIds: ["py_1", "py_2"],
      completedChallengeIds: ["py_ch_1"],
      bestQuizScore: 90,
      finalProjectDone: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    progressRef.update({
      bestQuizScore: 100,
      finalProjectDone: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Invalid quiz score > 100 fails
  await assertFails(
    progressRef.update({
      bestQuizScore: 150,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  const activityRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("dailyActivity")
    .doc("2026-11-24");

  await assertSucceeds(
    activityRef.set({
      userId: ALICE_UID,
      dateKey: "2026-11-24",
      editsCount: 5,
      runsCount: 3,
      lessonsCount: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("dailyActivity")
      .doc("2026-11-24")
      .get()
  );
});
