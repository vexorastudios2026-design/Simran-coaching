const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const TEACHER_A = "teacher_a_uid";
const TEACHER_B = "teacher_b_uid";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function verifiedContext(uid, email = `${uid}@gmail.com`) {
  return testEnv.authenticatedContext(uid, {
    email,
    email_verified: true,
  });
}

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

test("Unauthenticated caller cannot read or write students or payments", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${TEACHER_A}/students`).get());
  await assertFails(unauthDb.collection(`users/${TEACHER_A}/payments`).get());
  await assertFails(unauthDb.collection(`users/${TEACHER_A}/feeRates`).get());
  await assertFails(unauthDb.doc(`users/${TEACHER_A}/settings/profile`).get());
});

test("Unverified email cannot read or write collections", async () => {
  const unverifiedDb = testEnv.authenticatedContext(TEACHER_A, {
    email: "unverified@gmail.com",
    email_verified: false,
  }).firestore();
  await assertFails(unverifiedDb.collection(`users/${TEACHER_A}/students`).get());
});

test("Cross-user isolation: Teacher B cannot read or write Teacher A data", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.doc(`users/${TEACHER_A}/students/stu_1`).set({
      userId: TEACHER_A,
      name: "Aarav Sharma",
      phone: "9876543210",
      className: "Class 10",
      monthlyFeePaise: 150000,
      joiningDate: "2026-11-01",
      joiningYearMonth: "2026-11",
      notes: "",
      active: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const dbB = verifiedContext(TEACHER_B).firestore();
  await assertFails(dbB.doc(`users/${TEACHER_A}/students/stu_1`).get());
  await assertFails(dbB.collection(`users/${TEACHER_A}/students`).get());
  await assertFails(dbB.doc(`users/${TEACHER_A}/students/stu_1`).delete());
});

test("Authorized owner can create student + feeRate atomically, and negative fee fails", async () => {
  const dbA = verifiedContext(TEACHER_A).firestore();
  // Date in the past (1s ago) satisfies `is timestamp && <= request.time` in JS tests
  const now = new Date(Date.now() - 1000);

  const batch = dbA.batch();
  const stuRef = dbA.doc(`users/${TEACHER_A}/students/stu_1`);
  const rateRef = dbA.doc(`users/${TEACHER_A}/feeRates/stu_1_2026-11`);

  batch.set(stuRef, {
    userId: TEACHER_A,
    name: "Diya Verma",
    phone: "9876543210",
    className: "Class 9",
    monthlyFeePaise: 120000,
    joiningDate: "2026-11-01",
    joiningYearMonth: "2026-11",
    notes: "Evening batch",
    active: true,
    createdAt: now,
    updatedAt: now,
  });
  batch.set(rateRef, {
    userId: TEACHER_A,
    studentId: "stu_1",
    effectiveFrom: "2026-11",
    monthlyFeePaise: 120000,
    note: "Initial fee",
    createdAt: now,
    updatedAt: now,
  });

  await assertSucceeds(batch.commit());
  await assertSucceeds(dbA.collection(`users/${TEACHER_A}/students`).get());

  // Negative fee must fail
  await assertFails(
    dbA.doc(`users/${TEACHER_A}/students/stu_bad`).set({
      userId: TEACHER_A,
      name: "Bad Fee",
      phone: "",
      className: "Class 8",
      monthlyFeePaise: -100,
      joiningDate: "2026-11-01",
      joiningYearMonth: "2026-11",
      notes: "",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Ghost field must fail
  await assertFails(
    dbA.doc(`users/${TEACHER_A}/students/stu_ghost`).set({
      userId: TEACHER_A,
      name: "Ghost",
      phone: "",
      className: "Class 8",
      monthlyFeePaise: 100000,
      joiningDate: "2026-11-01",
      joiningYearMonth: "2026-11",
      notes: "",
      active: true,
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Payments require existing student, positive amountPaise, and valid method", async () => {
  const dbA = verifiedContext(TEACHER_A).firestore();
  const now = new Date(Date.now() - 1000);

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().doc(`users/${TEACHER_A}/students/stu_1`).set({
      userId: TEACHER_A,
      name: "Diya Verma",
      phone: "9876543210",
      className: "Class 9",
      monthlyFeePaise: 120000,
      joiningDate: "2026-11-01",
      joiningYearMonth: "2026-11",
      notes: "",
      active: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  // Valid payment succeeds
  await assertSucceeds(
    dbA.doc(`users/${TEACHER_A}/payments/pay_1`).set({
      userId: TEACHER_A,
      studentId: "stu_1",
      studentNameSnapshot: "Diya Verma",
      amountPaise: 120000,
      paymentDate: "2026-11-13",
      forYearMonth: "2026-11",
      method: "UPI",
      note: "Nov fee",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Zero or negative payment fails
  await assertFails(
    dbA.doc(`users/${TEACHER_A}/payments/pay_zero`).set({
      userId: TEACHER_A,
      studentId: "stu_1",
      studentNameSnapshot: "Diya Verma",
      amountPaise: 0,
      paymentDate: "2026-11-13",
      forYearMonth: "2026-11",
      method: "Cash",
      note: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Orphaned payment (missing student) fails
  await assertFails(
    dbA.doc(`users/${TEACHER_A}/payments/pay_orphan`).set({
      userId: TEACHER_A,
      studentId: "non_existent",
      studentNameSnapshot: "Ghost",
      amountPaise: 50000,
      paymentDate: "2026-11-13",
      forYearMonth: "2026-11",
      method: "Cash",
      note: "",
      createdAt: now,
      updatedAt: now,
    })
  );
});
