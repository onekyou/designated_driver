/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 * import {onDocumentWritten} from "firebase-functions/v2/firestore";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

import { onDocumentWritten, onDocumentUpdated, onDocumentCreated } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import * as logger from "firebase-functions/logger";

// Firebase Admin SDK 초기화
admin.initializeApp();

// DEBUG: Enable Firestore detailed logging to trace writes (remove in production)
admin.firestore().settings({ logging: true });

// --------------------- Helper: point_transactions 경로 ---------------------
const pointTxCollection = (regionId: string, officeId: string) =>
  admin.firestore()
    .collection("regions").doc(regionId)
    .collection("offices").doc(officeId)
    .collection("point_transactions");

// 데이터 구조를 명확히 하기 위한 인터페이스 정의
interface CallData {
    assignedDriverId?: string;
    status?: string;
    // 여기에 필요한 다른 필드들을 추가할 수 있습니다.
}

const DRIVER_COLLECTION_NAME = "designated_drivers";

export const oncallassigned = onDocumentWritten(
    {
        region: "asia-northeast3",
        document: "regions/{regionId}/offices/{officeId}/calls/{callId}"
    },
    async (event: any) => {
        const {regionId, officeId, callId} = event.params;

        // 1. 이벤트 데이터와 변경 후 데이터 존재 여부 확인 (가장 안전한 방법)
        if (!event.data || !event.data.after) {
            logger.info(`[${callId}] 이벤트 데이터가 없어 함수를 종료합니다.`);
            return;
        }

        const afterData = event.data.after.data() as CallData;

        // 문서가 삭제된 경우
        if (!event.data.after.exists) {
            logger.info(`[${callId}] 문서가 삭제되어 함수를 종료합니다.`);
            return;
        }

        const beforeData = event.data.before?.data() as CallData | undefined;

        // 2. 배차됨 조건 확인
        const driverAssignedChanged = afterData.assignedDriverId &&
            afterData.assignedDriverId !== beforeData?.assignedDriverId;

        const statusBecameAssigned = afterData.status === "ASSIGNED" && beforeData?.status !== "ASSIGNED";

        if (!(driverAssignedChanged || statusBecameAssigned) || !afterData.assignedDriverId) {
            logger.info(`[${callId}] 기사 배정 이벤트 아님(변경 없음). 알림을 보내지 않습니다.`);
            return;
        }

        const driverId = afterData.assignedDriverId;
        logger.info(`[${callId}] 기사 [${driverId}]에게 알림 전송 시작.`);

        try {
            // 3. 기사 문서에서 FCM 토큰 가져오기 (authUid 로 검색)
            const driverCol = admin.firestore()
                .collection("regions").doc(regionId)
                .collection("offices").doc(officeId)
                .collection(DRIVER_COLLECTION_NAME);

            // 먼저 문서 ID == authUid 인 경우 시도 (이전 스키마 호환)
            let driverDocSnap = await driverCol.doc(driverId).get();

            if (!driverDocSnap.exists) {
                // 문서 ID가 다른 경우, authUid 필드로 검색 (권장 스키마)
                const qSnap = await driverCol.where("authUid", "==", driverId).limit(1).get();
                if (!qSnap.empty) {
                    driverDocSnap = qSnap.docs[0];
                }
            }

            if (!driverDocSnap.exists) {
                logger.error(`[${callId}] 기사 문서를 찾을 수 없습니다. driverId/authUid=${driverId}`);
                return;
            }

            const fcmToken = driverDocSnap.data()?.fcmToken;
            if (!fcmToken) {
                logger.warn(`[${callId}] 기사 [${driverId}]의 FCM 토큰이 없습니다.`);
                return;
            }

            // 4. 알림 페이로드 구성 및 전송
            const payload: admin.messaging.Message = {
                token: fcmToken,
                android: {
                    priority: "high"
                },
                data: {
                    title: "새로운 콜 배정",
                    body: "새로운 콜이 배정되었습니다. 앱을 확인해주세요.",
                    callId: callId,
                    type: "NEW_CALL_ASSIGNED"
                },
                notification: {
                    title: "새로운 콜 배정",
                    body: "새로운 콜이 배정되었습니다. 앱을 확인해주세요."
                }
            };

            await admin.messaging().send(payload);
            logger.info(`[${callId}] 기사 [${driverId}]에게 성공적으로 알림을 보냈습니다.`);

        } catch (error) {
            logger.error(`[${callId}] 알림 전송 중 오류 발생:`, error);
        }
    }
);

// =============================
// 공유 콜이 OPEN -> CLAIMED 으로 변경될 때 트리거
// 1) 대상 사무실 calls 컬렉션에 복사
// 2) 포인트 +/- (10% 기본, 추후 환경변수화)
// 3) FCM 알림 양쪽 매니저에게 전송 (토큰 로직은 미구현 -> TODO)
// =============================
interface SharedCallData {
  status: string;
  departure?: string;
  destination?: string;
  fare?: number;
  sourceRegionId: string;
  sourceOfficeId: string;
  targetRegionId: string;
  claimedOfficeId?: string;
  createdBy: string;
  claimedAt?: FirebaseFirestore.FieldValue | string;
  claimedDriverId?: string;
  phoneNumber?: string; // 새로 추가: 공유 콜 상세정보 전달용
}

export const onSharedCallClaimed = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "shared_calls/{callId}"
  },
  async (event: any) => {
    const callId = event.params.callId;

    if (!event.data) {
      logger.warn(`[shared:${callId}] 이벤트 데이터가 없습니다.`);
      return;
    }

    const beforeData = event.data.before.data() as SharedCallData | undefined;
    const afterData = event.data.after.data() as SharedCallData;

    if (!beforeData || !afterData) {
      logger.warn(`[shared:${callId}] before/after 데이터 누락`);
      return;
    }

    // OPEN -> CLAIMED 인지 확인
    if (beforeData.status === "OPEN" && afterData.status === "CLAIMED") {
      logger.info(`[shared:${callId}] 공유 콜이 CLAIMED 되었습니다. 대상사무실로 복사 시작.`);
      logger.info(`[shared:${callId}] afterData.claimedDriverId=${afterData.claimedDriverId}`);

      try {
        await admin.firestore().runTransaction(async (tx) => {
          // 레퍼런스 정의 ----------------------------------
          const destCallsRef = admin
            .firestore()
            .collection("regions")
            .doc(afterData.targetRegionId)
            .collection("offices")
            .doc(afterData.claimedOfficeId!)
            .collection("calls")
            .doc(callId);

          const driverSnap = afterData.claimedDriverId ? await tx.get(admin.firestore()
            .collection("regions").doc(afterData.targetRegionId)
            .collection("offices").doc(afterData.claimedOfficeId!)
            .collection("designated_drivers").doc(afterData.claimedDriverId)) : null;

          const driverData = driverSnap ? driverSnap.data() : undefined;
          const assignedDriverId = afterData.claimedDriverId || null;
          const assignedDriverName = driverData ? driverData.name : null;
          const assignedDriverPhone = driverData ? driverData.phoneNumber : null;

          //  WRITE ----------------------------------------
          const callDoc: any = {
            ...afterData,
            status: assignedDriverId ? "ASSIGNED" : "WAITING",
            departure_set: afterData.departure ?? "",
            destination_set: afterData.destination ?? "",
            fare_set: afterData.fare ?? 0,
            assignedTimestamp: admin.firestore.FieldValue.serverTimestamp(),
            callType: "SHARED",
            sourceSharedCallId: callId,
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
            timestamp: admin.firestore.FieldValue.serverTimestamp(),
            lastUpdate: admin.firestore.FieldValue.serverTimestamp(),
          };
          if (assignedDriverId) {
            callDoc.assignedDriverId = assignedDriverId;
            if (assignedDriverName) callDoc.assignedDriverName = assignedDriverName;
            if (assignedDriverPhone) callDoc.assignedDriverPhone = assignedDriverPhone;
          }
          tx.set(destCallsRef, callDoc);

          // 드라이버 상태 ASSIGNED 로 업데이트 (옵션)
          if (assignedDriverId && driverSnap?.exists) {
            tx.update(driverSnap.ref, { status: "배차중" });
          }
        });

        logger.info(`[shared:${callId}] 콜 복사 완료.`);

        // ---- 공유콜 문서 processed 플래그 업데이트 (트랜잭션 외부) ----
        try {
          await event.data.after.ref.update({ processed: true, lastUpdate: admin.firestore.FieldValue.serverTimestamp() });
          logger.debug(`[shared:${callId}] shared_calls 문서 processed 플래그 업데이트 완료.`);
        } catch (updateErr) {
          logger.error(`[shared:${callId}] processed 플래그 업데이트 실패`, updateErr);
        }

        // ---- FCM 알림 전송 ----
        try {
          const adminColl = admin.firestore().collection("admins");

          // 원본 사무실 관리자 토큰
          const srcSnap = await adminColl
            .where("associatedRegionId", "==", afterData.sourceRegionId)
            .where("associatedOfficeId", "==", afterData.sourceOfficeId)
            .get();

          // 수락 사무실 관리자 토큰
          const tgtSnap = await adminColl
            .where("associatedRegionId", "==", afterData.targetRegionId)
            .where("associatedOfficeId", "==", afterData.claimedOfficeId)
            .get();

          const tokens: string[] = [];
          srcSnap.forEach((doc) => {
            const t = doc.data().fcmToken;
            if (t) tokens.push(t);
          });
          tgtSnap.forEach((doc) => {
            const t = doc.data().fcmToken;
            if (t) tokens.push(t);
          });

          if (tokens.length > 0) {
            const msg: admin.messaging.MulticastMessage = {
              notification: {
                title: "공유 콜 수락됨",
                body: `${afterData.departure ?? "출발"} → ${afterData.destination ?? "도착"} / 요금 ${afterData.fare ?? 0}원`,
              },
              data: {
                sharedCallId: callId,
                type: "SHARED_CALL_CLAIMED",
              },
              tokens,
            };

            const resp = await admin.messaging().sendEachForMulticast(msg);
            logger.info(`[shared:${callId}] FCM sendEachForMulticast done. Success: ${resp.successCount}, Failure: ${resp.failureCount}`);
          } else {
            logger.info(`[shared:${callId}] 알림을 보낼 토큰이 없습니다.`);
          }
        } catch (fcmErr) {
          logger.error(`[shared:${callId}] FCM 전송 오류`, fcmErr);
        }

      } catch (err) {
        logger.error(`[shared:${callId}] 트랜잭션 오류`, err);
      }
    }
  }
);

// 신규 콜이 생성될 때 (status == WAITING && assignedDriverId == null)
export const sendNewCallNotification = onDocumentCreated(
  {
    region: "asia-northeast3",
    document: "regions/{regionId}/offices/{officeId}/calls/{callId}",
  },
  async (event: any) => {
    logger.info(`[sendNewCallNotification:${event.params.callId}] START - New call received.`);

    const data = event.data?.data();
    if (!data) {
      logger.warn("[sendNewCallNotification] No data in document.");
      return;
    }

    if (data.status !== "WAITING") {
      logger.info("[sendNewCallNotification] Call is not in WAITING status. Skip.");
      return;
    }

    // 1) 관리자 FCM 토큰 조회
    const adminQuery = await admin
      .firestore()
      .collection("admins")
      .where("associatedRegionId", "==", event.params.regionId)
      .where("associatedOfficeId", "==", event.params.officeId)
      .get();

    const tokens = adminQuery.docs
      .map((d) => d.data().fcmToken as string | undefined)
      .filter((t): t is string => !!t && t.length > 0);

    logger.info(`[getAdminTokens] SUCCESS: Found ${adminQuery.size} admins, ${tokens.length} valid tokens.`);

    if (tokens.length === 0) {
      logger.warn("[sendNewCallNotification] No valid admin FCM tokens, abort.");
      return;
    }

    // 2) 알림 + 데이터 메시지 전송
    await admin.messaging().sendEachForMulticast({
      notification: {
        title: "새로운 콜이 접수되었습니다.",
      },
      data: {
        type: "NEW_CALL_WAITING",
        callId: event.params.callId,
        customerPhone: data.phoneNumber || "",
      },
      android: {
        priority: "high",
        notification: {
          sound: "default",
          channelId: "new_call_fcm_channel_v2",
        },
      },
      tokens,
    });

    logger.info("[sendNewCallNotification] sendEachForMulticast with notification sent.");
  }
);

// =============================
// SHARED 콜 COMPLETED 시 포인트 이동
// =============================
interface CallDocData {
  status: string;
  callType?: string;
  fare?: number;
  fare_set?: number;
  sourceOfficeId?: string;
  sourceRegionId?: string;
  departure?: string;
  departure_set?: string;
  destination?: string;
  destination_set?: string;
  waypoints_set?: string;
  customerName?: string;
}

export const onSharedCallCompleted = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "regions/{regionId}/offices/{officeId}/calls/{callId}"
  },
  async (event: any) => {
    const { regionId, officeId, callId } = event.params;

    if (!event.data) return;

    const beforeData = event.data.before.data() as CallDocData | undefined;
    const afterData = event.data.after.data() as CallDocData;

    if (!beforeData || !afterData) return;

    // 조건: SHARED 콜 & 상태 COMPLETED 로 전환
    if (afterData.callType !== "SHARED") return;
    const completedStatuses = ["COMPLETED", "AWAITING_SETTLEMENT"];
    const becameEligible = !completedStatuses.includes(beforeData?.status || "") && completedStatuses.includes(afterData.status || "");
    if (!becameEligible) return;

    const fare = afterData.fare_set ?? afterData.fare ?? 0;
    const amount = Math.round(fare * getCommissionRate());
    if (amount === 0) return;

    const sourceOfficeId = afterData.sourceOfficeId;
    const sourceRegionId = afterData.sourceRegionId;
    if (!sourceOfficeId || !sourceRegionId) {
      logger.error(`[shared-completed:${callId}] sourceOfficeId/RegionId 누락`);
      return;
    }

    try {
      await admin.firestore().runTransaction(async (tx) => {
        const handlerPointsRef = admin
          .firestore()
          .collection("regions").doc(regionId)
          .collection("offices").doc(officeId)
          .collection("points").doc("points");

        const sourcePointsRef = admin
          .firestore()
          .collection("regions").doc(sourceRegionId)
          .collection("offices").doc(sourceOfficeId)
          .collection("points").doc("points");

        const [handlerSnap, sourceSnap] = await Promise.all([
          tx.get(handlerPointsRef),
          tx.get(sourcePointsRef)
        ]);

        const handlerBalance = (handlerSnap.data()?.balance || 0) - amount;
        const sourceBalance = (sourceSnap.data()?.balance || 0) + amount;

        tx.set(handlerPointsRef, { balance: handlerBalance, updatedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
        tx.set(sourcePointsRef, { balance: sourceBalance, updatedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });

        // 거래 기록 (오피스별 하위 point_transactions)
        const txRef = pointTxCollection(regionId, officeId).doc();
        tx.set(txRef, {
          amount,
          fromOfficeId: officeId,
          toOfficeId: sourceOfficeId,
          callId,
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
        });
      });

      logger.info(`[shared-completed:${callId}] 포인트 ${amount} 이동 완료.`);
    } catch (err) {
      logger.error(`[shared-completed:${callId}] 트랜잭션 오류`, err);
    }
  }
);

// =============================
// 콜이 COMPLETED 가 될 때 Settlement 문서 자동 생성
// =============================
interface CallDocData {
  status: string;
  fare?: number;
  fare_set?: number;
  paymentMethod?: string;
  cashReceived?: number;
  creditAmount?: number;
  assignedDriverId?: string;
  assignedDriverName?: string;
  officeId?: string;
  regionId?: string;
  completedAt?: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
  settlementStatus?: string;
  settlementId?: string;
  phoneNumber?: string; // 고객 전화번호 필드 추가
}

export const onCallCompleted = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "regions/{regionId}/offices/{officeId}/calls/{callId}"
  },
  async (event: any) => {
    const {regionId, officeId, callId} = event.params;
    if (!event.data) return;

    const beforeData = event.data.before.data() as CallDocData | undefined;
    const afterData = event.data.after.data() as CallDocData;

    if (!afterData) return;

    const completedStatuses = ["COMPLETED", "AWAITING_SETTLEMENT"];
    const becameCompleted = !completedStatuses.includes(beforeData?.status || "") && completedStatuses.includes(afterData.status || "");
    if (!becameCompleted) return;

    // 이미 settlementId 가 있으면 중복 생성 방지
    if (afterData.settlementId) {
      logger.info(`[${callId}] 이미 settlementId 존재 ${afterData.settlementId}. 함수 종료.`);
      return;
    }

    // ===== workDate 계산 (KST 13시 컷) =====
    const calcWorkDate = (dateObj: Date): string => {
      const kst = new Date(dateObj.getTime() + 9 * 60 * 60 * 1000); // UTC→KST
      if (kst.getUTCHours() < 13) {
        kst.setUTCDate(kst.getUTCDate() - 1);
      }
      const y = kst.getUTCFullYear();
      const m = String(kst.getUTCMonth() + 1).padStart(2, "0");
      const d = String(kst.getUTCDate()).padStart(2, "0");
      return `${y}-${m}-${d}`;
    };

    const completedAtDate = (() => {
      if (afterData.completedAt && (afterData.completedAt as any).toDate) {
        return (afterData.completedAt as any).toDate() as Date;
      }
      return new Date();
    })();
    const workDateStr = calcWorkDate(completedAtDate);

    try {
      await admin.firestore().runTransaction(async (tx) => {
        // 1) settlement 문서 만들기
        const settlementsCol = admin
          .firestore()
          .collection("regions").doc(regionId)
          .collection("offices").doc(officeId)
          .collection("settlements");

        const settlementRef = settlementsCol.doc();

        // 일부 필드는 이전 스키마에 없을 수 있으므로 any 캐스트 사용
        const fareFinalVal = (afterData as any).fareFinal ?? afterData.fare_set ?? afterData.fare ?? 0;
        const customerPhone = afterData.phoneNumber ?? (afterData as any).customerPhone ?? "";
        const fare = fareFinalVal; // maintain existing variable name
        const settlementDoc: any = {
          callId,
          driverId: afterData.assignedDriverId || null,
          driverName: afterData.assignedDriverName || null,
          customerName: afterData.customerName || null,
            customerPhone,
          departure: afterData.departure ?? afterData.departure_set ?? "",
          destination: afterData.destination ?? afterData.destination_set ?? "",
          waypoints: afterData.waypoints_set ?? "",
          fare,
            fareFinal: fareFinalVal,
          paymentMethod: afterData.paymentMethod || "",
          cashAmount: afterData.cashReceived ?? null,
          creditAmount: afterData.creditAmount ?? null,
            completedAt: afterData.completedAt ?? admin.firestore.FieldValue.serverTimestamp(),
          officeId,
          regionId,
          settlementStatus: "PENDING",
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          workDate: workDateStr,
          isFinalized: false,
          lastUpdate: admin.firestore.FieldValue.serverTimestamp(),
        };

        tx.set(settlementRef, settlementDoc);

        // 2) 콜 문서에 settlementId, settlementStatus 추가 (+sessionId)
        tx.update(event.data.after.ref, {
          settlementId: settlementRef.id,
          settlementStatus: "PENDING",
          isFinalized: false,
          workDate: workDateStr,
          lastUpdate: admin.firestore.FieldValue.serverTimestamp()
        });
      });

      logger.info(`[${callId}] Settlement 문서 생성 완료.`);
    } catch (err) {
      logger.error(`[${callId}] Settlement 생성 중 오류`, err);
    }
  }
);

// =============================
// settlement 문서 생성시 (외상) → credits 증가
// =============================
export const onSettlementCreated = onDocumentCreated(
  {
    region: "asia-northeast3",
    document: "regions/{regionId}/offices/{officeId}/settlements/{settlementId}"
  },
  async (event: any) => {
    const { regionId, officeId, settlementId } = event.params;
    if (!event.data) return;

    const data = event.data.data() as any;
    if (!data || data.paymentMethod !== "외상") return;

    const fare = data.fare ?? 0;
    if (fare <= 0) return;

    // 고객 식별 – 우선 customerName 사용, 특수문자 제거
    const rawKey: string = data.customerName || "unknown_customer";
    const customerKey = rawKey.replace(/[^a-zA-Z0-9_-]/g, "_").slice(0, 80);

    try {
      await admin.firestore().runTransaction(async (tx) => {
        const creditsRef = admin
          .firestore()
          .collection("regions").doc(regionId)
          .collection("offices").doc(officeId)
          .collection("credits").doc(customerKey);

        tx.set(
          creditsRef,
          {
            totalOwed: admin.firestore.FieldValue.increment(fare),
            lastUpdated: admin.firestore.FieldValue.serverTimestamp(),
          },
          { merge: true }
        );
      });
      logger.info(`[settlement-created:${settlementId}] 외상 ${fare}원 → credits 누적 완료 (${customerKey})`);
    } catch (err) {
      logger.error(`[settlement-created:${settlementId}] credits 증가 실패`, err);
    }
  }
);

// =============================
// settlementStatus PENDING -> SETTLED 시 포인트/credits 처리
// =============================
interface SettlementDoc {
  settlementStatus?: string;
  fare?: number;
  officeId?: string;
  regionId?: string;
  driverId?: string;
  driverName?: string;
  createdAt?: FirebaseFirestore.Timestamp;
  callType?: string;
  paymentMethod?: string;
  customerName?: string;
}

export const onSettlementUpdated = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "regions/{regionId}/offices/{officeId}/settlements/{settlementId}"
  },
  async (event: any) => {
    const {regionId, officeId, settlementId} = event.params;
    if (!event.data) return;
    const before = event.data.before.data() as SettlementDoc | undefined;
    const after  = event.data.after.data() as SettlementDoc;
    if (!after) return;

    const becameSettled = before?.settlementStatus !== "SETTLED" && after.settlementStatus === "SETTLED";
    if (!becameSettled) return;

    const fare = after.fare ?? 0;

    // ---- 외상 결제 credits 차감 ----
    if (after.paymentMethod === "외상" && fare > 0) {
      try {
        await admin.firestore().runTransaction(async (tx) => {
          const rawKey: string = after.customerName || "unknown_customer";
          const customerKey = rawKey.replace(/[^a-zA-Z0-9_-]/g, "_").slice(0, 80);
          const creditsRef = admin.firestore()
            .collection("regions").doc(regionId)
            .collection("offices").doc(officeId)
            .collection("credits").doc(customerKey);

          const creditSnap = await tx.get(creditsRef);
          const prevTotal = creditSnap.exists ? (creditSnap.data()?.totalOwed || 0) : 0;
          const newTotal = prevTotal - fare;

          if (newTotal <= 0) {
            tx.delete(creditsRef);
          } else {
            tx.set(creditsRef, {
              totalOwed: newTotal,
              lastUpdated: admin.firestore.FieldValue.serverTimestamp(),
            }, { merge: true });
          }
        });
        logger.info(`[settlement:${settlementId}] 외상 ${fare}원 회수 → credits 차감 완료`);
      } catch (err) {
        logger.error(`[settlement:${settlementId}] credits 차감 실패`, err);
      }
    }

    // NORMAL 콜은 포인트 적립 대상이 아님
    if (after.callType !== "SHARED") {
      logger.info(`[settlement:${settlementId}] callType=${after.callType} → 포인트 적립 스킵`);
      return;
    }

    // ----- 중복 포인트 적립 방지 -----
    const dupSnap = await pointTxCollection(regionId, officeId)
      .where("settlementId", "==", settlementId)
      .limit(1)
      .get();

    if (!dupSnap.empty) {
      logger.warn(`[settlement:${settlementId}] point transaction already exists → skip`);
      return;
    }

    const officeShareRatio = getCommissionRate();
    const officeEarn   = Math.round(fare * officeShareRatio);

    try {
      await admin.firestore().runTransaction(async (tx) => {
        // 1) 사무실 points 문서 balance 증가
        const pointsRef = admin.firestore()
          .collection("regions").doc(regionId)
          .collection("offices").doc(officeId)
          .collection("points").doc("points");

        tx.set(pointsRef, { balance: admin.firestore.FieldValue.increment(officeEarn) }, { merge: true });

        // 2) point_transactions 기록 (driverId 로 연결)
        const txRef = pointTxCollection(regionId, officeId).doc();
        tx.set(txRef, {
          type: "SETTLEMENT",
          officeId,
          regionId,
          driverId: after.driverId || null,
          amount: officeEarn,
          source: "settlement",
          settlementId,
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
        });
      });
      logger.info(`[settlement:${settlementId}] 포인트 ${officeEarn} 적립 완료.`);
    } catch (err) {
      logger.error(`[settlement:${settlementId}] 포인트 적립 실패`, err);
    }
  }
);

// =============================
// settlementStatus SETTLED -> CORRECTED 시 포인트 롤백
// =============================
export const onSettlementCorrected = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "regions/{regionId}/offices/{officeId}/settlements/{settlementId}"
  },
  async (event: any) => {
    const { regionId, officeId, settlementId } = event.params;
    if (!event.data) return;

    const before = event.data.before.data() as SettlementDoc | undefined;
    const after  = event.data.after.data() as SettlementDoc;
    if (!after || !before) return;

    const becameCorrected = before.settlementStatus === "SETTLED" && after.settlementStatus === "CORRECTED";
    if (!becameCorrected) return;

    // SHARED 콜만 롤백 대상
    if (before.callType !== "SHARED") {
      logger.info(`[settlement-corr:${settlementId}] callType=${before.callType} → 포인트 롤백 없음`);
      return;
    }

    // 이미 롤백 트랜잭션이 존재하는지 확인
    const dupSnap = await pointTxCollection(regionId, officeId)
      .where("settlementId", "==", settlementId)
      .where("type", "==", "CORRECTION_NEG")
      .limit(1)
      .get();

    if (!dupSnap.empty) {
      logger.warn(`[settlement-corr:${settlementId}] rollback already exists → skip`);
      return;
    }

    const fare = before.fare ?? 0;
    const officeEarn = Math.round(fare * getCommissionRate());

    try {
      await admin.firestore().runTransaction(async (tx) => {
        // 1) points.balance 감소
        const pointsRef = admin.firestore()
          .collection("regions").doc(regionId)
          .collection("offices").doc(officeId)
          .collection("points").doc("points");
        tx.set(pointsRef, { balance: admin.firestore.FieldValue.increment(-officeEarn) }, { merge: true });

        // 2) 음수 거래 기록
        const txRef = pointTxCollection(regionId, officeId).doc();
        tx.set(txRef, {
          type: "CORRECTION_NEG",
          officeId,
          regionId,
          driverId: after.driverId || null,
          amount: -officeEarn,
          source: "settlement_correction",
          settlementId,
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
        });
      });
      logger.info(`[settlement-corr:${settlementId}] 포인트 -${officeEarn} 롤백 완료.`);
    } catch (err) {
      logger.error(`[settlement-corr:${settlementId}] 포인트 롤백 실패`, err);
    }
  }
);

// ------------------ 공통 수수료율 유틸 ------------------
function getCommissionRate(): number {
  const envVar = process.env.COMMISSION_RATE;
  if (envVar) {
    const p = parseFloat(envVar);
    if (!isNaN(p)) return p;
  }
  // Firebase Functions config 값
  // (배포 전: firebase functions:config:set commission.rate="0.10")
  // eslint-disable-next-line @typescript-eslint/ban-ts-comment
  // @ts-ignore - functions.config() 는 런타임에서 제공
  const cfg = (typeof process !== "undefined" && require("firebase-functions").config?.() || {}) as any;
  const cfgRate = cfg?.commission?.rate;
  if (cfgRate) {
    const p = parseFloat(cfgRate);
    if (!isNaN(p)) return p;
  }
  return 0.10; // default
}

// --- 배포 강제용 더미 변수 (코드 변경 없을 때 강제 리빌드) ---
const _forceDeploy = Date.now();
void _forceDeploy;

// =============================
// 콜 문서 paymentMethod/cashReceived/creditAmount 가 변경될 때
// 기존 settlement 문서를 업데이트
// =============================
export const onCallPaymentMethodUpdated = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "regions/{regionId}/offices/{officeId}/calls/{callId}"
  },
  async (event: any) => {
    const { regionId, officeId, callId } = event.params;
    if (!event.data) return;

    const before = event.data.before.data() as any;
    const after  = event.data.after.data() as any;
    if (!after) return;

    // paymentMethod 변경 또는 cash/credit 필드 변경 여부 확인
    const paymentChanged = before?.paymentMethod !== after.paymentMethod;
    const cashChanged    = before?.cashReceived  !== after.cashReceived;
    const creditChanged  = before?.creditAmount   !== after.creditAmount;
    const fareChanged    = before?.fareFinal      !== after.fareFinal;

    if (!(paymentChanged || cashChanged || creditChanged || fareChanged)) return;

    const settlementId = after.settlementId;
    if (!settlementId) return; // settlement 문서가 아직 없으면 종료

    try {
      const settlementRef = admin.firestore()
        .collection("regions").doc(regionId)
        .collection("offices").doc(officeId)
        .collection("settlements").doc(settlementId);

      const updateData: any = { lastUpdate: admin.firestore.FieldValue.serverTimestamp() };
      if (paymentChanged) updateData.paymentMethod = after.paymentMethod || "";
      if (cashChanged)    updateData.cashAmount     = after.cashReceived ?? null;
      if (creditChanged)  updateData.creditAmount   = after.creditAmount ?? null;
      if (fareChanged) {
        updateData.fareFinal = after.fareFinal ?? null;
        updateData.fare      = after.fareFinal ?? after.fare ?? null;
      }

      await settlementRef.set(updateData, { merge: true });
      logger.info(`[call-payment-sync:${callId}] settlement ${settlementId} updated with payment info.`);
    } catch (err) {
      logger.error(`[call-payment-sync:${callId}] failed to update settlement`, err);
    }
  }
);

// finalizeDailySettlement 함수 제거 (클라이언트 배치로 대체)