import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: '대리운전 - 안전한 귀가의 동반자',
  description: '편리하고 안전한 대리운전 서비스',
  openGraph: {
    title: '대리운전 앱',
    description: '지금 다운로드하고 포인트 혜택을 받으세요',
    images: ['/og-image.png']
  }
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  )
}