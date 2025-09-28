'use client'

import { useEffect, useState } from 'react'
import { useParams } from 'next/navigation'
import { initializeApp } from 'firebase/app'
import { getFirestore, collection, addDoc, Timestamp } from 'firebase/firestore'
import FingerprintJS from '@fingerprintjs/fingerprintjs'

// Firebase 설정 (기존 프로젝트 활용)
const firebaseConfig = {
  apiKey: "AIzaSyA9x04acmgJozvpz1zpbe27rOwPmHrORXs",
  authDomain: "calldetector-5d61e.firebaseapp.com",
  projectId: "calldetector-5d61e",
  storageBucket: "calldetector-5d61e.firebasestorage.app",
  messagingSenderId: "60275310305",
  appId: "1:60275310305:web:temp" // 임시값, 나중에 정확한 웹앱 ID로 교체
}

const app = initializeApp(firebaseConfig)
const db = getFirestore(app)

export default function LandingPage() {
  const params = useParams()
  const officeCode = params.officeCode as string
  const [fingerprint, setFingerprint] = useState<string>('')
  const [isLoading, setIsLoading] = useState(true)
  const [officeName, setOfficeName] = useState('대리운전')

  useEffect(() => {
    // 핑거프린팅 수집
    collectFingerprint()
    // 사무실 정보 로드
    loadOfficeInfo()
  }, [])

  const collectFingerprint = async () => {
    try {
      // FingerprintJS 초기화
      const fp = await FingerprintJS.load()
      const result = await fp.get()

      // 디바이스 정보 수집
      const deviceInfo = {
        visitorId: result.visitorId,
        userAgent: navigator.userAgent,
        screenResolution: `${window.screen.width}x${window.screen.height}`,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        language: navigator.language,
        platform: navigator.platform,
        cookieEnabled: navigator.cookieEnabled,
        timestamp: Date.now(),
        officeCode: officeCode
      }

      // Firebase에 저장
      await savePreAttribution(deviceInfo)
      setFingerprint(result.visitorId)
    } catch (error) {
      console.error('핑거프린팅 수집 실패:', error)
    }
  }

  const savePreAttribution = async (deviceInfo: any) => {
    try {
      // 콜매니저 계획서에 맞는 올바른 경로: regions/seoul/offices/TEST_OFFICE/attributions/
      await addDoc(collection(db, 'regions', 'seoul', 'offices', officeCode, 'attributions'), {
        ...deviceInfo,
        createdAt: Timestamp.now(),
        source: 'landing',
        officeCode: officeCode,
        officeId: officeCode, // 콜매니저 호환성을 위해 추가
        linkedOfficeId: officeCode, // 콜매니저 CustomerInfo 구조에 맞춤
        // IP는 서버사이드에서 수집
      })
    } catch (error) {
      console.error('어트리뷰션 저장 실패:', error)
    }
  }

  const loadOfficeInfo = async () => {
    // 실제로는 Firebase에서 사무실 정보 로드
    const officeNames: Record<string, string> = {
      'seoul01': '서울 대리운전',
      'busan01': '부산 대리운전',
      'daegu01': '대구 대리운전'
    }
    setOfficeName(officeNames[officeCode] || '대리운전')
    setIsLoading(false)
  }

  const handleDownload = () => {
    // Play Store로 리다이렉트 (실제 URL로 교체)
    window.location.href = `https://play.google.com/store/apps/details?id=com.designated.customer&referrer=office_${officeCode}`
  }

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">로딩 중...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gradient-to-b from-blue-50 to-white">
      {/* 헤더 */}
      <header className="bg-white shadow-sm">
        <div className="max-w-4xl mx-auto px-4 py-4">
          <h1 className="text-2xl font-bold text-blue-600">{officeName}</h1>
        </div>
      </header>

      {/* 메인 콘텐츠 */}
      <main className="max-w-4xl mx-auto px-4 py-12">
        <div className="text-center">
          <h2 className="text-4xl font-bold text-gray-900 mb-4">
            안전한 귀가의 동반자
          </h2>
          <p className="text-xl text-gray-600 mb-8">
            {officeName}의 전용 앱을 다운로드하고<br />
            편리한 대리운전 서비스를 이용하세요
          </p>

          {/* CTA 버튼 */}
          <div className="space-y-4">
            <button
              onClick={handleDownload}
              className="bg-blue-600 text-white px-8 py-4 rounded-lg text-lg font-semibold hover:bg-blue-700 transition-colors shadow-lg mr-4"
            >
              앱 다운로드
            </button>
            <button
              onClick={handleDownload}
              className="bg-green-600 text-white px-8 py-4 rounded-lg text-lg font-semibold hover:bg-green-700 transition-colors shadow-lg"
            >
              원터치 호출 테스트
            </button>
          </div>

          {/* 혜택 안내 */}
          <div className="mt-12 grid md:grid-cols-3 gap-6">
            <div className="bg-white p-6 rounded-lg shadow">
              <div className="text-3xl mb-2">📱</div>
              <h3 className="font-semibold text-gray-900 mb-2">원터치 호출</h3>
              <p className="text-gray-600">버튼 하나로 즉시 대리기사 호출</p>
            </div>
            <div className="bg-white p-6 rounded-lg shadow">
              <div className="text-3xl mb-2">💰</div>
              <h3 className="font-semibold text-gray-900 mb-2">포인트 적립</h3>
              <p className="text-gray-600">이용 시마다 포인트 적립 혜택</p>
            </div>
            <div className="bg-white p-6 rounded-lg shadow">
              <div className="text-3xl mb-2">🎁</div>
              <h3 className="font-semibold text-gray-900 mb-2">등급별 혜택</h3>
              <p className="text-gray-600">VIP까지 등급별 추가 혜택</p>
            </div>
          </div>

          {/* QR 코드 섹션 */}
          <div className="mt-12 bg-white p-8 rounded-lg shadow">
            <h3 className="text-xl font-semibold text-gray-900 mb-4">
              QR 코드로 앱 다운로드
            </h3>
            <div className="w-48 h-48 bg-gray-200 mx-auto rounded-lg flex items-center justify-center">
              {/* 실제 QR 코드 이미지 */}
              <span className="text-gray-500">QR Code</span>
            </div>
            <p className="text-sm text-gray-600 mt-4">
              스마트폰 카메라로 QR 코드를 스캔하세요
            </p>
          </div>
        </div>
      </main>

      {/* 푸터 */}
      <footer className="mt-20 bg-gray-100 py-8">
        <div className="max-w-4xl mx-auto px-4 text-center text-gray-600">
          <p>&copy; 2024 {officeName}. All rights reserved.</p>
          <p className="text-xs mt-2">Device ID: {fingerprint || '수집 중...'}</p>
        </div>
      </footer>
    </div>
  )
}