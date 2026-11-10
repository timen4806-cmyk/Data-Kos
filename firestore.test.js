const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validRecordPayload(uid) {
  return {
    userId: uid,
    encryptedPayload: "QmFzZTY0RW5jcnlwdGVkUGF5bG9hZERhdGExMjM0NTY=",
    iv: "MTIzNDU2Nzg5MDEyMzQ1Ng==",
    encryptionVersion: 1,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

function validVaultPayload(uid) {
  return {
    userId: uid,
    salt: "U2FsdDE2Qnl0ZXNMb25nU2FsdA==",
    keyCheckCiphertext: "Q2lwaGVydGV4dFdhcmdhS29zVjE=",
    keyCheckIv: "MTIzNDU2Nzg5MDEyMzQ1Ng==",
    twoFactorEnabled: true,
    biometricEnabled: true,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
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

test("Unauthenticated user cannot read or write warga_kos", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("warga_kos").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("warga_kos").doc("rec_1").set(validRecordPayload(ALICE_UID))
  );
});

test("Owner can create, list with matching filter, update, and delete warga_kos", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("warga_kos").doc("rec_1");
  await assertSucceeds(docRef.set(validRecordPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("warga_kos").where("userId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    docRef.update({
      encryptedPayload: "VXBkYXRlZEVuY3J5cHRlZFBheWxvYWREYXRhMTIzNDU=",
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(docRef.delete());
});

test("Cross-user access to warga_kos and security vault is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(BOB_UID).collection("warga_kos").doc("rec_bob").set(validRecordPayload(BOB_UID));
    await adminDb.collection("users").doc(BOB_UID).collection("security").doc("vault").set(validVaultPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("warga_kos").doc("rec_bob").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("warga_kos").where("userId", "==", BOB_UID).get()
  );
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("security").doc("vault").get());
});

test("Shadow update with ghost field or spoofed userId is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("warga_kos").doc("rec_ghost");
  await assertFails(docRef.set({ ...validRecordPayload(ALICE_UID), isAdmin: true }));
  await assertFails(docRef.set(validRecordPayload(BOB_UID)));
});

test("Invalid document ID is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badRef = aliceDb.collection("users").doc(ALICE_UID).collection("warga_kos").doc("bad id with spaces!");
  await assertFails(badRef.set(validRecordPayload(ALICE_UID)));
});

test("Owner can create and update security vault doc only", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const vaultRef = aliceDb.collection("users").doc(ALICE_UID).collection("security").doc("vault");
  await assertSucceeds(vaultRef.set(validVaultPayload(ALICE_UID)));
  await assertSucceeds(vaultRef.update({ biometricEnabled: false, updatedAt: new Date() }));
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("security").doc("other").set(validVaultPayload(ALICE_UID))
  );
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("security").get());
});
