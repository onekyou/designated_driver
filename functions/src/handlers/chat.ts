/**
 * 콜마당 사무실 단톡방 채팅 — Cloud Functions
 *
 * 트리거 / 함수:
 *  1. onChatMessageCreated — 새 메시지 → FCM 발송
 *  2. addChatMember / removeChatMember — 멤버 동기화 헬퍼 (index.ts에서 호출)
 *  3. scheduledChatMessageCleanup — 90일 PII 자동 삭제 (매일 04:00 KST)
 *  4. backfillChatMembers — 운영 1회 호출 (Callable, 매니저 admin 전용)
 *
 * 관련 문서:
 *  - 플랜: memory/designated_drive/chat_feature_plan_2026-04-28.md
 *  - 공유 스펙: docs/chat-shared-spec.md
 */

import { onDocumentCreated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { buildMulticastFcmPayload } from "../utils/fcmPayload";

const REGION = "asia-northeast3";

// ============================================================
//  멤버 동기화 헬퍼 (index.ts에서 가입/탈퇴 시 호출)
// ============================================================

export type ChatMemberRole = "MANAGER" | "DESIGNATED_DRIVER" | "PICKUP_DRIVER";

/**
 * 채팅방 멤버 등록 — 가입 승인 시 호출
 */
export async function addChatMember(
  provinceId: string,
  cityId: string,
  officeId: string,
  userId: string,
  role: ChatMemberRole,
): Promise<void> {
  if (!provinceId || !cityId || !officeId || !userId) {
    logger.warn("[addChatMember] 필수 매개변수 누락", { provinceId, cityId, officeId, userId });
    return;
  }
  try {
    const memberRef = admin.firestore()
      .doc(`provinces/${provinceId}/cities/${cityId}/offices/${officeId}/chatRoom/main/members/${userId}`);
    await memberRef.set(
      {
        userId,
        role,
        joinedAt: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );
    logger.info(`[addChatMember] ${role} 멤버 등록: ${userId} @ ${officeId}`);
  } catch (e) {
    logger.error("[addChatMember] 실패", e);
  }
}

/**
 * 채팅방 멤버 제거 — 회원 거부/탈퇴 시 호출. 멱등성 보장 (이미 없으면 무시).
 */
export async function removeChatMember(
  provinceId: string,
  cityId: string,
  officeId: string,
  userId: string,
): Promise<void> {
  if (!provinceId || !cityId || !officeId || !userId) {
    logger.warn("[removeChatMember] 필수 매개변수 누락");
    return;
  }
  try {
    const memberRef = admin.firestore()
      .doc(`provinces/${provinceId}/cities/${cityId}/offices/${officeId}/chatRoom/main/members/${userId}`);
    await memberRef.delete().catch(() => { /* 이미 없으면 무시 */ });
    logger.info(`[removeChatMember] 멤버 제거: ${userId} @ ${officeId}`);
  } catch (e) {
    logger.error("[removeChatMember] 실패", e);
  }
}

// ============================================================
//  트리거: 새 메시지 → FCM multicast 발송
// ============================================================

interface ChatMessageDoc {
  id?: string;
  senderId?: string;
  senderName?: string;
  senderRole?: string;
  text?: string;
  createdAt?: Timestamp;
  clientCreatedAt?: number;
  status?: string;
}

/**
 * 채팅방 메시지 생성 트리거.
 *  사무실 모든 멤버 토큰 수집 → 발신자 본인 제외 → FCM multicast 발송.
 *  채팅 알림 채널은 클라이언트 앱이 type=NEW_CHAT_MESSAGE 수신 후 자체 채널로 표시.
 */
export const onChatMessageCreated = onDocumentCreated(
  {
    region: REGION,
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/chatRoom/main/messages/{messageId}",
  },
  async (event: any) => {
    const { provinceId, cityId, officeId, messageId } = event.params;

    if (!event.data) {
      logger.warn(`[onChatMessageCreated:${messageId}] event.data 없음`);
      return;
    }

    const msg = event.data.data() as ChatMessageDoc;
    if (!msg.senderId || !msg.text) {
      logger.warn(`[onChatMessageCreated:${messageId}] senderId 또는 text 누락`);
      return;
    }

    const senderId = msg.senderId;
    const senderName = msg.senderName ?? "알 수 없음";
    const senderRole = msg.senderRole ?? "MANAGER";
    const text = msg.text;
    const createdAtMs = msg.createdAt?.toMillis?.() ?? Date.now();
    const clientCreatedAt = msg.clientCreatedAt ?? createdAtMs;

    logger.info(
      `[onChatMessageCreated:${messageId}] from ${senderRole}/${senderName}/${senderId} @ office=${officeId}`,
    );

    try {
      const officeRef = admin.firestore()
        .doc(`provinces/${provinceId}/cities/${cityId}/offices/${officeId}`);

      // 1. 매니저 토큰 — managerTokens/{adminUid}.fcmToken (docId == authUid 가정)
      const managerSnapshot = await officeRef.collection("managerTokens").get();
      const managerTokens: string[] = managerSnapshot.docs
        .filter(d => d.id !== senderId)
        .map(d => d.data().fcmToken)
        .filter((t): t is string => typeof t === "string" && t.length > 0);

      // 2. 대리기사 토큰 — designated_drivers, fcmToken 보유 + authUid != sender
      const designatedSnapshot = await officeRef.collection("designated_drivers").get();
      const designatedTokens: string[] = designatedSnapshot.docs
        .filter(d => {
          const data = d.data();
          if (data.authUid === senderId || d.id === senderId) return false;
          return typeof data.fcmToken === "string" && (data.fcmToken as string).length > 0;
        })
        .map(d => d.data().fcmToken as string);

      // 3. 픽업기사 토큰 — pickup_drivers (V1 미배포 시 빈 배열)
      const pickupSnapshot = await officeRef.collection("pickup_drivers").get();
      const pickupTokens: string[] = pickupSnapshot.docs
        .filter(d => {
          const data = d.data();
          if (data.authUid === senderId || d.id === senderId) return false;
          return typeof data.fcmToken === "string" && (data.fcmToken as string).length > 0;
        })
        .map(d => d.data().fcmToken as string);

      const allTokens = [...managerTokens, ...designatedTokens, ...pickupTokens];
      const uniqueTokens = Array.from(new Set(allTokens));

      logger.info(
        `[onChatMessageCreated:${messageId}] tokens=${uniqueTokens.length} (mgr=${managerTokens.length}, des=${designatedTokens.length}, pck=${pickupTokens.length})`,
      );

      if (uniqueTokens.length === 0) {
        logger.info(`[onChatMessageCreated:${messageId}] 발송 대상 토큰 0건. 스킵`);
        return;
      }

      // 알림 미리보기: 본문 100자 cap
      const titleText = senderName;
      const bodyText = text.length > 100 ? text.substring(0, 100) + "…" : text;

      const multicast = buildMulticastFcmPayload(
        {
          data: {
            type: "NEW_CHAT_MESSAGE",
            messageId,
            senderId,
            senderName,
            senderRole,
            text,
            createdAt: String(createdAtMs),
            clientCreatedAt: String(clientCreatedAt),
            provinceId,
            cityId,
            officeId,
          },
          title: titleText,
          body: bodyText,
          level: "active",
          ttlSeconds: 86400, // 24시간
        },
        uniqueTokens,
      );

      const response = await admin.messaging().sendEachForMulticast(multicast);
      logger.info(
        `[onChatMessageCreated:${messageId}] sent: success=${response.successCount}, failure=${response.failureCount}`,
      );

      // 등록 해제 토큰 정리
      if (response.failureCount > 0) {
        const deadTokens: string[] = [];
        response.responses.forEach((r, idx) => {
          if (!r.success && r.error) {
            const code = (r.error as { code?: string }).code;
            if (code === "messaging/registration-token-not-registered") {
              deadTokens.push(uniqueTokens[idx]);
            }
          }
        });
        if (deadTokens.length > 0) {
          await cleanupDeadTokens(officeRef, deadTokens);
        }
      }
    } catch (e) {
      logger.error(`[onChatMessageCreated:${messageId}] 처리 실패`, e);
    }
  },
);

/**
 * 등록 해제된 토큰을 office의 토큰 컬렉션 3곳에서 제거
 */
async function cleanupDeadTokens(
  officeRef: FirebaseFirestore.DocumentReference,
  deadTokens: string[],
): Promise<void> {
  const collections = ["managerTokens", "designated_drivers", "pickup_drivers"];
  for (const colName of collections) {
    try {
      const snapshot = await officeRef.collection(colName).get();
      const batch = admin.firestore().batch();
      let count = 0;
      snapshot.forEach(doc => {
        const token = doc.data().fcmToken;
        if (typeof token === "string" && deadTokens.includes(token)) {
          batch.update(doc.ref, { fcmToken: FieldValue.delete() });
          count++;
        }
      });
      if (count > 0) {
        await batch.commit();
        logger.info(`[cleanupDeadTokens] ${colName}: ${count}건 fcmToken 제거`);
      }
    } catch (e) {
      logger.error(`[cleanupDeadTokens:${colName}] 실패`, e);
    }
  }
}

// ============================================================
//  트리거: 기사 가입/탈퇴 시 chat member 자동 동기화
// ============================================================

/**
 * 기사 문서 변화에 따른 chat member 동기화 헬퍼.
 *  - 생성 (create): authUid 기반 chat member 추가
 *  - 삭제 (delete): authUid 기반 chat member 제거
 *  - 업데이트: 무시 (멤버십 변화 없음)
 */
async function syncChatMemberOnDriverChange(
  event: any,
  role: ChatMemberRole,
): Promise<void> {
  const { provinceId, cityId, officeId } = event.params;
  if (!event.data) return;

  const before = event.data.before?.exists ? event.data.before.data() : null;
  const after = event.data.after?.exists ? event.data.after.data() : null;

  // 생성: before 없음 + after 있음
  if (!before && after) {
    const authUid = (after.authUid as string | undefined) || undefined;
    if (!authUid) {
      logger.warn(`[syncChatMemberOnDriverChange:${role}] authUid 없음 — chat member 등록 스킵`);
      return;
    }
    await addChatMember(provinceId, cityId, officeId, authUid, role);
    return;
  }

  // 삭제: before 있음 + after 없음
  if (before && !after) {
    const authUid = (before.authUid as string | undefined) || undefined;
    if (!authUid) return;
    await removeChatMember(provinceId, cityId, officeId, authUid);
    return;
  }

  // 업데이트는 멤버십 변화 없음. authUid 변경 시점은 가입 직후뿐이라 안전 (사후 변경 X)
}

/**
 * 대리기사 가입/탈퇴 시 chat member 동기화.
 *  PendingDriversViewModel에서 designated_drivers 문서가 생성되면 트리거 발화.
 */
export const onChatSyncDesignatedDriver = onDocumentWritten(
  {
    region: REGION,
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/designated_drivers/{driverId}",
  },
  async (event: any) => {
    await syncChatMemberOnDriverChange(event, "DESIGNATED_DRIVER");
  },
);

/**
 * 픽업기사 가입/탈퇴 시 chat member 동기화.
 *  픽업기사 승인 시 pickup_drivers 문서가 생성되면 트리거 발화.
 */
export const onChatSyncPickupDriver = onDocumentWritten(
  {
    region: REGION,
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/pickup_drivers/{driverId}",
  },
  async (event: any) => {
    await syncChatMemberOnDriverChange(event, "PICKUP_DRIVER");
  },
);

/**
 * 매니저 admins 문서 삭제 시 chat member 제거.
 *  생성은 registerOwner / approveOfficeApplication 흐름에서 명시적 호출.
 *  본 트리거는 삭제 케이스만 처리 (admins 컬렉션 트리거에서 associatedOfficeId 추출 후 멤버 제거).
 */
export const onChatSyncAdminRemoval = onDocumentWritten(
  {
    region: REGION,
    document: "admins/{adminId}",
  },
  async (event: any) => {
    if (!event.data) return;
    const before = event.data.before?.exists ? event.data.before.data() : null;
    const after = event.data.after?.exists ? event.data.after.data() : null;

    // 삭제 케이스만 처리
    if (before && !after) {
      const adminId: string = event.params.adminId;
      const provinceId = before.associatedProvinceId as string | undefined;
      const cityId = before.associatedCityId as string | undefined;
      const officeId = before.associatedOfficeId as string | undefined;
      if (!provinceId || !cityId || !officeId) {
        logger.warn(`[onChatSyncAdminRemoval:${adminId}] 사무실 정보 없음 — chat member 제거 스킵`);
        return;
      }
      await removeChatMember(provinceId, cityId, officeId, adminId);
    }
  },
);

// ============================================================
//  스케줄: 90일 PII cleanup
// ============================================================

/**
 * 채팅 메시지 90일 자동 삭제 — 매일 04:00 KST 실행.
 *  collectionGroup("messages") + path 필터로 chatRoom 메시지만 삭제.
 *  500건 batch로 일괄 처리.
 */
export const scheduledChatMessageCleanup = onSchedule(
  {
    schedule: "every day 04:00",
    timeZone: "Asia/Seoul",
    region: REGION,
  },
  async () => {
    const cutoffMs = Date.now() - 90 * 24 * 60 * 60 * 1000;
    const cutoff = Timestamp.fromMillis(cutoffMs);
    logger.info(`[scheduledChatMessageCleanup] 시작. cutoff=${new Date(cutoffMs).toISOString()}`);

    let totalDeleted = 0;
    const BATCH_SIZE = 500;

    try {
      let cursor: FirebaseFirestore.QueryDocumentSnapshot | null = null;
      let safetyCounter = 0; // 무한 루프 방지

      while (safetyCounter < 1000) { // 최대 50만건 / 회 (현실적 충분)
        safetyCounter++;
        let q: FirebaseFirestore.Query = admin.firestore()
          .collectionGroup("messages")
          .where("createdAt", "<", cutoff)
          .orderBy("createdAt", "asc")
          .limit(BATCH_SIZE);

        if (cursor) q = q.startAfter(cursor);

        const snapshot = await q.get();
        if (snapshot.empty) break;

        // chatRoom 메시지만 필터 (다른 messages 컬렉션 보호)
        const chatMessages = snapshot.docs.filter(d => d.ref.path.includes("/chatRoom/main/messages/"));

        if (chatMessages.length > 0) {
          const batch = admin.firestore().batch();
          chatMessages.forEach(d => batch.delete(d.ref));
          await batch.commit();
          totalDeleted += chatMessages.length;
        }

        cursor = snapshot.docs[snapshot.docs.length - 1];
        if (snapshot.size < BATCH_SIZE) break;
      }

      logger.info(`[scheduledChatMessageCleanup] 완료. 삭제 ${totalDeleted}건`);
    } catch (e) {
      logger.error(`[scheduledChatMessageCleanup] 실패. 삭제 진행 ${totalDeleted}건`, e);
    }
  },
);

// ============================================================
//  Callable: backfillChatMembers (운영 1회 호출)
// ============================================================

interface BackfillRequest {
  provinceId?: string;
  cityId?: string;
  officeId?: string;
}

/**
 * 기존 사무실 멤버를 chat members에 일괄 등록.
 *  매니저 admin이 firebase functions:shell 또는 Cloud Console에서 1회 호출.
 *  매개변수: { provinceId, cityId, officeId }
 *  검증: 호출자가 admins/{uid} 문서 보유 + 해당 사무실 매니저인지.
 *  멱등성: 이미 멤버이면 set merge로 안전하게 재등록.
 */
export const backfillChatMembers = onCall(
  {
    region: REGION,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "인증이 필요합니다.");
    }

    const callerUid = request.auth.uid;
    const adminDoc = await admin.firestore().doc(`admins/${callerUid}`).get();
    if (!adminDoc.exists) {
      throw new HttpsError("permission-denied", "관리자만 호출 가능합니다.");
    }

    const data = (request.data ?? {}) as BackfillRequest;
    const { provinceId, cityId, officeId } = data;
    if (!provinceId || !cityId || !officeId) {
      throw new HttpsError("invalid-argument", "provinceId, cityId, officeId 필수");
    }

    const adminData = adminDoc.data() ?? {};
    if (
      adminData.associatedProvinceId !== provinceId ||
      adminData.associatedCityId !== cityId ||
      adminData.associatedOfficeId !== officeId
    ) {
      throw new HttpsError("permission-denied", "해당 사무실 관리자가 아닙니다.");
    }

    logger.info(`[backfillChatMembers] 시작 by ${callerUid} @ ${officeId}`);

    const officeRef = admin.firestore()
      .doc(`provinces/${provinceId}/cities/${cityId}/offices/${officeId}`);

    const registered = { managers: 0, designated: 0, pickup: 0 };

    // 1. 매니저 — admins 컬렉션에서 office 일치
    const adminsSnapshot = await admin.firestore().collection("admins")
      .where("associatedProvinceId", "==", provinceId)
      .where("associatedCityId", "==", cityId)
      .where("associatedOfficeId", "==", officeId)
      .get();
    for (const doc of adminsSnapshot.docs) {
      await addChatMember(provinceId, cityId, officeId, doc.id, "MANAGER");
      registered.managers++;
    }

    // 2. 대리기사 — designated_drivers, authUid 필드 보유
    const designatedSnapshot = await officeRef.collection("designated_drivers").get();
    for (const doc of designatedSnapshot.docs) {
      const dData = doc.data();
      const authUid = dData.authUid as string | undefined;
      if (authUid) {
        await addChatMember(provinceId, cityId, officeId, authUid, "DESIGNATED_DRIVER");
        registered.designated++;
      }
    }

    // 3. 픽업기사 — pickup_drivers
    const pickupSnapshot = await officeRef.collection("pickup_drivers").get();
    for (const doc of pickupSnapshot.docs) {
      const dData = doc.data();
      const authUid = dData.authUid as string | undefined;
      if (authUid) {
        await addChatMember(provinceId, cityId, officeId, authUid, "PICKUP_DRIVER");
        registered.pickup++;
      }
    }

    logger.info(`[backfillChatMembers] 완료`, registered);
    return { success: true, registered };
  },
);
