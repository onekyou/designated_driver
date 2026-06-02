import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import * as logger from "firebase-functions/logger";
import { RtcTokenBuilder, RtcRole } from "agora-token";
import { defineSecret } from "firebase-functions/params";
import { buildMulticastPttWakePayload } from "../utils/fcmPayload";

const REGION = "asia-northeast3";

// Agora App ID는 공개값 (클라이언트 앱에도 임베드되는 식별자). Certificate만 비밀.
const APP_ID = "e5aae3aa18484cd2a1fed0018cfb15bd";
const AGORA_APP_CERTIFICATE = defineSecret("AGORA_APP_CERTIFICATE");

const TOKEN_EXPIRE_SECONDS = 86400; // 24h (상대 시간)

/**
 * PTT용 Agora RTC 토큰 발급 (FCM wake + Trigger Join 모델).
 *  입력: { regionId, officeId, uid? }  (uid 미전달 시 0 = Agora 자동 할당)
 *  채널명 = `${regionId}_${officeId}_ptt` (사무실당 1채널, 매니저+기사 공유)
 *  반환: { token, channelName, uid, expiresIn, appId }
 *  (2025-08 검증 템플릿 agoraToken.ts 복구본 — PoC 범위로 진단 로깅·token_logs·refresh 제거)
 */
export const generateAgoraToken = onCall(
  { region: REGION, secrets: [AGORA_APP_CERTIFICATE], minInstances: 1 },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "인증되지 않은 사용자입니다.");
    }

    const { regionId, officeId, uid } = request.data ?? {};
    if (!regionId || !officeId) {
      throw new HttpsError("invalid-argument", "필수 파라미터 누락: regionId, officeId");
    }
    const agoraUID = uid !== undefined ? Number(uid) : 0;
    if (isNaN(agoraUID)) {
      throw new HttpsError("invalid-argument", "uid는 숫자여야 합니다.");
    }

    const appCertificate = AGORA_APP_CERTIFICATE.value();
    if (!appCertificate || appCertificate.length !== 32) {
      logger.error(`[generateAgoraToken] AGORA_APP_CERTIFICATE 미구성/형식오류 (len=${appCertificate ? appCertificate.length : 0}, expected 32). 'firebase functions:secrets:set AGORA_APP_CERTIFICATE' 필요.`);
      throw new HttpsError("failed-precondition", "서버 설정 오류: Agora App Certificate가 구성되지 않았습니다.");
    }
    if (APP_ID.length !== 32) {
      throw new HttpsError("internal", "APP_ID 형식 오류");
    }

    const channelName = `${regionId}_${officeId}_ptt`;
    let token: string;
    try {
      token = RtcTokenBuilder.buildTokenWithUid(
        APP_ID,
        appCertificate,
        channelName,
        agoraUID,
        RtcRole.PUBLISHER, // PTT는 송수신 모두 PUBLISHER 토큰. 수신전용은 클라가 enableLocalAudio(false)로 제어.
        TOKEN_EXPIRE_SECONDS,
        TOKEN_EXPIRE_SECONDS,
      );
    } catch (err) {
      logger.error("[generateAgoraToken] 토큰 생성 예외:", err);
      throw new HttpsError("internal", `토큰 생성 오류: ${err instanceof Error ? err.message : "unknown"}`);
    }
    if (!token) {
      throw new HttpsError("internal", "토큰 생성 실패 (빈 토큰)");
    }

    logger.info(`[generateAgoraToken] auth=${request.auth.uid} channel=${channelName} agoraUID=${agoraUID}`);
    return { token, channelName, uid: agoraUID, expiresIn: TOKEN_EXPIRE_SECONDS, appId: APP_ID };
  },
);

/**
 * PTT 발화 시작 시 수신측(사무실 기사) wake.
 *  입력: { provinceId, cityId, officeId, channelName, senderName? }
 *  office designated_drivers 전체 fcmToken 수집(발신자 제외) → data-only high-priority FCM.
 *  수신 클라(driver_app)는 type=ptt_dispatch 분기에서 channelName으로 fast-join.
 */
export const sendPttWake = onCall(
  { region: REGION, minInstances: 1 },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "인증되지 않은 사용자입니다.");
    }

    const { provinceId, cityId, officeId, channelName, senderName } = request.data ?? {};
    if (!provinceId || !cityId || !officeId || !channelName) {
      throw new HttpsError("invalid-argument", "필수 파라미터 누락: provinceId, cityId, officeId, channelName");
    }

    const senderId = request.auth.uid;
    const officeRef = admin.firestore()
      .doc(`provinces/${provinceId}/cities/${cityId}/offices/${officeId}`);

    // 대리기사 토큰 수집 — onChatMessageCreated 패턴 정합 (authUid/docId == sender 제외)
    const designatedSnapshot = await officeRef.collection("designated_drivers").get();
    const tokens: string[] = designatedSnapshot.docs
      .filter((d) => {
        const data = d.data();
        if (data.authUid === senderId || d.id === senderId) return false;
        return typeof data.fcmToken === "string" && (data.fcmToken as string).length > 0;
      })
      .map((d) => d.data().fcmToken as string);
    const uniqueTokens = Array.from(new Set(tokens));

    logger.info(`[sendPttWake] channel=${channelName} 대상 기사 토큰=${uniqueTokens.length}`);
    if (uniqueTokens.length === 0) {
      return { sent: 0, failed: 0 };
    }

    const multicast = buildMulticastPttWakePayload(
      {
        type: "ptt_dispatch",
        channelName,
        senderName: senderName ?? "매니저",
        provinceId,
        cityId,
        officeId,
      },
      uniqueTokens,
    );
    const response = await admin.messaging().sendEachForMulticast(multicast);
    logger.info(`[sendPttWake] 성공=${response.successCount} 실패=${response.failureCount}`);
    return { sent: response.successCount, failed: response.failureCount };
  },
);

/**
 * PTT 콜드 음성 메모 녹음 시작 시 수신측(사무실 기사) Doze 선행 깨우기.
 *  입력: { provinceId, cityId, officeId }  (channelName 불필요 — Agora 미사용, 라디오 깨우기 목적)
 *  녹음+업로드와 병렬로 기사폰을 깨워, 음성 메시지(onChatMessageCreated) 도착 시 이미 준비되게 함.
 *  수신 클라(driver_app)는 type=ptt_prewake 분기에서 no-op(FCM 도달 자체가 Doze 관통).
 */
// minInstances 미설정(콜드 허용) — pre-wake는 녹음 시작 시 발사라 함수 콜드스타트(~2s)가
// 녹음 동안 흡수됨. 상시 웜 비용 불필요(메시지 도착은 녹음+업로드 뒤).
export const sendPttPreWake = onCall(
  { region: REGION },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "인증되지 않은 사용자입니다.");
    }

    const { provinceId, cityId, officeId } = request.data ?? {};
    if (!provinceId || !cityId || !officeId) {
      throw new HttpsError("invalid-argument", "필수 파라미터 누락: provinceId, cityId, officeId");
    }

    const senderId = request.auth.uid;
    const officeRef = admin.firestore()
      .doc(`provinces/${provinceId}/cities/${cityId}/offices/${officeId}`);

    // 대리기사 토큰 수집 — sendPttWake 패턴 정합 (authUid/docId == sender 제외)
    const designatedSnapshot = await officeRef.collection("designated_drivers").get();
    const tokens: string[] = designatedSnapshot.docs
      .filter((d) => {
        const data = d.data();
        if (data.authUid === senderId || d.id === senderId) return false;
        return typeof data.fcmToken === "string" && (data.fcmToken as string).length > 0;
      })
      .map((d) => d.data().fcmToken as string);
    const uniqueTokens = Array.from(new Set(tokens));

    logger.info(`[sendPttPreWake] office=${officeId} 대상 기사 토큰=${uniqueTokens.length}`);
    if (uniqueTokens.length === 0) {
      return { sent: 0, failed: 0 };
    }

    const multicast = buildMulticastPttWakePayload(
      {
        type: "ptt_prewake",
        provinceId,
        cityId,
        officeId,
      },
      uniqueTokens,
    );
    const response = await admin.messaging().sendEachForMulticast(multicast);
    logger.info(`[sendPttPreWake] 성공=${response.successCount} 실패=${response.failureCount}`);
    return { sent: response.successCount, failed: response.failureCount };
  },
);
