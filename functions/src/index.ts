/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 * import {onDocumentWritten} from "firebase-functions/v2/firestore";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

import {onDocumentWritten, onDocumentUpdated, onDocumentCreated} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import * as logger from "firebase-functions/logger";

// Firebase Admin SDK 초기화
admin.initializeApp();

// 데이터 구조를 명확히 하기 위한 인터페이스 정의
interface CallData {
    assignedDriverId?: string;
    callType?: string;
    sourceSharedCallId?: string;
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

        // 2. assignedDriverId가 유효하게 할당/변경되었는지 확인
        // 공유콜의 경우 새 문서 생성 시에는 알림을 보내지 않음 (중복 알림 방지)
        const isSharedCall = afterData.callType === "SHARED";
        const isNewDocument = !beforeData;
        const isDriverChanged = beforeData && afterData.assignedDriverId !== beforeData.assignedDriverId;
        
        logger.info(`[${callId}] 알림 조건 확인: callType=${afterData.callType}, isSharedCall=${isSharedCall}, isNewDocument=${isNewDocument}, isDriverChanged=${isDriverChanged}, assignedDriverId=${afterData.assignedDriverId}, sourceSharedCallId=${afterData.sourceSharedCallId}`);
        
        // 공유콜이면서 새 문서인 경우 상세 로그
        if (isSharedCall && isNewDocument) {
            logger.info(`[${callId}] 공유콜 새 문서 생성 감지. 기사 배정 시 알림 전송됩니다.`);
        }
        
        const isDriverAssigned = afterData.assignedDriverId && (
            isNewDocument || // 새 문서 생성 시 (공유콜 포함)
            isDriverChanged // 기존 문서의 기사 변경 시
        );

        if (!isDriverAssigned || !afterData.assignedDriverId) {
            logger.info(`[${callId}] 기사 배정 변경사항이 없어 알림을 보내지 않습니다. assignedDriverId: ${afterData.assignedDriverId}, beforeAssignedDriverId: ${beforeData?.assignedDriverId}, isSharedCall: ${isSharedCall}, isNewDocument: ${isNewDocument}, isDriverAssigned: ${isDriverAssigned}`);
            return;
        }

        const driverId = afterData.assignedDriverId;
        logger.info(`[${callId}] 기사 [${driverId}]에게 알림 전송 시작. isNewDocument=${isNewDocument}, isDriverChanged=${isDriverChanged}`);

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

            // 4. 알림 페이로드 구성 및 전송 (고우선순위 설정)
            // notification 필드 제거 - 앱에서 커스텀 알림 처리
            const payload = {
                data: {
                    callId: callId,
                    type: "call_assigned",
                    title: "🚨 새로운 콜 배정",
                    body: "새로운 콜이 배정되었습니다. 즉시 확인해주세요!"
                },
                android: {
                    priority: "high" as const,
                    ttl: 30000, // 30초 TTL
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
  phoneNumber?: string; // 새로 추가: 공유 콜 상세정보 전달용
  completedAt?: FirebaseFirestore.FieldValue | string; // 완료 시각
  destCallId?: string; // 복사된 콜의 ID
  cancelledByDriver?: boolean; // 기사가 취소했는지 여부
  originalCallId?: string; // 원본 콜 ID
  cancelReason?: string; // 취소 사유
  cancelledAt?: FirebaseFirestore.FieldValue | string; // 취소 시각
}

// =============================
// 새로운 공유 콜이 생성될 때 트리거
// 대상 지역의 모든 사무실 관리자에게 FCM 알림 전송
// =============================
export const onSharedCallCreated = onDocumentCreated(
  {
    region: "asia-northeast3",
    document: "shared_calls/{callId}"
  },
  async (event: any) => {
    const callId = event.params.callId;
    
    if (!event.data) {
      logger.warn(`[shared-created:${callId}] 이벤트 데이터가 없습니다.`);
      return;
    }

    const sharedCallData = event.data.data() as SharedCallData;
    
    if (!sharedCallData || sharedCallData.status !== "OPEN") {
      logger.info(`[shared-created:${callId}] OPEN 상태가 아니므로 알림을 보내지 않습니다. Status: ${sharedCallData?.status}`);
      return;
    }

    logger.info(`[shared-created:${callId}] 새로운 공유콜 생성됨. 대상 지역 관리자들에게 알림 전송 시작.`);
    logger.info(`[shared-created:${callId}] 공유콜 데이터: sourceRegionId=${sharedCallData.sourceRegionId}, sourceOfficeId=${sharedCallData.sourceOfficeId}, targetRegionId=${sharedCallData.targetRegionId}`);

    try {
      // 대상 지역의 모든 관리자 FCM 토큰 조회 (원본 사무실 제외)
      const adminQuery = await admin
        .firestore()
        .collection("admins")
        .where("associatedRegionId", "==", sharedCallData.targetRegionId)
        .get();

      const tokens: string[] = [];
      adminQuery.docs.forEach((doc) => {
        const adminData = doc.data();
        logger.info(`[shared-created:${callId}] 관리자 확인: regionId=${adminData.associatedRegionId}, officeId=${adminData.associatedOfficeId}, sourceOfficeId=${sharedCallData.sourceOfficeId}`);
        
        // 원본 사무실은 제외 (sourceOfficeId와 동일한 사무실 제외)
        if (adminData.associatedOfficeId === sharedCallData.sourceOfficeId) {
          logger.info(`[shared-created:${callId}] ⛔ 원본 사무실 제외: ${adminData.associatedOfficeId} (sourceOfficeId: ${sharedCallData.sourceOfficeId})`);
          return; // 다음 관리자로 넘어감
        }
        
        logger.info(`[shared-created:${callId}] ✅ 원본 사무실이 아님: ${adminData.associatedOfficeId} ≠ ${sharedCallData.sourceOfficeId}`);
        
        if (adminData.fcmToken) {
          // 중복 토큰 방지
          if (!tokens.includes(adminData.fcmToken)) {
            tokens.push(adminData.fcmToken);
            logger.info(`[shared-created:${callId}] 📤 알림 대상 추가: ${adminData.associatedOfficeId}`);
          } else {
            logger.info(`[shared-created:${callId}] 🔄 중복 토큰 제외: ${adminData.associatedOfficeId}`);
          }
        } else {
          logger.warn(`[shared-created:${callId}] ⚠️ FCM 토큰 없음: ${adminData.associatedOfficeId}`);
        }
      });

      logger.info(`[shared-created:${callId}] 알림 대상: ${tokens.length}명의 관리자`);

      if (tokens.length === 0) {
        logger.warn(`[shared-created:${callId}] 알림을 보낼 관리자 토큰이 없습니다.`);
        return;
      }

      // FCM 알림 전송
      const message: admin.messaging.MulticastMessage = {
        notification: {
          title: "새로운 공유콜이 도착했습니다!",
          body: `${sharedCallData.departure || "출발지"} → ${sharedCallData.destination || "도착지"} / ${sharedCallData.fare || 0}원`,
        },
        data: {
          type: "NEW_SHARED_CALL",
          sharedCallId: callId,
          departure: sharedCallData.departure || "",
          destination: sharedCallData.destination || "",
          fare: (sharedCallData.fare || 0).toString(),
          click_action: "ACTION_SHOW_SHARED_CALL",
        },
        android: {
          priority: "high",
          notification: {
            sound: "default",
            channelId: "shared_call_fcm_channel",
            priority: "high",
            clickAction: "ACTION_SHOW_SHARED_CALL", // click_action → clickAction
          },
        },
        tokens,
      };

      const response = await admin.messaging().sendEachForMulticast(message);
      logger.info(`[shared-created:${callId}] FCM 알림 전송 완료. 성공: ${response.successCount}, 실패: ${response.failureCount}`);

      // 실패한 토큰들 로그
      response.responses.forEach((resp, idx) => {
        if (!resp.success) {
          logger.warn(`[shared-created:${callId}] 토큰 ${idx} 전송 실패: ${resp.error?.message}`);
        }
      });

    } catch (error) {
      logger.error(`[shared-created:${callId}] 알림 전송 중 오류 발생:`, error);
    }
  }
);

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

    // CLAIMED -> OPEN 인지 확인 (기사가 공유콜 취소)
    if (beforeData.status === "CLAIMED" && afterData.status === "OPEN" && afterData.cancelledByDriver) {
      logger.info(`[shared:${callId}] 공유 콜이 기사에 의해 취소되었습니다. 원본 사무실 콜 복구 시작.`);
      
      try {
        await admin.firestore().runTransaction(async (tx) => {
          // 원본 사무실의 콜을 WAITING 상태로 복구
          const originalCallRef = admin
            .firestore()
            .collection("regions")
            .doc(afterData.sourceRegionId)
            .collection("offices")
            .doc(afterData.sourceOfficeId)
            .collection("calls")
            .doc(callId);
          
          tx.update(originalCallRef, {
            status: "HOLD",
            callType: null,
            sourceSharedCallId: null,
            assignedDriverId: null,
            assignedDriverName: null,
            assignedDriverPhone: null,
            cancelReason: `공유콜 취소됨: ${afterData.cancelReason || "사유 없음"}`,
            departure_set: null,
            destination_set: null,
            fare_set: null,
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
          });
          
          logger.info(`[shared:${callId}] 원본 사무실 콜이 HOLD 상태로 복구되었습니다.`);
        });
        
        // 원본 사무실 관리자들에게 알림 전송
        const adminQuery = await admin
          .firestore()
          .collection("admins")
          .where("associatedRegionId", "==", afterData.sourceRegionId)
          .where("associatedOfficeId", "==", afterData.sourceOfficeId)
          .get();
        
        const tokens: string[] = [];
        adminQuery.docs.forEach((doc) => {
          const adminData = doc.data();
          if (adminData.fcmToken) {
            tokens.push(adminData.fcmToken);
          }
        });
        
        if (tokens.length > 0) {
          const message: admin.messaging.MulticastMessage = {
            notification: {
              title: "공유콜이 취소되었습니다",
              body: `${afterData.cancelReason || "사유 없음"} - 콜이 대기 상태로 복구되었습니다.`,
            },
            data: {
              type: "SHARED_CALL_CANCELLED",
              callId: callId,
              cancelReason: afterData.cancelReason || "",
            },
            android: {
              priority: "high",
              notification: {
                sound: "default",
                channelId: "call_manager_fcm_channel",
                priority: "high",
              },
            },
            tokens,
          };
          
          const response = await admin.messaging().sendEachForMulticast(message);
          logger.info(`[shared:${callId}] 원본 사무실에 취소 알림 전송 완료. 성공: ${response.successCount}`);
        }
      } catch (error) {
        logger.error(`[shared:${callId}] 원본 콜 복구 중 오류:`, error);
      }
      return;
    }
    
    // OPEN -> CLAIMED 인지 확인 (콜 복사만, 포인트 처리 없음)
    if (beforeData.status === "OPEN" && afterData.status === "CLAIMED") {
      logger.info(`[shared:${callId}] 공유 콜이 CLAIMED 되었습니다. 대상사무실로 복사 시작.`);
      logger.info(`[shared:${callId}] afterData.claimedDriverId=${afterData.claimedDriverId}`);

      logger.info(`[shared:${callId}] assignedDriverId=${afterData.claimedDriverId}`);

      // 트랜잭션 외부에서 변수 선언
      let assignedDriverId: string | null = null;
      let assignedDriverName: string | null = null;
      let assignedDriverPhone: string | null = null;
      let driverSnap: any = null;

      try {
        await admin.firestore().runTransaction(async (tx) => {
          // ========== 모든 읽기 작업을 먼저 수행 ==========
          
          // 1. 기사 정보 읽기 (배정된 기사가 있을 경우)
          driverSnap = afterData.claimedDriverId ? await tx.get(admin.firestore()
            .collection("regions").doc(afterData.targetRegionId)
            .collection("offices").doc(afterData.claimedOfficeId!)
            .collection("designated_drivers").doc(afterData.claimedDriverId)) : null;

          // 2. 원본 콜 문서 존재 여부 확인
          const sourceCallRef = admin
            .firestore()
            .collection("regions")
            .doc(afterData.sourceRegionId)
            .collection("offices")
            .doc(afterData.sourceOfficeId)
            .collection("calls")
            .doc(callId);
          const sourceCallSnap = await tx.get(sourceCallRef);

          // ========== 읽기 결과 처리 ==========
          
          const driverData = driverSnap ? driverSnap.data() : undefined;
          assignedDriverId = driverData ? driverData.authUid : null; // authUid 사용
          assignedDriverName = driverData ? driverData.name : null;
          assignedDriverPhone = driverData ? driverData.phoneNumber : null;

          logger.info(`[shared:${callId}] driverDocId=${afterData.claimedDriverId}, assignedDriverId(authUid)=${assignedDriverId}`);

          // ========== 모든 쓰기 작업 수행 ==========
          
          // 1. 대상 사무실에 콜 복사
          const destCallsRef = admin
            .firestore()
            .collection("regions")
            .doc(afterData.targetRegionId)
            .collection("offices")
            .doc(afterData.claimedOfficeId!)
            .collection("calls")
            .doc(callId);

          // 공유콜 생성 - 기사 배정이 있으면 바로 ASSIGNED 상태로 생성
          const callDoc: any = {
            ...afterData,
            status: assignedDriverId ? "ASSIGNED" : "WAITING",
            departure_set: afterData.departure ?? null,
            destination_set: afterData.destination ?? null,
            fare_set: afterData.fare ?? null,
            callType: "SHARED",
            sourceSharedCallId: callId,
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
            // 기사 배정이 있다면 바로 포함
            ...(assignedDriverId && {
              assignedDriverId: assignedDriverId,
              assignedDriverName: assignedDriverName,
              assignedDriverPhone: assignedDriverPhone,
              assignedTimestamp: admin.firestore.FieldValue.serverTimestamp(),
            })
          };
          tx.set(destCallsRef, callDoc);
          
          // assignedDriverId가 있다면 별도 업데이트로 처리 (중복 알림 방지)
          if (assignedDriverId) {
            logger.info(`[shared-claimed:${callId}] 기사 배정을 별도 업데이트로 처리: ${assignedDriverId}`);
            // 트랜잭션 외부에서 처리하도록 변경 필요
          }

          // 2. 드라이버 상태 업데이트는 트랜잭션 외부에서 처리

          // 3. 원본 콜 문서 업데이트 (존재하는 경우에만)
          if (sourceCallSnap.exists) {
            // 원본 콜은 일단 CLAIMED 상태로 업데이트 (기사 배정은 나중에)
            const sourceCallUpdates: any = {
              status: "CLAIMED", // 수락됨 상태
              claimedOfficeId: afterData.claimedOfficeId,
              assignedDriverName: `수락됨 (${afterData.claimedOfficeId})`,
              updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            };
            tx.update(sourceCallRef, sourceCallUpdates);
            logger.info(`[shared:${callId}] 원본 콜을 수락됨 상태로 업데이트 완료`);
          } else {
            logger.warn(`[shared:${callId}] 원본 콜 문서가 존재하지 않습니다. 건너뜁니다.`);
          }

          //   c) 공유콜 문서 processed 플래그 수정 → 트랜잭션 외부로 이동하여
          //      "읽기 후 쓰기" 제약을 피함 (트랜잭션 내부에 포함하면
          //      사전에 해당 문서를 읽지 않았기 때문에 Firestore가
          //      암묵적 read 를 삽입하며 오류가 발생한다)
        });

        logger.info(`[shared:${callId}] 콜 복사 및 포인트 처리 완료. 대상 사무실에 WAITING 상태로 생성됨.`);

        // ---- 기사 상태 업데이트 (기사 배정이 있는 경우만) ----
        if (assignedDriverId && driverSnap?.exists) {
          try {
            logger.info(`[shared:${callId}] 기사 상태 업데이트: ${assignedDriverId}`);
            await driverSnap.ref.update({ status: "배차중" });
            logger.info(`[shared:${callId}] 기사 상태 업데이트 완료: ${assignedDriverId}`);
          } catch (assignErr) {
            logger.error(`[shared:${callId}] 기사 상태 업데이트 실패`, assignErr);
          }
        }

        // ---- 공유콜 문서 processed 플래그 업데이트 (트랜잭션 외부) ----
        try {
          await event.data.after.ref.update({ processed: true });
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
    
    // CLAIMED -> OPEN 인지 확인 (기사가 취소한 경우)
    else if (beforeData.status === "CLAIMED" && afterData.status === "OPEN") {
      logger.info(`[shared:${callId}] 공유 콜이 취소되어 OPEN으로 되돌려졌습니다.`);
      
      try {
        // 복사된 콜이 있다면 삭제 (선택사항 - HOLD 상태로 둘 수도 있음)
        if (beforeData.claimedOfficeId) {
          const copiedCallRef = admin
            .firestore()
            .collection("regions")
            .doc(afterData.targetRegionId)
            .collection("offices")
            .doc(beforeData.claimedOfficeId)
            .collection("calls")
            .doc(callId);
            
          const copiedCallSnap = await copiedCallRef.get();
          if (copiedCallSnap.exists) {
            const copiedCallData = copiedCallSnap.data();
            logger.info(`[shared:${callId}] 복사된 콜 상태: ${copiedCallData?.status}`);
            
            // HOLD 상태인 경우에만 삭제 (이미 진행 중인 콜은 건드리지 않음)
            if (copiedCallData?.status === "HOLD") {
              await copiedCallRef.delete();
              logger.info(`[shared:${callId}] HOLD 상태의 복사된 콜을 삭제했습니다.`);
            }
          }
        }
        
        logger.info(`[shared:${callId}] 공유콜 취소 처리 완료.`);
      } catch (err) {
        logger.error(`[shared:${callId}] 공유콜 취소 처리 오류`, err);
      }
    }
  }
);

// 공유콜이 기사에 의해 취소될 때 처리하는 함수
export const onSharedCallCancelledByDriver = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "regions/{regionId}/offices/{officeId}/calls/{callId}"
  },
  async (event: any) => {
    const { callId } = event.params;

    if (!event.data) {
      logger.warn(`[call-cancelled:${callId}] 이벤트 데이터가 없습니다.`);
      return;
    }

    const beforeData = event.data.before.data();
    const afterData = event.data.after.data();

    if (!beforeData || !afterData) {
      logger.warn(`[call-cancelled:${callId}] before/after 데이터 누락`);
      return;
    }

    logger.info(`[call-cancelled:${callId}] 함수 시작. callType: ${afterData.callType}, status: ${afterData.status}, cancelledByDriver: ${afterData.cancelledByDriver}, sourceSharedCallId: ${afterData.sourceSharedCallId}`);

    // 공유콜이 기사에 의해 취소되었는지 확인
    if (afterData.callType === "SHARED" && 
        afterData.status === "CANCELLED_BY_DRIVER" && 
        afterData.cancelledByDriver === true &&
        afterData.sourceSharedCallId) {
      
      logger.info(`[call-cancelled:${callId}] 공유콜이 기사에 의해 취소되었습니다. 원본 사무실 복구 시작.`);
      
      try {
        const sourceSharedCallId = afterData.sourceSharedCallId;
        const sharedCallRef = admin.firestore().collection("shared_calls").doc(sourceSharedCallId);
        
        // shared_calls 정보 가져오기
        const sharedCallSnap = await sharedCallRef.get();
        if (!sharedCallSnap.exists) {
          logger.error(`[call-cancelled:${callId}] 원본 shared_calls 문서를 찾을 수 없습니다.`);
          return;
        }
        
        const sharedCallData = sharedCallSnap.data() as SharedCallData;
        const originalCallId = (sharedCallData as any).originalCallId;
        
        logger.info(`[call-cancelled:${callId}] shared_calls 정보: sourceRegionId=${sharedCallData.sourceRegionId}, sourceOfficeId=${sharedCallData.sourceOfficeId}, originalCallId=${originalCallId}`);
        
        if (!originalCallId) {
          logger.error(`[call-cancelled:${callId}] originalCallId가 없습니다. shared_calls 데이터를 확인하세요.`);
          return;
        }
        
        await admin.firestore().runTransaction(async (tx) => {
          // 원본 사무실의 콜 문서 레퍼런스 (originalCallId 사용!)
          const originalCallRef = admin.firestore()
            .collection("regions").doc(sharedCallData.sourceRegionId)
            .collection("offices").doc(sharedCallData.sourceOfficeId)
            .collection("calls").doc(originalCallId);
          
          // 원본 콜 문서 존재 여부 확인
          const originalCallSnap = await tx.get(originalCallRef);
          
          // shared_calls는 삭제 (원사무실에서 다시 공유 여부 결정)
          tx.delete(sharedCallRef);
          
          // 원본 콜을 HOLD 상태로 복구 (존재하는 경우에만)
          if (originalCallSnap.exists) {
            const originalCallData = originalCallSnap.data();
            logger.info(`[call-cancelled:${callId}] 원본 콜 현재 상태: ${originalCallData?.status}`);
            
            const updateData = {
              status: "HOLD", // 공유콜 취소 시 보류 상태로 변경
              callType: null,
              sourceSharedCallId: null,
              assignedDriverId: null,
              assignedDriverName: null,
              assignedDriverPhone: null,
              departure_set: null,
              destination_set: null,
              fare_set: null,
              cancelReason: `공유콜 취소됨: ${afterData.cancelReason || "사유 없음"}`,
              updatedAt: admin.firestore.FieldValue.serverTimestamp()
            };
            
            tx.update(originalCallRef, updateData);
            logger.info(`[call-cancelled:${callId}] 원본 콜을 HOLD 상태로 복구 완료. Path: ${originalCallRef.path}`);
          } else {
            logger.warn(`[call-cancelled:${callId}] 원본 콜 문서가 존재하지 않습니다. Path: ${originalCallRef.path}`);
          }
          
          logger.info(`[call-cancelled:${callId}] shared_calls 초기화 완료`);
        });
        
        // 원본 사무실 관리자들에게 FCM 알림 전송 (팝업 포함)
        const adminQuery = await admin
          .firestore()
          .collection("admins")
          .where("associatedRegionId", "==", sharedCallData.sourceRegionId)
          .where("associatedOfficeId", "==", sharedCallData.sourceOfficeId)
          .get();

        const tokens: string[] = [];
        adminQuery.docs.forEach((doc) => {
          const adminData = doc.data();
          if (adminData.fcmToken) {
            tokens.push(adminData.fcmToken);
          }
        });

        if (tokens.length > 0) {
          const message: admin.messaging.MulticastMessage = {
            notification: {
              title: "🚫 공유콜이 취소되었습니다!",
              body: `${sharedCallData.departure || "출발지"} → ${sharedCallData.destination || "도착지"}\n취소사유: ${afterData.cancelReason || "사유 없음"}\n콜이 대기상태로 복구되었습니다.`,
            },
            data: {
              type: "SHARED_CALL_CANCELLED_POPUP",
              sharedCallId: sourceSharedCallId,
              callId: sourceSharedCallId,
              departure: sharedCallData.departure || "",
              destination: sharedCallData.destination || "",
              fare: (sharedCallData.fare || 0).toString(),
              cancelReason: afterData.cancelReason || "사유 없음",
              phoneNumber: sharedCallData.phoneNumber || "",
              showPopup: "true" // 팝업 표시 플래그
            },
            android: {
              priority: "high",
              notification: {
                sound: "default",
                channelId: "call_manager_fcm_channel",
                priority: "high",
                clickAction: "FLUTTER_NOTIFICATION_CLICK"
              },
            },
            tokens,
          };

          const response = await admin.messaging().sendEachForMulticast(message);
          logger.info(`[call-cancelled:${callId}] 원본 사무실에 FCM 알림 전송 완료. 성공: ${response.successCount}, 실패: ${response.failureCount}`);
        }
        
        // 수락사무실에서 취소된 콜 문서 삭제
        await event.data.after.ref.delete();
        logger.info(`[call-cancelled:${callId}] 수락사무실에서 취소된 공유콜 삭제 완료`);
        
      } catch (error) {
        logger.error(`[call-cancelled:${callId}] 공유콜 취소 처리 오류:`, error);
      }
    }
  }
);

// 콜 상태 변경 시 알림 (운행시작, 정산완료 등)
export const onCallStatusChanged = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "regions/{regionId}/offices/{officeId}/calls/{callId}",
  },
  async (event) => {
    const { regionId, officeId, callId } = event.params;
    
    if (!event.data) {
      logger.warn(`[onCallStatusChanged:${callId}] No event data.`);
      return;
    }

    const beforeData = event.data.before?.data();
    const afterData = event.data.after?.data();

    if (!afterData) {
      logger.warn(`[onCallStatusChanged:${callId}] Missing after data.`);
      return;
    }

    // 새 문서 생성인 경우 (공유콜 포함)
    const isNewDocument = !beforeData;
    if (isNewDocument) {
      logger.info(`[onCallStatusChanged:${callId}] 새 문서 생성 감지. 알림을 보내지 않습니다. callType: ${afterData.callType}`);
      return;
    }

    // 상태가 변경되지 않았으면 무시
    if (beforeData.status === afterData.status) {
      return;
    }

    logger.info(`[onCallStatusChanged:${callId}] Status changed: ${beforeData.status} → ${afterData.status}`);

    // 운행 시작 (IN_PROGRESS) 또는 정산 완료 (COMPLETED) 상태 체크
    if (afterData.status === "IN_PROGRESS" || afterData.status === "COMPLETED") {
      // 관리자 FCM 토큰 조회
      const adminQuery = await admin
        .firestore()
        .collection("admins")
        .where("associatedRegionId", "==", regionId)
        .where("associatedOfficeId", "==", officeId)
        .get();

      const tokens = adminQuery.docs
        .map((d) => d.data().fcmToken as string | undefined)
        .filter((t): t is string => !!t && t.length > 0);

      if (tokens.length === 0) {
        logger.warn(`[onCallStatusChanged:${callId}] No admin tokens found.`);
        return;
      }

      let notificationData: any = {
        type: "",
        callId: callId,
        timestamp: Date.now().toString(),
      };

      if (afterData.status === "IN_PROGRESS") {
        // 운행 시작 알림
        const tripSummary = `출발: ${afterData.departure_set || afterData.customerAddress || "정보없음"}, 도착: ${afterData.destination_set || "정보없음"}, 요금: ${afterData.fare_set || afterData.fare || 0}원`;
        const driverName = afterData.assignedDriverName || "기사";
        
        // 공유콜인 경우: 원사무실(sourceOfficeId)에만 (공유기사) 표시, 수락사무실에는 실제 기사 이름만 표시
        const isSourceOffice = afterData.callType === "SHARED" && afterData.sourceOfficeId === officeId;
        const driverDisplayName = isSourceOffice ? `${driverName} (공유기사)` : driverName;
        
        logger.info(`[onCallStatusChanged:${callId}] 기사 이름 표시 로직 - callType: ${afterData.callType}, sourceOfficeId: ${afterData.sourceOfficeId}, currentOfficeId: ${officeId}, isSourceOffice: ${isSourceOffice}, driverDisplayName: ${driverDisplayName}`);
        
        notificationData = {
          ...notificationData,
          type: "TRIP_STARTED",
          driverName: driverDisplayName,
          driverPhone: afterData.assignedDriverPhone || "",
          customerName: afterData.customerName || "고객",
          tripSummary: tripSummary,
          departure: afterData.departure_set || "",
          destination: afterData.destination_set || "",
          fare: (afterData.fare_set || afterData.fare || 0).toString(),
          showPopup: "true"
        };

        // notification 필드 제거 - 앱에서 커스텀 알림 처리
        await admin.messaging().sendEachForMulticast({
          data: {
            ...notificationData,
            title: "🚗 운행이 시작되었습니다",
            body: `${driverName} - ${tripSummary}`,
          },
          android: {
            priority: "high",
          },
          tokens,
        });

        logger.info(`[onCallStatusChanged:${callId}] Trip started notification sent.`);
        
      } else if (afterData.status === "COMPLETED") {
        // 정산 완료 알림
        const basedriverName = afterData.assignedDriverName || "기사";
        const isSourceOffice = afterData.callType === "SHARED" && afterData.sourceOfficeId === officeId;
        const driverName = isSourceOffice ? `${basedriverName} (공유기사)` : basedriverName;
        
        logger.info(`[onCallStatusChanged:${callId}] 운행완료 기사 이름 표시 로직 - callType: ${afterData.callType}, sourceOfficeId: ${afterData.sourceOfficeId}, currentOfficeId: ${officeId}, isSourceOffice: ${isSourceOffice}, driverName: ${driverName}`);
        const customerName = afterData.customerName || "고객";
        
        notificationData = {
          ...notificationData,
          type: "TRIP_COMPLETED",
          driverName: driverName,
          customerName: customerName,
          showPopup: "true"
        };

        // notification 필드 제거 - 앱에서 커스텀 알림 처리
        await admin.messaging().sendEachForMulticast({
          data: {
            ...notificationData,
            title: "✅ 운행이 완료되었습니다",
            body: `${driverName}님이 ${customerName}님의 운행을 완료했습니다`,
          },
          android: {
            priority: "high",
          },
          tokens,
        });

        logger.info(`[onCallStatusChanged:${callId}] Trip completed notification sent.`);
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
  async (event) => {
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
// 공유콜 상태 동기화 - 수락사무실의 콜 상태를 원사무실에 반영
// =============================
export const onSharedCallStatusSync = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "regions/{regionId}/offices/{officeId}/calls/{callId}"
  },
  async (event: any) => {
    const { callId } = event.params;

    if (!event.data) {
      return;
    }

    const beforeData = event.data.before.data();
    const afterData = event.data.after.data();

    if (!beforeData || !afterData) {
      return;
    }

    // 공유콜인지 확인
    if (afterData.callType !== "SHARED" || !afterData.sourceSharedCallId) {
      logger.info(`[shared-sync:${callId}] 공유콜이 아니므로 스킵. callType: ${afterData.callType}, sourceSharedCallId: ${afterData.sourceSharedCallId}`);
      return;
    }

    // 상태가 변경되었는지 확인
    if (beforeData.status === afterData.status) {
      logger.info(`[shared-sync:${callId}] 상태 변경 없음. status: ${afterData.status}`);
      return;
    }

    // CANCELLED_BY_DRIVER는 이미 별도 함수에서 처리
    if (afterData.status === "CANCELLED_BY_DRIVER") {
      return;
    }

    logger.info(`[shared-sync:${callId}] 공유콜 상태 동기화: ${beforeData.status} → ${afterData.status}`);

    try {
      // shared_calls 문서에서 원사무실 정보 가져오기
      const sharedCallRef = admin.firestore().collection("shared_calls").doc(afterData.sourceSharedCallId);
      const sharedCallSnap = await sharedCallRef.get();
      
      if (!sharedCallSnap.exists) {
        logger.warn(`[shared-sync:${callId}] shared_calls 문서를 찾을 수 없습니다.`);
        return;
      }

      const sharedCallData = sharedCallSnap.data() as SharedCallData;

      // 원사무실 콜 업데이트 (originalCallId 사용)
      const originalCallId = (sharedCallData as any).originalCallId;
      logger.info(`[shared-sync:${callId}] sharedCallData: ${JSON.stringify(sharedCallData)}`);
      
      if (originalCallId) {
        const originalCallRef = admin.firestore()
          .collection("regions").doc(sharedCallData.sourceRegionId)
          .collection("offices").doc(sharedCallData.sourceOfficeId)
          .collection("calls").doc(originalCallId);

        const originalCallSnap = await originalCallRef.get();
        if (originalCallSnap.exists) {
          const updateData = {
            status: afterData.status,
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
          };
          await originalCallRef.update(updateData);
          logger.info(`[shared-sync:${callId}] 원사무실 콜 상태 업데이트 완료: ${originalCallId} → ${afterData.status}`);
        } else {
          logger.warn(`[shared-sync:${callId}] 원사무실 콜을 찾을 수 없습니다: ${originalCallId}`);
        }
      } else {
        logger.warn(`[shared-sync:${callId}] originalCallId가 없습니다. 대신 callId로 시도합니다.`);
        
        // originalCallId가 없으면 shared_calls의 ID와 원본 콜 ID가 같을 수 있음
        const fallbackCallRef = admin.firestore()
          .collection("regions").doc(sharedCallData.sourceRegionId)
          .collection("offices").doc(sharedCallData.sourceOfficeId)
          .collection("calls").doc(afterData.sourceSharedCallId);
          
        const fallbackSnap = await fallbackCallRef.get();
        if (fallbackSnap.exists) {
          await fallbackCallRef.update({
            status: afterData.status,
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
          });
          logger.info(`[shared-sync:${callId}] 원사무실 콜 상태 업데이트 완료 (fallback): ${afterData.sourceSharedCallId} → ${afterData.status}`);
        }
      }
    } catch (error) {
      logger.error(`[shared-sync:${callId}] 상태 동기화 오류:`, error);
    }
  }
);

// =============================
// 공유 콜에서 복사된 일반 콜이 COMPLETED 될 때 트리거
// 1) 원본 shared_calls 문서를 COMPLETED로 업데이트
// 2) 포인트 가감 처리 (10% 수수료)
// =============================
export const onSharedCallCompleted = onDocumentUpdated(
  {
    region: "asia-northeast3",
    document: "regions/{regionId}/offices/{officeId}/calls/{callId}"
  },
  async (event: any) => {
    const { regionId, officeId, callId } = event.params;

    if (!event.data) {
      logger.warn(`[call-completed:${callId}] 이벤트 데이터가 없습니다.`);
      return;
    }

    const beforeData = event.data.before.data();
    const afterData = event.data.after.data();

    if (!beforeData || !afterData) {
      logger.warn(`[call-completed:${callId}] before/after 데이터 누락`);
      return;
    }

    // 공유콜에서 복사된 콜인지 확인
    if (afterData.callType !== "SHARED" || !afterData.sourceSharedCallId) {
      return; // 일반 콜이므로 처리하지 않음
    }

    // 완료 상태로 변경되었는지 확인
    if (beforeData.status !== "COMPLETED" && afterData.status === "COMPLETED") {
      logger.info(`[call-completed:${callId}] 공유콜에서 복사된 콜이 완료되었습니다. 원본 업데이트 시작.`);
      
      const sourceSharedCallId = afterData.sourceSharedCallId;
      const fare = afterData.fare_set || afterData.fare || 0;
      const pointRatio = 0.1; // 10%
      const pointAmount = Math.round(fare * pointRatio);

      try {
        await admin.firestore().runTransaction(async (tx) => {
          // 원본 shared_calls 문서 레퍼런스
          const sharedCallRef = admin.firestore().collection("shared_calls").doc(sourceSharedCallId);
          const sharedCallSnap = await tx.get(sharedCallRef);

          if (!sharedCallSnap.exists) {
            logger.error(`[call-completed:${callId}] 원본 shared_calls 문서를 찾을 수 없습니다: ${sourceSharedCallId}`);
            return;
          }

          const sharedCallData = sharedCallSnap.data() as SharedCallData;

          // 포인트 레퍼런스
          const sourcePointsRef = admin
            .firestore()
            .collection("regions")
            .doc(sharedCallData.sourceRegionId)
            .collection("offices")
            .doc(sharedCallData.sourceOfficeId)
            .collection("points")
            .doc("points");

          const targetPointsRef = admin
            .firestore()
            .collection("regions")
            .doc(regionId)
            .collection("offices")
            .doc(officeId)
            .collection("points")
            .doc("points");

          // 포인트 잔액 읽기
          const [sourceSnap, targetSnap] = await Promise.all([
            tx.get(sourcePointsRef),
            tx.get(targetPointsRef)
          ]);

          const sourceBalance = (sourceSnap.data()?.balance || 0) + pointAmount;
          const targetBalance = (targetSnap.data()?.balance || 0) - pointAmount;

          // 1) shared_calls 문서를 COMPLETED로 업데이트
          tx.update(sharedCallRef, {
            status: "COMPLETED",
            completedAt: admin.firestore.FieldValue.serverTimestamp(),
            destCallId: callId
          });

          // 2) 포인트 문서 업데이트
          tx.set(sourcePointsRef, {
            balance: sourceBalance,
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          }, { merge: true });

          tx.set(targetPointsRef, {
            balance: targetBalance,
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          }, { merge: true });

          // 3) 포인트 거래 내역 저장
          const timestamp = admin.firestore.FieldValue.serverTimestamp();
          
          // 원본 사무실 거래 내역 (포인트 받음)
          const sourceTransactionRef = admin.firestore().collection("point_transactions").doc();
          tx.set(sourceTransactionRef, {
            type: "SHARED_CALL_RECEIVE",
            amount: pointAmount,
            description: `공유콜 수수료 수익 (${sharedCallData.departure || "출발지"} → ${sharedCallData.destination || "도착지"})`,
            timestamp: timestamp,
            regionId: sharedCallData.sourceRegionId,
            officeId: sharedCallData.sourceOfficeId,
            relatedSharedCallId: sourceSharedCallId
          });

          // 대상 사무실 거래 내역 (포인트 차감)
          const targetTransactionRef = admin.firestore().collection("point_transactions").doc();
          tx.set(targetTransactionRef, {
            type: "SHARED_CALL_SEND",
            amount: -pointAmount,
            description: `공유콜 수수료 지출 (${sharedCallData.departure || "출발지"} → ${sharedCallData.destination || "도착지"})`,
            timestamp: timestamp,
            regionId: regionId,
            officeId: officeId,
            relatedSharedCallId: sourceSharedCallId
          });

          logger.info(`[call-completed:${callId}] 포인트 처리 완료. Source: +${pointAmount}, Target: -${pointAmount}, 거래내역 생성됨`);
        });

        logger.info(`[call-completed:${callId}] 공유콜 완료 처리 성공. SharedCallId: ${sourceSharedCallId}`);

      } catch (error) {
        logger.error(`[call-completed:${callId}] 공유콜 완료 처리 오류:`, error);
      }
    }
  }
);

export { finalizeWorkDay } from "./finalizeWorkDay";

// Agora PTT 토큰 관련 함수 추가
export { generateAgoraToken, refreshAgoraToken } from "./agoraToken";

// 픽업 기사 데이터 마이그레이션 함수 (한 번만 실행)
import {onCall} from "firebase-functions/v2/https";

export const migratePickupDrivers = onCall(
  {
    region: "asia-northeast3",
  },
  async (request) => {
    logger.info("픽업 기사 데이터 마이그레이션 시작");
    
    try {
      const results = {
        found: 0,
        migrated: 0,
        errors: 0,
        details: [] as string[]
      };

      // designated_drivers에서 driverType이 PICKUP인 기사들 찾기
      const pickupDriversInDesignated = await admin
        .firestore()
        .collectionGroup("designated_drivers")
        .where("driverType", "==", "PICKUP")
        .get();

      results.found = pickupDriversInDesignated.size;
      logger.info(`발견된 픽업 기사: ${results.found}명`);

      for (const doc of pickupDriversInDesignated.docs) {
        try {
          const driverData = doc.data();
          const driverId = doc.id;
          
          // 경로에서 regionId와 officeId 추출
          const pathSegments = doc.ref.path.split('/');
          const regionId = pathSegments[1]; // regions/{regionId}
          const officeId = pathSegments[3]; // offices/{officeId}
          
          logger.info(`마이그레이션 중: ${driverData.name} (${regionId}/${officeId})`);

          // pickup_drivers 컬렉션에 새 문서 생성
          const pickupDriverRef = admin
            .firestore()
            .collection("regions")
            .doc(regionId)
            .collection("offices")
            .doc(officeId)
            .collection("pickup_drivers")
            .doc(driverId);

          await pickupDriverRef.set(driverData);
          
          // 원본 designated_drivers 문서 삭제
          await doc.ref.delete();
          
          results.migrated++;
          results.details.push(`✅ ${driverData.name} (${regionId}/${officeId}) 마이그레이션 완료`);
          
        } catch (error) {
          results.errors++;
          logger.error(`픽업 기사 마이그레이션 오류: ${doc.id}`, error);
          results.details.push(`❌ ${doc.id} 마이그레이션 실패: ${error}`);
        }
      }

      logger.info(`픽업 기사 마이그레이션 완료: ${results.migrated}/${results.found} 성공, ${results.errors} 오류`);
      
      return {
        success: true,
        message: `픽업 기사 마이그레이션 완료`,
        ...results
      };

    } catch (error) {
      logger.error("픽업 기사 마이그레이션 전체 오류:", error);
      return {
        success: false,
        message: `마이그레이션 실패: ${error}`,
        error: error
      };
    }
  }
);

const _forceDeploy = Date.now() + 1; // 배포 강제용 더미 변수
void _forceDeploy;                 // 사용해서 컴파일 경고 해소