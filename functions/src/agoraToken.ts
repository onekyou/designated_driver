import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import * as logger from "firebase-functions/logger";
import { RtcTokenBuilder, RtcRole } from "agora-token";
import { defineSecret } from "firebase-functions/params";

// Agora 프로젝트 설정
const APP_ID = "e5aae3aa18484cd2a1fed0018cfb15bd";
const AGORA_APP_CERTIFICATE = defineSecret("AGORA_APP_CERTIFICATE");

/**
 * PTT용 Agora 토큰 생성 함수
 * 
 * 요청 파라미터:
 * - regionId: 지역 ID (예: "seoul", "busan", "gyeonggi")
 * - officeId: 사무실 ID (예: "gangnam", "songpa", "haeundae")
 * - userId: 사용자 ID (Firebase Auth UID)
 * - userType: 사용자 타입 ("call_manager", "pickup_driver", "driver")
 * 
 * 반환값:
 * - token: 생성된 Agora 토큰
 * - channelName: 사용할 채널명
 * - uid: Agora UID (0 = 자동 할당)
 * - expiresIn: 토큰 만료 시간(초)
 */
export const generateAgoraToken = onCall(
  {
    region: "asia-northeast3",
    secrets: [AGORA_APP_CERTIFICATE],
  },
  async (request) => {
    // 인증 확인
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "인증되지 않은 사용자입니다.");
    }
    
    const data = request.data;
    const context = request;

    // 파라미터 검증
    const { regionId, officeId, userType } = data;
    
    if (!regionId || !officeId || !userType) {
      throw new HttpsError("invalid-argument", "필수 파라미터가 누락되었습니다: regionId, officeId, userType");
    }

    // 유효한 사용자 타입 확인
    const validUserTypes = ["call_manager", "pickup_driver", "driver", "media_session_ptt"];
    if (!validUserTypes.includes(userType)) {
      throw new Error(`유효하지 않은 userType: ${userType}`);
    }

    try {
      logger.info("generateAgoraToken function invoked. Starting diagnostics...");
      logger.info(`Token generation started for user: ${context.auth!.uid}, userType: ${userType}, region: ${regionId}, office: ${officeId}`);
      
      // 진단 1: defineSecret으로 반환된 시크릿 객체의 상태를 로깅합니다.
      logger.info("Inspecting the secret object itself:", AGORA_APP_CERTIFICATE);
      logger.info("Secret object type:", typeof AGORA_APP_CERTIFICATE);
      logger.info("Secret object keys:", AGORA_APP_CERTIFICATE ? Object.keys(AGORA_APP_CERTIFICATE) : "null or undefined");
      
      // App Certificate 가져오기
      let appCertificate: string | undefined;
      try {
        appCertificate = AGORA_APP_CERTIFICATE.value();
        
        // 진단 2: .value() 호출 직후의 결과를 명확하게 로깅합니다.
        if (appCertificate === undefined) {
          logger.error("CRITICAL: AGORA_APP_CERTIFICATE.value() returned undefined. The secret is not loaded into the function's runtime environment.");
        } else if (appCertificate === null) {
          logger.error("CRITICAL: AGORA_APP_CERTIFICATE.value() returned null.");
        } else if (appCertificate === "") {
          logger.warn("Warning: Secret value was read, but it is an empty string.");
        } else {
          // 보안을 위해 실제 값은 로깅하지 않습니다.
          logger.info("Success: Secret value was read correctly.");
          logger.info(`Secret value type: ${typeof appCertificate}`);
          logger.info(`Secret value length: ${appCertificate.length}`);
        }
      } catch (certError) {
        logger.error("EXCEPTION while accessing App Certificate:", certError);
        logger.error("Error type:", typeof certError);
        logger.error("Error message:", certError instanceof Error ? certError.message : 'Unknown error');
        logger.error("Error stack:", certError instanceof Error ? certError.stack : 'No stack trace');
        appCertificate = undefined;
      }
      
      if (!appCertificate) {
        logger.error("Returning empty token because appCertificate is missing.");
        logger.error(`appCertificate value: ${appCertificate}`);
        
        // 테스트용 빈 토큰 반환
        const channelName = `${regionId}_${officeId}_ptt`;
        logger.info(`Returning test mode token - Channel: ${channelName}`);
        return {
          token: "", // App Certificate 비활성화 상태에서는 빈 문자열
          channelName,
          uid: 0, // 0 = 자동 UID 할당
          expiresIn: 86400, // 24시간
          appId: APP_ID,
          testMode: true
        };
      }

    // 채널명 생성 (지역_사무실_ptt 형식)
    const channelName = `${regionId}_${officeId}_ptt`;
    
    // UID는 0으로 설정 (자동 할당)
    const uid = 0;
    
    // 역할 설정 (PTT는 모두 Publisher 권한 필요)
    const role = RtcRole.PUBLISHER;
    
    // 토큰 만료 시간 설정 (24시간)
    const expireTime = Math.floor(Date.now() / 1000) + 86400;
    
    // 파라미터 유효성 검사
    if (!APP_ID || APP_ID.length !== 32) {
      logger.error(`Invalid APP_ID: ${APP_ID}`);
      throw new Error("Invalid APP_ID format");
    }
    
    if (!appCertificate || appCertificate.length !== 32) {
      logger.error(`Invalid App Certificate length: ${appCertificate ? appCertificate.length : 0}, expected 32`);
      logger.warn("Note: App Certificate should be exactly 32 characters without any spaces or special characters");
    }
    
    if (!channelName || channelName === "") {
      logger.error("Channel name is empty!");
      throw new Error("Channel name is required");
    }
    
    // 토큰 생성 전 파라미터 로깅
    logger.info("=== Token Generation Parameters ===");
    logger.info(`APP_ID: ${APP_ID} (length: ${APP_ID.length})`);
    logger.info(`App Certificate: ${appCertificate.substring(0, 4)}...${appCertificate.substring(appCertificate.length - 4)} (length: ${appCertificate.length})`);
    logger.info(`Channel: ${channelName}`);
    logger.info(`UID: ${uid} (type: ${typeof uid})`);
    logger.info(`Role: ${role} (RtcRole.PUBLISHER = ${RtcRole.PUBLISHER})`);
    logger.info(`ExpireTime: ${expireTime} (Date: ${new Date(expireTime * 1000).toISOString()})`);
    logger.info("===================================");
    
    // 토큰 생성
    let token: string;
    try {
      token = RtcTokenBuilder.buildTokenWithUid(
        APP_ID,
        appCertificate,
        channelName,
        uid,
        role,
        expireTime,
        expireTime  // privilegeExpiredTs 파라미터 추가
      );
      
      logger.info(`Token generation result - Length: ${token ? token.length : 0}`);
      if (!token || token === "") {
        logger.error("RtcTokenBuilder returned empty token!");
        logger.error("Possible causes: Invalid App Certificate format or App ID mismatch");
      }
    } catch (tokenError) {
      logger.error("Exception during token generation:", tokenError);
      throw tokenError;
    }

    // 토큰 생성 로그 (디버깅용)
    logger.info(`Token generated for channel: ${channelName}, user: ${context.auth!.uid}, type: ${userType}`);

    // 토큰 정보를 Firestore에 저장 (선택적, 모니터링용)
    await admin.firestore()
      .collection("token_logs")
      .add({
        userId: context.auth!.uid,
        userType,
        regionId,
        officeId,
        channelName,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        expiresAt: new Date(expireTime * 1000)
      });

    // 반환 전 최종 토큰 정보 로그
    logger.info(`Final token details - Channel: ${channelName}, Token length: ${token.length}, Token: ${token ? 'HAS_VALUE' : 'EMPTY'}`);
    
    const result = {
      token,
      channelName,
      uid,
      expiresIn: 86400,
      appId: APP_ID,
      testMode: false
    };
    
    logger.info(`Returning result: ${JSON.stringify({...result, token: token ? 'HAS_VALUE' : 'EMPTY'})}`);
    
    return result;

    } catch (error) {
      logger.error("Token generation error:", error);
      logger.error("Error details:", {
        message: error instanceof Error ? error.message : 'Unknown error',
        stack: error instanceof Error ? error.stack : 'No stack trace',
        type: typeof error
      });
      throw new HttpsError("internal", `토큰 생성 중 오류가 발생했습니다: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }
  }
);

/**
 * 토큰 갱신 함수
 * 만료가 임박한 토큰을 새로운 토큰으로 교체
 */
export const refreshAgoraToken = onCall(
  {
    region: "asia-northeast3",
    secrets: [AGORA_APP_CERTIFICATE],
  },
  async (request) => {
    // 인증 확인
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "인증되지 않은 사용자입니다.");
    }

    const { channelName, currentToken } = request.data;
    
    if (!channelName) {
      throw new Error("channelName이 필요합니다.");
    }

    // channelName에서 regionId와 officeId 추출
    const parts = channelName.split("_");
    if (parts.length !== 3 || parts[2] !== "ptt") {
      throw new Error("유효하지 않은 채널명 형식입니다.");
    }

    const regionId = parts[0];
    const officeId = parts[1];

    // 갱신된 토큰 직접 생성
    try {
      const appCertificate = AGORA_APP_CERTIFICATE.value();
    
    if (!appCertificate) {
      const channelName = `${regionId}_${officeId}_ptt`;
      return {
        token: "",
        channelName,
        uid: 0,
        expiresIn: 86400,
        appId: APP_ID,
        testMode: true
      };
    }

    const channelName = `${regionId}_${officeId}_ptt`;
    const uid = 0;
    const role = RtcRole.PUBLISHER;
    const expireTime = Math.floor(Date.now() / 1000) + 86400;
    
    const token = RtcTokenBuilder.buildTokenWithUid(
      APP_ID,
      appCertificate,
      channelName,
      uid,
      role,
      expireTime,
      expireTime
    );

    return {
      token,
      channelName,
      uid,
      expiresIn: 86400,
      appId: APP_ID,
      testMode: false
    };

    } catch (error) {
      logger.error("Token refresh error:", error);
      throw new HttpsError("internal", `토큰 갱신 중 오류가 발생했습니다: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }
  }
);