'use client'

import { useEffect, useState } from 'react'
import { initializeApp } from 'firebase/app'
import { getFirestore, collection, addDoc, getDocs, query, where, deleteDoc } from 'firebase/firestore'

// Firebase 설정
const firebaseConfig = {
  apiKey: "AIzaSyA9x04acmgJozvpz1zpbe27rOwPmHrORXs",
  authDomain: "calldetector-5d61e.firebaseapp.com",
  projectId: "calldetector-5d61e",
  storageBucket: "calldetector-5d61e.firebasestorage.app",
  messagingSenderId: "60275310305",
  appId: "1:60275310305:web:e41e3a733e45f24f00ca34"
}

// Firebase 초기화
const app = initializeApp(firebaseConfig)
const db = getFirestore(app)

export default function DownloadPage() {
  const [officePhone, setOfficePhone] = useState<string>('')
  const [bankName, setBankName] = useState<string>('')
  const [accountNumber, setAccountNumber] = useState<string>('')
  const [accountHolder, setAccountHolder] = useState<string>('')
  const [token, setToken] = useState<string>('')

  useEffect(() => {
    // URL에서 파라미터 추출
    const params = new URLSearchParams(window.location.search)
    const tokenParam = params.get('token')

    if (tokenParam) {
      // 토큰 방식
      setToken(tokenParam)
      console.log('토큰 다운로드 페이지:', tokenParam)
      // 토큰 정보는 앱에서 직접 조회하므로 여기서는 저장만
    } else {
      // 기사 추천 방식 확인 (r, o, d, dn 파라미터)
      const regionId = params.get('r')
      const officeId = params.get('o')
      const driverId = params.get('d')
      const driverName = params.get('dn')

      if (regionId && officeId) {
        // 기사 추천 방식 - Firestore에 attribution 생성
        console.log('기사 추천 다운로드:', {regionId, officeId, driverId, driverName})

        // 화면 해상도 가져오기 (물리적 픽셀)
        const width = Math.round(window.screen.width * window.devicePixelRatio)
        const height = Math.round(window.screen.height * window.devicePixelRatio)
        const screenResolution = `${width}x${height}`

        // UUID 토큰 생성
        const generatedToken = crypto.randomUUID()

        // Firestore에 attribution 저장 (앱이 설치 후 자동으로 매칭)
        const attributionData = {
          screenResolution: screenResolution,
          token: generatedToken,
          driverId: driverId || null,
          driverName: driverName || null,
          createdAt: new Date(),
          claimed: false
        }

        // 🔥 청소 로직 + 저장 (비동기 함수)
        const saveAttributionWithCleanup = async () => {
          try {
            // 1단계: 모든 사무실에서 해당 screenResolution의 기존 attribution 삭제
            console.log(`모든 사무실에서 해당 해상도(${screenResolution})의 attribution 삭제 시작...`)

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
                  where('screenResolution', '==', screenResolution)
                )
                const existingDocs = await getDocs(existingQuery)

                for (const doc of existingDocs.docs) {
                  await deleteDoc(doc.ref)
                  totalDeleted++
                }
              }
            }

            console.log(`총 ${totalDeleted}개의 기존 attribution 삭제 완료`)

            // 2단계: QR로 지정된 사무실에만 새 attribution 생성
            await addDoc(collection(db, 'regions', regionId, 'offices', officeId, 'attributions'), attributionData)
            console.log('✅ Attribution 저장 완료:', attributionData)
          } catch (error) {
            console.error('❌ Attribution 저장 실패:', error)
          }
        }

        // 비동기 함수 실행
        saveAttributionWithCleanup()
      } else {
        // 기존 방식 (하위 호환)
        const phone = params.get('phone') || ''
        const bank = params.get('bank') || ''
        const account = params.get('account') || ''
        const holder = params.get('holder') || ''

        setOfficePhone(phone)
        setBankName(bank)
        setAccountNumber(account)
        setAccountHolder(holder)
      }
    }
  }, [])

  const handleDownloadAPK = () => {
    // 토큰을 localStorage에 저장 (앱에서 읽을 수 있도록)
    if (token) {
      localStorage.setItem('attribution_token', token)
      console.log('토큰 저장:', token)
    }
    window.location.href = 'https://calldetector-5d61e.web.app/customer_app.apk'
  }

  return (
    <div className="min-h-screen bg-gradient-to-b from-blue-50 to-white">
      {/* 헤더 */}
      <header className="bg-white shadow-sm">
        <div className="max-w-4xl mx-auto px-4 py-4">
          <h1 className="text-2xl font-bold text-blue-600">대리운전 고객앱</h1>
        </div>
      </header>

      {/* 메인 콘텐츠 */}
      <main className="max-w-4xl mx-auto px-4 py-12">
        <div className="text-center">
          <div className="mb-8">
            <div className="text-6xl mb-4">📱</div>
            <h2 className="text-3xl font-bold text-gray-900 mb-2">
              앱 다운로드
            </h2>
            <p className="text-gray-600">
              아래 버튼을 눌러 앱을 다운로드하세요
            </p>
          </div>

          {/* 사무실 정보 표시 */}
          {officePhone && (
            <div className="mb-8 bg-white p-6 rounded-lg shadow-lg max-w-md mx-auto">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">사무실 정보</h3>
              <div className="space-y-3 text-left">
                <div>
                  <p className="text-sm text-gray-500">전화번호</p>
                  <a href={`tel:${officePhone}`} className="text-lg font-semibold text-blue-600 hover:underline">
                    {officePhone}
                  </a>
                </div>
                {bankName && accountNumber && (
                  <div>
                    <p className="text-sm text-gray-500">입금 계좌</p>
                    <p className="text-md font-semibold text-gray-800">{bankName}</p>
                    <p className="text-md text-gray-700">{accountNumber}</p>
                    {accountHolder && <p className="text-sm text-gray-600">예금주: {accountHolder}</p>}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* 버튼 */}
          <div className="space-y-4">
            <a
              href="https://calldetector-5d61e.web.app/customer_app.apk"
              download="customer_app.apk"
              className="block w-full max-w-md bg-blue-600 text-white px-8 py-4 rounded-lg text-lg font-semibold hover:bg-blue-700 transition-colors shadow-lg text-center"
            >
              고객앱 다운로드 (Native Android)
            </a>
            <a
              href="https://calldetector-5d61e.web.app/customer_app_flutter.apk"
              download="customer_app_flutter.apk"
              className="block w-full max-w-md bg-green-600 text-white px-8 py-4 rounded-lg text-lg font-semibold hover:bg-green-700 transition-colors shadow-lg text-center"
            >
              고객앱 다운로드 (Flutter 신규) ⭐
            </a>
          </div>

          {/* 안내 */}
          <div className="mt-12 bg-blue-50 border border-blue-200 rounded-lg p-6 max-w-md mx-auto">
            <h3 className="font-semibold text-blue-900 mb-2">📌 설치 안내</h3>
            <ol className="text-sm text-blue-800 text-left space-y-2">
              <li>1. "APK 다운로드" 버튼을 클릭하여 앱을 다운로드하세요</li>
              <li>2. 다운로드한 APK 파일을 실행하여 앱을 설치하세요</li>
              <li>3. 앱을 실행하면 사무실 정보가 자동으로 연결됩니다</li>
            </ol>
          </div>
        </div>
      </main>

      {/* 푸터 */}
      <footer className="mt-20 bg-gray-100 py-8">
        <div className="max-w-4xl mx-auto px-4 text-center text-gray-600">
          <p>&copy; 2024 대리운전. All rights reserved.</p>
        </div>
      </footer>
    </div>
  )
}
