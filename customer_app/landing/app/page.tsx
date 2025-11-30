'use client'

import { useEffect, useState } from 'react'

export default function LandingPage() {
  const [officeName, setOfficeName] = useState('대리운전')
  const [hasError, setHasError] = useState(false)

  useEffect(() => {
    // URL에서 파라미터 추출
    const params = new URLSearchParams(window.location.search)
    const r = params.get('r')
    const o = params.get('o')
    const token = params.get('token')

    console.log('Current search:', window.location.search)
    console.log('Parsed params - r:', r, 'o:', o, 'token:', token)

    if (token || (r && o)) {
      // 유효한 파라미터가 있으면 Download 페이지로 즉시 리다이렉트
      console.log('유효한 QR 코드 - Download 페이지로 리다이렉트')

      // 0.5초 후 리다이렉트 (사용자에게 로딩 UI 표시)
      setTimeout(() => {
        window.location.href = `/download?${params.toString()}`
      }, 500)
    } else {
      // 잘못된 접근
      console.log('Invalid URL - missing required parameters')
      setHasError(true)
    }
  }, [])

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
                  잠시만 기다려주세요...
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
            </div>
          </footer>
        </>
      )}
    </div>
  )
}
