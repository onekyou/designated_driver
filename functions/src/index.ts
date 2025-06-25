/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 * import {onDocumentWritten} from "firebase-functions/v2/firestore";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

import {onDocumentWritten, onDocumentUpdated} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import * as logger from "firebase-functions/logger";

// Firebase Admin SDK 초기화
admin.initializeApp();

// 데이터 구조를 명확히 하기 위한 인터페이스 정의
interface CallData {
    assignedDriverId?: string;
    // 여기에 필요한 다른 필드들을 추가할 수 있습니다.
}

const DRIVER_COLLECTION_NAME = "designated_drivers";

export const oncallassigned = onDocumentWritten(
    "regions/{regionId}/offices/{officeId}/calls/{callId}",
    async (event) => {
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

        // 2. assignedDriverId가 유효하게 할당/변경되었는지 확인
        const isDriverAssigned = afterData.assignedDriverId &&
            afterData.assignedDriverId !== beforeData?.assignedDriverId;

        if (!isDriverAssigned || !afterData.assignedDriverId) {
            logger.info(`[${callId}] 기사 배정 변경사항이 없어 알림을 보내지 않습니다.`);
            return;
        }

        const driverId = afterData.assignedDriverId;
        logger.info(`[${callId}] 기사 [${driverId}]에게 알림 전송 시작.`);

        try {
            // 3. 기사 문서에서 FCM 토큰 가져오기
            const driverRef = admin.firestore()
                .collection("regions").doc(regionId)
                .collection("offices").doc(officeId)
                .collection(DRIVER_COLLECTION_NAME).doc(driverId);
            
            const driverDoc = await driverRef.get();
            if (!driverDoc.exists) {
                logger.error(`[${callId}] 기사 문서 [${driverId}]를 찾을 수 없습니다.`);
                return;
            }

            const fcmToken = driverDoc.data()?.fcmToken;
            if (!fcmToken) {
                logger.warn(`[${callId}] 기사 [${driverId}]의 FCM 토큰이 없습니다.`);
                return;
            }

            // 4. 알림 페이로드 구성 및 전송
            const payload = {
                data: {
                    title: "새로운 콜 배정",
                    body: "새로운 콜이 배정되었습니다. 앱을 확인해주세요.",
                    callId: callId,
                    type: "NEW_CALL_ASSIGNED"
                },
                token: fcmToken,
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
}

export const onSharedCallClaimed = onDocumentUpdated(
  "shared_calls/{callId}",
  async (event) => {
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

      const fare = afterData.fare || 0;
      const pointRatio = 0.1; // 10%
      const pointAmount = Math.round(fare * pointRatio);

      try {
        await admin.firestore().runTransaction(async (tx) => {
          // 1) 대상 사무실 calls 컬렉션에 문서 생성
          const destCallsRef = admin
            .firestore()
            .collection("regions")
            .doc(afterData.targetRegionId)
            .collection("offices")
            .doc(afterData.claimedOfficeId!)
            .collection("calls")
            .doc(callId);

          tx.set(destCallsRef, {
            // 원본 필드 전부 복사 + 초기 상태 WAITING 으로 지정
            ...afterData,
            status: "WAITING",
            sourceSharedCallId: callId,
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
          });

          // 2) 포인트 증감
          //   소스 사무실 points 증가, 클레임 사무실 points 감소
          const sourcePointsRef = admin
            .firestore()
            .collection("regions")
            .doc(afterData.sourceRegionId)
            .collection("offices")
            .doc(afterData.sourceOfficeId)
            .collection("points")
            .doc("points");

          const targetPointsRef = admin
            .firestore()
            .collection("regions")
            .doc(afterData.targetRegionId)
            .collection("offices")
            .doc(afterData.claimedOfficeId!)
            .collection("points")
            .doc("points");

          // 읽어온 후 없으면 0 으로 초기화
          const sourceSnap = await tx.get(sourcePointsRef);
          const targetSnap = await tx.get(targetPointsRef);

          const sourceBalance = (sourceSnap.data()?.balance || 0) + pointAmount;
          const targetBalance = (targetSnap.data()?.balance || 0) - pointAmount;

          tx.set(sourcePointsRef, {
            balance: sourceBalance,
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          }, {merge:true});

          tx.set(targetPointsRef, {
            balance: targetBalance,
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          }, {merge:true});

          // 3) 공유 콜 문서 상태 IN_PROGRESS 로 업데이트 (복사 완료 체크)
          tx.update(event.data!.after.ref, {
            status: "CLAIMED", // 유지
            processed: true,
          });
        });

        logger.info(`[shared:${callId}] 콜 복사 및 포인트 처리 완료.`);
        // TODO: FCM 알림 로직 구현

      } catch (err) {
        logger.error(`[shared:${callId}] 트랜잭션 오류`, err);
      }
    }
  }
);