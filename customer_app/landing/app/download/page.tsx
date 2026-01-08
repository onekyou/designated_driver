'use client'

import { useEffect, useState } from 'react'

export default function DownloadPage() {
  const [playStoreUrl, setPlayStoreUrl] = useState<string>('')
  const [showDevSection, setShowDevSection] = useState(false)
  const [officePhone, setOfficePhone] = useState<string>('')
  const [bankName, setBankName] = useState<string>('')
  const [accountNumber, setAccountNumber] = useState<string>('')
  const [accountHolder, setAccountHolder] = useState<string>('')

  useEffect(() => {
    // URL에서 파라미터 추출
    const params = new URLSearchParams(window.location.search)

    const regionId = params.get('r')
    const officeId = params.get('o')
    const driverId = params.get('driver')
    const driverName = params.get('driverName')
    const phone = params.get('phone')
    const bank = params.get('bank')
    const account = params.get('account')
    const holder = params.get('holder')

    // 사무실 정보 저장 (UI 표시용)
    if (phone) setOfficePhone(phone)
    if (bank) setBankName(bank)
    if (account) setAccountNumber(account)
    if (holder) setAccountHolder(holder)

    // Install Referrer 파라미터 생성
    const referrerParams = new URLSearchParams()

    if (regionId) referrerParams.append('r', regionId)
    if (officeId) referrerParams.append('o', officeId)
    if (driverId) referrerParams.append('driver', driverId)
    if (driverName) referrerParams.append('driverName', driverName)
    if (phone) referrerParams.append('phone', phone)
    if (bank) referrerParams.append('bank', bank)
    if (account) referrerParams.append('account', account)
    if (holder) referrerParams.append('holder', holder)

    // Play Store URL 생성
    const baseUrl = 'https://play.google.com/store/apps/details'
    const packageId = 'com.designated.customer.app'
    const referrerString = referrerParams.toString()

    let url = `${baseUrl}?id=${packageId}`
    if (referrerString) {
      url += `&referrer=${encodeURIComponent(referrerString)}`
    }

    setPlayStoreUrl(url)
    console.log('Play Store URL 생성:', url)
  }, [])

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
              Google Play에서 앱을 다운로드하세요
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

          {/* Play Store 다운로드 버튼 */}
          <div className="space-y-4">
            <a
              href={playStoreUrl || '#'}
              className="inline-flex items-center justify-center w-full max-w-md bg-blue-600 text-white px-8 py-4 rounded-lg text-lg font-semibold hover:bg-blue-700 transition-colors shadow-lg"
            >
              <svg className="w-6 h-6 mr-2" fill="currentColor" viewBox="0 0 24 24">
                <path d="M3,20.5V3.5C3,2.91 3.34,2.39 3.84,2.15L13.69,12L3.84,21.85C3.34,21.6 3,21.09 3,20.5M16.81,15.12L6.05,21.34L14.54,12.85L16.81,15.12M20.16,10.81C20.5,11.08 20.75,11.5 20.75,12C20.75,12.5 20.53,12.9 20.18,13.18L17.89,14.5L15.39,12L17.89,9.5L20.16,10.81M6.05,2.66L16.81,8.88L14.54,11.15L6.05,2.66Z"/>
              </svg>
              Google Play에서 다운로드
            </a>
          </div>

          {/* 안내 */}
          <div className="mt-12 bg-blue-50 border border-blue-200 rounded-lg p-6 max-w-md mx-auto">
            <h3 className="font-semibold text-blue-900 mb-2">📌 설치 안내</h3>
            <ol className="text-sm text-blue-800 text-left space-y-2">
              <li>1. "Google Play에서 다운로드" 버튼을 클릭하세요</li>
              <li>2. Play Store에서 "설치" 버튼을 눌러 앱을 설치하세요</li>
              <li>3. 앱을 실행하면 사무실 정보가 자동으로 연결됩니다</li>
            </ol>
          </div>

          {/* 개발자용 APK 다운로드 섹션 (접을 수 있음) */}
          <div className="mt-12">
            <button
              onClick={() => setShowDevSection(!showDevSection)}
              className="text-sm text-gray-500 hover:text-gray-700 underline"
            >
              {showDevSection ? '▼' : '▶'} 개발/테스트용 APK 다운로드
            </button>

            {showDevSection && (
              <div className="mt-4 bg-gray-50 border border-gray-300 rounded-lg p-6 max-w-md mx-auto">
                <p className="text-xs text-gray-600 mb-4">
                  ⚠️ 개발 및 테스트 목적으로만 사용하세요. 일반 사용자는 위의 Play Store 링크를 이용해주세요.
                </p>
                <div className="space-y-3">
                  <a
                    href="https://calldetector-5d61e.web.app/customer_app.apk"
                    download="customer_app.apk"
                    className="block w-full bg-gray-600 text-white px-6 py-3 rounded-lg text-sm font-semibold hover:bg-gray-700 transition-colors text-center"
                  >
                    고객앱 APK (Native Android)
                  </a>
                  <a
                    href="https://calldetector-5d61e.web.app/customer_app_flutter.apk"
                    download="customer_app_flutter.apk"
                    className="block w-full bg-gray-600 text-white px-6 py-3 rounded-lg text-sm font-semibold hover:bg-gray-700 transition-colors text-center"
                  >
                    고객앱 APK (Flutter)
                  </a>
                </div>
              </div>
            )}
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
