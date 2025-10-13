'use client'

import { useEffect, useState } from 'react'

export default function DownloadPage() {
  const [regionId, setRegionId] = useState<string>('')
  const [officeId, setOfficeId] = useState<string>('')
  const [officePhone, setOfficePhone] = useState<string>('')
  const [bankName, setBankName] = useState<string>('')
  const [accountNumber, setAccountNumber] = useState<string>('')
  const [accountHolder, setAccountHolder] = useState<string>('')
  const [deepLinkUrl, setDeepLinkUrl] = useState<string>('')

  useEffect(() => {
    // URL에서 파라미터 추출
    const params = new URLSearchParams(window.location.search)
    const r = params.get('r') || ''
    const o = params.get('o') || ''
    const phone = params.get('phone') || ''
    const bank = params.get('bank') || ''
    const account = params.get('account') || ''
    const holder = params.get('holder') || ''

    setRegionId(r)
    setOfficeId(o)
    setOfficePhone(phone)
    setBankName(bank)
    setAccountNumber(account)
    setAccountHolder(holder)

    // 딥링크 URL 생성
    const deepLink = `designatedcustomer://open?r=${r}&o=${o}&phone=${encodeURIComponent(phone)}&bank=${encodeURIComponent(bank)}&account=${encodeURIComponent(account)}&holder=${encodeURIComponent(holder)}`
    setDeepLinkUrl(deepLink)
  }, [])

  const handleDownloadAPK = () => {
    window.location.href = 'https://calldetector-5d61e.web.app/customer_app.apk'
  }

  const handleOpenApp = () => {
    // 딥링크 실행
    window.location.href = deepLinkUrl

    // 3초 후에도 앱이 안 열렸으면 APK 다운로드 안내
    setTimeout(() => {
      if (confirm('앱이 설치되어 있지 않습니다. APK를 다운로드하시겠습니까?')) {
        handleDownloadAPK()
      }
    }, 3000)
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
              아래 버튼을 눌러 앱을 설치하거나 실행하세요
            </p>
          </div>

          {/* 사무실 정보 표시 */}
          {officePhone && (
            <div className="mb-8 bg-white p-6 rounded-lg shadow-lg max-w-md mx-auto">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">사무실 정보</h3>
              <div className="space-y-3 text-left">
                <div>
                  <p className="text-sm text-gray-500">사무실</p>
                  <p className="text-lg font-semibold text-gray-800">{officeId}</p>
                </div>
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
            <button
              onClick={handleOpenApp}
              className="w-full max-w-md bg-blue-600 text-white px-8 py-4 rounded-lg text-lg font-semibold hover:bg-blue-700 transition-colors shadow-lg"
            >
              앱 열기 (설치되어 있는 경우)
            </button>

            <button
              onClick={handleDownloadAPK}
              className="w-full max-w-md bg-green-600 text-white px-8 py-4 rounded-lg text-lg font-semibold hover:bg-green-700 transition-colors shadow-lg"
            >
              APK 다운로드 (설치되지 않은 경우)
            </button>
          </div>

          {/* 안내 */}
          <div className="mt-12 bg-yellow-50 border border-yellow-200 rounded-lg p-6 max-w-md mx-auto">
            <h3 className="font-semibold text-yellow-900 mb-2">📌 설치 안내</h3>
            <ol className="text-sm text-yellow-800 text-left space-y-2">
              <li>1. 앱이 이미 설치되어 있다면 "앱 열기" 버튼을 클릭하세요</li>
              <li>2. 앱이 설치되어 있지 않다면 "APK 다운로드" 버튼을 클릭하세요</li>
              <li>3. 다운로드한 APK 파일을 실행하여 앱을 설치하세요</li>
              <li>4. 설치 후 앱을 실행하면 자동으로 사무실 정보가 설정됩니다</li>
            </ol>
          </div>

          {/* 디버그 정보 (개발 단계) */}
          {deepLinkUrl && (
            <div className="mt-8 bg-gray-100 p-4 rounded text-xs text-left max-w-md mx-auto">
              <p className="font-semibold mb-2">Debug Info:</p>
              <p className="break-all text-gray-600">{deepLinkUrl}</p>
            </div>
          )}
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
