'use client'

import { useEffect, useState } from 'react'
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
  const [regionId, setRegionId] = useState<string>('')
  const [officeId, setOfficeId] = useState<string>('')
  const [fingerprint, setFingerprint] = useState<string>('')
  const [officeName, setOfficeName] = useState('대리운전')
  const [hasError, setHasError] = useState(false)
  const [officePhone, setOfficePhone] = useState<string>('')
  const [bankName, setBankName] = useState<string>('')
  const [accountNumber, setAccountNumber] = useState<string>('')
  const [accountHolder, setAccountHolder] = useState<string>('')

  useEffect(() => {
    // URL에서 regionId와 officeId 파싱
    // Query Parameter 방식: ?r=regionId&o=officeId&phone=...&bank=...&account=...&holder=...
    const params = new URLSearchParams(window.location.search)
    const r = params.get('r')
    const o = params.get('o')
    const phone = params.get('phone')
    const bank = params.get('bank')
    const account = params.get('account')
    const holder = params.get('holder')

    console.log('Current search:', window.location.search)
    console.log('Parsed params - r:', r, 'o:', o)

    if (r && o) {
      console.log('Setting regionId:', r, 'officeId:', o)
      setRegionId(r)
      setOfficeId(o)
      if (phone) setOfficePhone(phone)
      if (bank) setBankName(bank)
      if (account) setAccountNumber(account)
      if (holder) setAccountHolder(holder)
    } else {
      console.log('Invalid URL - missing r or o parameter')
      setHasError(true)
    }
  }, [])

  const [hasCollected, setHasCollected] = useState(false)

  useEffect(() => {
    if (regionId && officeId && !hasCollected) {
      setHasCollected(true)
      console.log('Starting fingerprint collection...')
      // 핑거프린팅 수집
      collectFingerprint()
    }
  }, [regionId, officeId, hasCollected])

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
        regionId: regionId,
        officeId: officeId
      }

      // Firebase에 저장
      await savePreAttribution(deviceInfo)
      setFingerprint(result.visitorId)

      console.log('핑거프린트 수집 완료:', result.visitorId)
    } catch (error) {
      console.error('핑거프린팅 수집 실패:', error)
    } finally {
      // 성공하든 실패하든 다운로드 페이지로 리다이렉트
      const params = new URLSearchParams({
        r: regionId,
        o: officeId,
        ...(officePhone && { phone: officePhone }),
        ...(bankName && { bank: bankName }),
        ...(accountNumber && { account: accountNumber }),
        ...(accountHolder && { holder: accountHolder })
      })

      // 1초 후 리다이렉트
      setTimeout(() => {
        console.log('리다이렉트 실행:', `/download?${params.toString()}`)
        window.location.href = `/download?${params.toString()}`
      }, 1000)
    }
  }

  const savePreAttribution = async (deviceInfo: any) => {
    try {
      // 동적 경로: regions/{regionId}/offices/{officeId}/attributions/
      await addDoc(collection(db, 'regions', regionId, 'offices', officeId, 'attributions'), {
        ...deviceInfo,
        createdAt: Timestamp.now(),
        source: 'landing',
        regionId: regionId,
        officeId: officeId,
        linkedOfficeId: officeId, // 콜매니저 CustomerInfo 구조에 맞춤
        // IP는 서버사이드에서 수집
      })
    } catch (error) {
      console.error('어트리뷰션 저장 실패:', error)
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-b from-blue-50 to-white">
      {hasError ? (
        <div className="min-h-screen flex items-center justify-center">
          <div className="text-center">
            <p className="text-gray-600">잘못된 접근입니다.</p>
            <p className="text-sm text-gray-500 mt-2">올바른 QR 코드를 스캔해주세요.</p>
          </div>
        </div>
      ) : (
        <>
          {/* 헤더 */}
          <header className="bg-white shadow-sm">
            <div className="max-w-4xl mx-auto px-4 py-4">
              <h1 className="text-2xl font-bold text-blue-600">{officeName}</h1>
            </div>
          </header>

          {/* 메인 콘텐츠 */}
          <main className="max-w-4xl mx-auto px-4 py-12">
            <div className="text-center">
              <div className="mb-8">
                <div className="animate-spin rounded-full h-16 w-16 border-b-4 border-blue-600 mx-auto mb-6"></div>
                <h2 className="text-3xl font-bold text-gray-900 mb-4">
                  {officeName}
                </h2>
                <p className="text-xl text-gray-600">
                  핑거프린트 수집 중...<br />
                  잠시만 기다려주세요
                </p>
                <p className="text-sm text-gray-500 mt-4">
                  곧 다운로드 페이지로 이동합니다
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
        </>
      )}
    </div>
  )
}