import * as admin from "firebase-admin";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import * as logger from "firebase-functions/logger";

const REGION = "asia-northeast3";
const INVITE_CODE_LENGTH = 6;
const INVITE_CODE_TTL_DAYS = 7;
const NO_RESPONSE_TIMEOUT_MIN = 5;

function generateRandomCode(length: number): string {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 1, I, 0, O 제외
  let result = "";
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

async function isHeadManager(uid: string | undefined): Promise<boolean> {
  if (!uid) return false;
  const adminDoc = await admin.firestore().doc(`admins/${uid}`).get();
  if (!adminDoc.exists) return false;
  const role = adminDoc.data()?.role;
  return role === "HEAD_MANAGER" || role === "SUPER_ADMIN";
}

/**
 * generateRestaurantInviteCode (admin only, P0)
 * 사용자(원규씨, HEAD_MANAGER)가 식당 가입 코드 발급
 * P1+ 귀속 모드 활성화 시 사무실 매니저도 발급 가능하도록 확장
 */
export const generateRestaurantInviteCode = onCall(
  { region: REGION },
  async (request) => {
    if (!(await isHeadManager(request.auth?.uid))) {
      throw new HttpsError("permission-denied", "관리자만 가입 코드를 발급할 수 있습니다.");
    }
    const { provinceId, cityId, officeId, notes } = request.data || {};
    if (!provinceId || !cityId || !officeId) {
      throw new HttpsError("invalid-argument", "provinceId, cityId, officeId가 필요합니다.");
    }

    const codesRef = admin.firestore()
      .collection("provinces").doc(provinceId)
      .collection("cities").doc(cityId)
      .collection("offices").doc(officeId)
      .collection("restaurantInviteCodes");

    // 중복 회피: 최대 5회 시도
    let code = "";
    for (let attempt = 0; attempt < 5; attempt++) {
      code = generateRandomCode(INVITE_CODE_LENGTH);
      const existing = await codesRef.doc(code).get();
      if (!existing.exists) break;
      if (attempt === 4) {
        throw new HttpsError("internal", "코드 발급 실패 (중복).");
      }
    }

    const expiresAt = Timestamp.fromMillis(Date.now() + INVITE_CODE_TTL_DAYS * 24 * 60 * 60 * 1000);

    await codesRef.doc(code).set({
      used: false,
      usedByRestaurantId: null,
      createdBy: "PLATFORM", // P0: 사용자 admin만
      notes: notes || "",
      createdAt: FieldValue.serverTimestamp(),
      expiresAt,
    });

    logger.info(`[restaurant] generateInviteCode - office: ${officeId}, code: ${code}`);
    return { code, expiresAt: expiresAt.toMillis() };
  }
);

/**
 * redeemRestaurantInviteCode (식당앱 익명 인증)
 * 식당이 가입 코드로 가입 → restaurants doc 생성 + 코드 used=true
 */
export const redeemRestaurantInviteCode = onCall(
  { region: REGION },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "익명 인증이 필요합니다.");
    }
    const { code, provinceId, cityId, officeId, name, phone, address, taxiPhoneNumber, fcmToken } = request.data || {};
    if (!code || !provinceId || !cityId || !officeId) {
      throw new HttpsError("invalid-argument", "code, provinceId, cityId, officeId가 필요합니다.");
    }
    if (!name || !phone) {
      throw new HttpsError("invalid-argument", "식당명과 전화번호가 필요합니다.");
    }

    const officeRef = admin.firestore()
      .collection("provinces").doc(provinceId)
      .collection("cities").doc(cityId)
      .collection("offices").doc(officeId);
    const codeRef = officeRef.collection("restaurantInviteCodes").doc(code);

    const result = await admin.firestore().runTransaction(async (tx) => {
      const codeSnap = await tx.get(codeRef);
      if (!codeSnap.exists) {
        throw new HttpsError("not-found", "유효하지 않은 코드입니다.");
      }
      const codeData = codeSnap.data()!;
      if (codeData.used) {
        throw new HttpsError("already-exists", "이미 사용된 코드입니다.");
      }
      const expiresMs = codeData.expiresAt?.toMillis?.() ?? 0;
      if (expiresMs && expiresMs < Date.now()) {
        throw new HttpsError("deadline-exceeded", "만료된 코드입니다.");
      }

      const restaurantRef = officeRef.collection("restaurants").doc();
      tx.set(restaurantRef, {
        name,
        phone,
        address: address || "",
        taxiPhoneNumber: taxiPhoneNumber || "",
        points: 0,
        ownerUid: uid,
        fcmToken: fcmToken || "",
        registeredOfficeId: officeId,
        acquiredBy: codeData.createdBy === "PLATFORM" ? "PLATFORM" : codeData.createdBy, // P0=PLATFORM, P1+=officeId
        createdAt: FieldValue.serverTimestamp(),
      });

      tx.update(codeRef, {
        used: true,
        usedByRestaurantId: restaurantRef.id,
        usedAt: FieldValue.serverTimestamp(),
      });

      return { restaurantId: restaurantRef.id };
    });

    logger.info(`[restaurant] redeem - code: ${code}, restaurantId: ${result.restaurantId}, ownerUid: ${uid}`);
    return {
      restaurantId: result.restaurantId,
      provinceId,
      cityId,
      officeId,
    };
  }
);

/**
 * createSharedCallFromRestaurant (식당앱 익명 인증)
 * 식당앱이 호출 → shared_calls 생성 (sourceRestaurantId, paymentMethod 포함)
 * P0 = PLATFORM acquired (모든 사무실 broadcast)
 */
export const createSharedCallFromRestaurant = onCall(
  { region: REGION },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "익명 인증이 필요합니다.");
    }
    const {
      restaurantId, provinceId, cityId, officeId,
      departure, destination, paymentMethod
    } = request.data || {};
    if (!restaurantId || !provinceId || !cityId || !officeId) {
      throw new HttpsError("invalid-argument", "restaurantId, provinceId, cityId, officeId가 필요합니다.");
    }
    if (paymentMethod && !["CASH", "RESTAURANT_POINT"].includes(paymentMethod)) {
      throw new HttpsError("invalid-argument", "paymentMethod는 CASH 또는 RESTAURANT_POINT여야 합니다.");
    }

    const restaurantRef = admin.firestore()
      .collection("provinces").doc(provinceId)
      .collection("cities").doc(cityId)
      .collection("offices").doc(officeId)
      .collection("restaurants").doc(restaurantId);

    const restaurantSnap = await restaurantRef.get();
    if (!restaurantSnap.exists) {
      throw new HttpsError("not-found", "식당을 찾을 수 없습니다.");
    }
    const restaurantData = restaurantSnap.data()!;
    if (restaurantData.ownerUid !== uid) {
      throw new HttpsError("permission-denied", "본인 식당만 호출할 수 있습니다.");
    }

    const expiresAt = Timestamp.fromMillis(Date.now() + 30 * 24 * 60 * 60 * 1000); // 30일
    const expiresAt5min = Timestamp.fromMillis(Date.now() + NO_RESPONSE_TIMEOUT_MIN * 60 * 1000);

    const sharedCallRef = admin.firestore().collection("shared_calls").doc();
    await sharedCallRef.set({
      phoneNumber: restaurantData.phone,
      contactName: restaurantData.name,
      contactAddress: restaurantData.address || "",
      sourceProvinceId: provinceId,
      sourceCityId: cityId,
      sourceOfficeId: officeId, // 행정 소속
      sourceRestaurantId: restaurantId, // 식당앱 발생 표시 (PLATFORM 분기 키)
      targetProvinceId: provinceId,
      targetCityId: cityId,
      status: "OPEN",
      callType: "RESTAURANT", // 신규 type (식당앱 발생)
      paymentMethod: paymentMethod || "CASH",
      departure: departure || restaurantData.address || "",
      destination: destination || "",
      timestamp: FieldValue.serverTimestamp(),
      timestampClient: Date.now(),
      expireAt: expiresAt,
      expiresAt5min, // 5분 미응답 알림용
      notifiedNoResponse: false,
      fromRestaurantApp: true,
    });

    logger.info(`[restaurant] createSharedCall - restaurantId: ${restaurantId}, paymentMethod: ${paymentMethod}, sharedCallId: ${sharedCallRef.id}`);
    return { sharedCallId: sharedCallRef.id };
  }
);

/**
 * notifyRestaurantOnNoResponse (scheduled, 매분)
 * shared_calls 중 status=OPEN AND expiresAt5min < now AND notifiedNoResponse=false
 * → 식당 fcmToken으로 "사무실 미응답" 알림 발송 + notifiedNoResponse=true
 */
export const notifyRestaurantOnNoResponse = onSchedule(
  {
    region: REGION,
    schedule: "every 1 minutes",
    timeZone: "Asia/Seoul",
  },
  async () => {
    const now = Timestamp.now();
    const snap = await admin.firestore()
      .collection("shared_calls")
      .where("status", "==", "OPEN")
      .where("notifiedNoResponse", "==", false)
      .where("expiresAt5min", "<", now)
      .limit(50)
      .get();

    if (snap.empty) return;

    const promises: Promise<void>[] = [];
    for (const doc of snap.docs) {
      const data = doc.data();
      const sourceRestaurantId = data.sourceRestaurantId;
      if (!sourceRestaurantId) {
        // 식당앱 콜 아니면 skip (notifiedNoResponse만 true 처리)
        promises.push(doc.ref.update({ notifiedNoResponse: true }).then(() => undefined));
        continue;
      }

      const restRef = admin.firestore()
        .collection("provinces").doc(data.sourceProvinceId)
        .collection("cities").doc(data.sourceCityId)
        .collection("offices").doc(data.sourceOfficeId)
        .collection("restaurants").doc(sourceRestaurantId);

      promises.push((async () => {
        try {
          const restSnap = await restRef.get();
          const fcmToken = restSnap.data()?.fcmToken;
          if (fcmToken) {
            await admin.messaging().send({
              token: fcmToken,
              notification: {
                title: "사무실 미응답",
                body: "5분간 콜 응답이 없습니다. 다른 호출을 시도하세요.",
              },
              data: {
                type: "RESTAURANT_NO_RESPONSE",
                sharedCallId: doc.id,
              },
            });
            logger.info(`[restaurant] noResponse 알림 - restaurantId: ${sourceRestaurantId}, sharedCallId: ${doc.id}`);
          }
          await doc.ref.update({ notifiedNoResponse: true });
        } catch (e) {
          logger.error(`[restaurant] noResponse 알림 실패 - sharedCallId: ${doc.id}`, e);
        }
      })());
    }

    await Promise.all(promises);
    logger.info(`[restaurant] noResponse 알림 처리 완료 - 건수: ${snap.size}`);
  }
);
