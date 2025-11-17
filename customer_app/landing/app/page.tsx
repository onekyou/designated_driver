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

  const [driverId, setDriverId] = useState<string>('')
  const [driverName, setDriverName] = useState<string>('')

  useEffect(() => {
    // URL에서 토큰 또는 regionId/officeId 파싱
    const params = new URLSearchParams(window.location.search)
    const token = params.get('token')
    const r = params.get('r')
    const o = params.get('o')
    const d = params.get('d')
    const dn = params.get('dn')

    console.log('Current search:', window.location.search)
    console.log('Parsed params - token:', token, 'r:', r, 'o:', o, 'd:', d, 'dn:', dn)

    if (token) {
      // 새 방식: 토큰 기반
      console.log('토큰 방식 - token:', token)
      handleTokenFlow(token)
    } else if (r && o) {
      // 기존 방식 또는 기사 추천: regionId/officeId (하위 호환)
      console.log('기존 방식 - regionId:', r, 'officeId:', o)
      const phone = params.get('phone')
      const bank = params.get('bank')
      const account = params.get('account')
      const holder = params.get('holder')

      setRegionId(r)
      setOfficeId(o)
      if (d) setDriverId(d)
      if (dn) setDriverName(dn)
      if (phone) setOfficePhone(phone)
      if (bank) setBankName(bank)
      if (account) setAccountNumber(account)
      if (holder) setAccountHolder(holder)
    } else {
      console.log('Invalid URL - missing token or (r and o) parameters')
      setHasError(true)
    }
  }, [])

  const [hasCollected, setHasCollected] = useState(false)
  const [token, setToken] = useState<string>('')

  // 토큰 방식 처리
  const handleTokenFlow = async (tokenValue: string) => {
    try {
      setToken(tokenValue)
      console.log('토큰 플로우 시작:', tokenValue)

      // 토큰 유효성 확인 및 사무실 정보 가져오기
      const { doc: docImport, getDoc } = await import('firebase/firestore')
      const tokenDoc = await getDoc(docImport(db, 'attributionTokens', tokenValue))

      if (!tokenDoc.exists()) {
        console.error('유효하지 않은 토큰')
        setHasError(true)
        return
      }

      const tokenData = tokenDoc.data()
      console.log('토큰 데이터:', tokenData)

      // 사무실 정보 설정
      const rid = tokenData.regionId
      const oid = tokenData.officeId

      if (rid && oid) {
        setRegionId(rid)
        setOfficeId(oid)
        setOfficeName(tokenData.officeName || '대리운전')

        // 핑거프린트 수집 (토큰 포함)
        await collectFingerprintWithToken(rid, oid, tokenValue)
      } else {
        console.error('토큰에 사무실 정보 없음')
        setHasError(true)
      }

    } catch (error) {
      console.error('토큰 처리 실패:', error)
      setHasError(true)
    }
  }

  const collectFingerprintWithToken = async (rid: string, oid: string, tokenValue: string) => {
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
        regionId: rid,
        officeId: oid,
        token: tokenValue // 토큰 포함
      }

      // Firebase에 저장 (토큰 포함)
      const { addDoc } = await import('firebase/firestore')
      const attributionsRef = collection(db, 'regions', rid, 'offices', oid, 'attributions')

      await addDoc(attributionsRef, {
        ...deviceInfo,
        createdAt: Timestamp.now(),
        source: 'landing',
        linkedOfficeId: oid,
        expiresAt: Timestamp.fromMillis(Date.now() + 24 * 60 * 60 * 1000)
      })

      console.log('핑거프린트 + 토큰 저장 완료')

      // 다운로드 페이지로 리다이렉트
      setTimeout(() => {
        console.log('다운로드 페이지로 리다이렉트:', `/download?token=${tokenValue}`)
        window.location.href = `/download?token=${tokenValue}`
      }, 1000)

    } catch (error) {
      console.error('핑거프린트 수집 실패:', error)
      // 실패해도 다운로드 페이지로 리다이렉트
      setTimeout(() => {
        window.location.href = `/download?token=${tokenValue}`
      }, 1000)
    }
  }

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
        officeId: officeId,
        ...(driverId && { driverId }), // 기사 추천 정보
        ...(driverName && { driverName })
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
        ...(accountHolder && { holder: accountHolder }),
        ...(driverId && { d: driverId }),  // ✅ 기사 ID 추가
        ...(driverName && { dn: driverName })  // ✅ 기사 이름 추가
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
      const { getDocs, query, where, deleteDoc, doc } = await import('firebase/firestore')

      // 1. 모든 지역의 모든 사무실에서 같은 screenResolution의 이전 핑거프린트 삭제 (중복 방지)
      console.log(`모든 사무실에서 같은 해상도(${deviceInfo.screenResolution})의 핑거프린트 삭제 시작...`)

      const regionsSnapshot = await getDocs(collection(db, 'regions'))
      let totalDeleted = 0

      for (const regionDoc of regionsSnapshot.docs) {
        const rid = regionDoc.id
        const officesSnapshot = await getDocs(collection(db, 'regions', rid, 'offices'))

        for (const officeDoc of officesSnapshot.docs) {
          const oid = officeDoc.id
          const attributionsRef = collection(db, 'regions', rid, 'offices', oid, 'attributions')
          const existingQuery = query(
            attributionsRef,
            where('screenResolution', '==', deviceInfo.screenResolution)
          )
          const existingDocs = await getDocs(existingQuery)

          for (const doc of existingDocs.docs) {
            await deleteDoc(doc.ref)
            totalDeleted++
          }
        }
      }

      console.log(`총 ${totalDeleted}개의 기존 핑거프린트 삭제 완료`)

      // 2. 현재 사무실에 새 핑거프린트 저장
      const attributionsRef = collection(db, 'regions', regionId, 'offices', officeId, 'attributions')

      // 토큰이 있으면 함께 저장 (토큰 방식 우선)
      const params = new URLSearchParams(window.location.search)
      const token = params.get('token')

      await addDoc(attributionsRef, {
        ...deviceInfo,
        createdAt: Timestamp.now(),
        source: 'landing',
        regionId: regionId,
        officeId: officeId,
        linkedOfficeId: officeId, // 콜매니저 CustomerInfo 구조에 맞춤
        expiresAt: Timestamp.fromMillis(Date.now() + 24 * 60 * 60 * 1000), // 24시간 후 만료
        ...(token && { token }), // 토큰이 있으면 추가
        // IP는 서버사이드에서 수집
      })

      console.log('새 핑거프린트 저장 완료')
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