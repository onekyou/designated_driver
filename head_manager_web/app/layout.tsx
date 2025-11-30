import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "총관리자 시스템",
  description: "대리운전 통합 관리 플랫폼 - 총관리자 웹 시스템",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
