import * as functions from 'firebase-functions'
import * as admin from 'firebase-admin'

admin.initializeApp()
const db = admin.firestore()

/**
 * 핑거프린팅 기반 어트리뷰션 매칭
 * 70점 임계값 시스템
 */
export const matchAttribution = functions.https.onCall(async (data, context) => {
  const { fingerprint, phoneNumber, deviceInfo } = data

  try {
    // 1. pre_attributions에서 매칭 찾기
    const preAttributions = await db.collection('pre_attributions')
      .where('visitorId', '==', fingerprint.visitorId)
      .orderBy('createdAt', 'desc')
      .limit(5)
      .get()

    let bestMatch = null
    let highestScore = 0

    // 2. 각 매칭 후보에 대해 점수 계산
    for (const doc of preAttributions.docs) {
      const preAttr = doc.data()
      const score = calculateAttributionScore(deviceInfo, preAttr)

      if (score > highestScore) {
        highestScore = score
        bestMatch = preAttr
      }
    }

    // 3. 70점 이상이면 확정 귀속
    if (highestScore >= 70 && bestMatch) {
      const attribution = {
        customerId: phoneNumber,
        officeId: bestMatch.officeCode,
        primaryOfficeId: bestMatch.officeCode, // 최초 귀속 (불변)
        fingerprintId: fingerprint.visitorId,
        attributionScore: highestScore,
        source: 'landing',
        linkedAt: admin.firestore.Timestamp.now(),
        lastVerifiedAt: admin.firestore.Timestamp.now(),
        metadata: deviceInfo,
        auditLog: [{
          action: 'initial_link',
          timestamp: admin.firestore.Timestamp.now(),
          details: {
            score: highestScore,
            source: 'landing'
          }
        }]
      }

      // 어트리뷰션 저장
      await db.collection('attributions').add(attribution)

      // 사무실별 손님 등록
      await registerCustomer(phoneNumber, bestMatch.officeCode)

      return {
        success: true,
        officeId: bestMatch.officeCode,
        score: highestScore,
        confidence: 'HIGH'
      }
    }

    // 4. 50-69점: 수동 확인 필요
    if (highestScore >= 50 && bestMatch) {
      return {
        success: false,
        officeId: bestMatch?.officeCode,
        score: highestScore,
        confidence: 'MEDIUM',
        requiresManualConfirmation: true
      }
    }

    // 5. 50점 미만: 매칭 실패
    return {
      success: false,
      score: highestScore,
      confidence: 'LOW',
      requiresManualEntry: true
    }

  } catch (error) {
    console.error('어트리뷰션 매칭 실패:', error)
    throw new functions.https.HttpsError('internal', '어트리뷰션 처리 중 오류가 발생했습니다')
  }
})

/**
 * 어트리뷰션 점수 계산 (최대 100점)
 */
function calculateAttributionScore(current: any, stored: any): number {
  let score = 0

  // 1. Visitor ID 일치 (30점)
  if (current.visitorId === stored.visitorId) {
    score += 30
  }

  // 2. User Agent 유사도 (20점)
  const uaSimilarity = calculateStringSimilarity(current.userAgent, stored.userAgent)
  score += Math.floor(uaSimilarity * 20)

  // 3. 화면 해상도 일치 (15점)
  if (current.screenResolution === stored.screenResolution) {
    score += 15
  }

  // 4. 시간차 (20점)
  const timeDiff = Math.abs(current.timestamp - stored.timestamp)
  if (timeDiff < 5 * 60 * 1000) { // 5분 이내
    score += 20
  } else if (timeDiff < 30 * 60 * 1000) { // 30분 이내
    score += 10
  } else if (timeDiff < 60 * 60 * 1000) { // 1시간 이내
    score += 5
  }

  // 5. 언어 및 타임존 (10점)
  if (current.language === stored.language) {
    score += 5
  }
  if (current.timezone === stored.timezone) {
    score += 5
  }

  // 6. 플랫폼 일치 (5점)
  if (current.platform === stored.platform) {
    score += 5
  }

  return score
}

/**
 * 문자열 유사도 계산 (Levenshtein Distance)
 */
function calculateStringSimilarity(str1: string, str2: string): number {
  if (!str1 || !str2) return 0
  if (str1 === str2) return 1

  const longer = str1.length > str2.length ? str1 : str2
  const shorter = str1.length > str2.length ? str2 : str1

  const editDistance = levenshteinDistance(longer, shorter)
  return (longer.length - editDistance) / longer.length
}

function levenshteinDistance(str1: string, str2: string): number {
  const matrix = []

  for (let i = 0; i <= str2.length; i++) {
    matrix[i] = [i]
  }

  for (let j = 0; j <= str1.length; j++) {
    matrix[0][j] = j
  }

  for (let i = 1; i <= str2.length; i++) {
    for (let j = 1; j <= str1.length; j++) {
      if (str2.charAt(i - 1) === str1.charAt(j - 1)) {
        matrix[i][j] = matrix[i - 1][j - 1]
      } else {
        matrix[i][j] = Math.min(
          matrix[i - 1][j - 1] + 1,
          matrix[i][j - 1] + 1,
          matrix[i - 1][j] + 1
        )
      }
    }
  }

  return matrix[str2.length][str1.length]
}

/**
 * 손님 등록
 */
async function registerCustomer(phoneNumber: string, officeCode: string) {
  // 지역 및 사무실 ID 매핑 (실제로는 DB에서 조회)
  const officeMapping: Record<string, { regionId: string, officeId: string }> = {
    'seoul01': { regionId: 'seoul', officeId: 'office_seoul_01' },
    'busan01': { regionId: 'busan', officeId: 'office_busan_01' },
    'daegu01': { regionId: 'daegu', officeId: 'office_daegu_01' }
  }

  const office = officeMapping[officeCode]
  if (!office) throw new Error('Invalid office code')

  const customerRef = db
    .collection('regions').doc(office.regionId)
    .collection('offices').doc(office.officeId)
    .collection('customers').doc(phoneNumber)

  const existingCustomer = await customerRef.get()

  if (!existingCustomer.exists) {
    await customerRef.set({
      phoneNumber,
      grade: 'bronze',
      points: 0,
      totalRides: 0,
      totalSpent: 0,
      linkedOfficeId: office.officeId,
      primaryOfficeId: office.officeId,
      registeredAt: admin.firestore.Timestamp.now()
    })
  }
}

/**
 * 재귀속 요청 처리
 */
export const requestReattribution = functions.https.onCall(async (data, context) => {
  const { customerId, newOfficeId, reason, evidence, adminId } = data

  // 권한 확인
  if (!context.auth || !adminId) {
    throw new functions.https.HttpsError('unauthenticated', '관리자 권한이 필요합니다')
  }

  const reattribution = {
    customerId,
    oldOfficeId: '', // 현재 귀속 사무실 조회 필요
    newOfficeId,
    reason,
    evidence,
    requestedBy: adminId,
    status: 'pending',
    requestedAt: admin.firestore.Timestamp.now()
  }

  await db.collection('reattributions').add(reattribution)

  return { success: true, message: '재귀속 요청이 접수되었습니다' }
})

/**
 * KPI 모니터링 (90% 임계값)
 */
export const checkAttributionKPI = functions.pubsub
  .schedule('every 1 hours')
  .onRun(async (context) => {
    const now = admin.firestore.Timestamp.now()
    const oneHourAgo = new admin.firestore.Timestamp(now.seconds - 3600, now.nanoseconds)

    // 최근 1시간 어트리뷰션 성공률 계산
    const [attempts, successes] = await Promise.all([
      db.collection('pre_attributions')
        .where('createdAt', '>', oneHourAgo)
        .get(),
      db.collection('attributions')
        .where('linkedAt', '>', oneHourAgo)
        .get()
    ])

    const successRate = successes.size / attempts.size

    if (successRate < 0.9) { // 90% 미만
      // 알림 발송
      console.warn(`⚠️ 어트리뷰션 성공률 ${(successRate * 100).toFixed(1)}% (90% 미만)`)

      // Slack 또는 이메일 알림 (구현 필요)
      await sendKPIAlert({
        metric: 'attribution_success_rate',
        currentValue: successRate,
        threshold: 0.9,
        timestamp: now
      })
    }

    // KPI 메트릭 저장
    await db.collection('kpi_metrics').add({
      metric: 'attribution_success_rate',
      value: successRate,
      timestamp: now
    })
  })

async function sendKPIAlert(data: any) {
  // 실제 알림 구현 (Slack, Email 등)
  console.log('KPI Alert:', data)
}