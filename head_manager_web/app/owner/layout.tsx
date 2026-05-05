'use client';

import { useAuth } from '@/lib/hooks/useAuth';
import { useRouter, usePathname } from 'next/navigation';
import { useEffect } from 'react';
import { signOut } from 'firebase/auth';
import { auth } from '@/lib/firebase';
import { LogOut } from 'lucide-react';

// 인증 가드 예외: 토큰 기반 공개 페이지
const PUBLIC_OWNER_ROUTES = ['/owner/download'];

export default function OwnerLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, loading } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const isPublic = PUBLIC_OWNER_ROUTES.some((p) => pathname?.startsWith(p));

  useEffect(() => {
    if (isPublic) return;
    if (!loading && !user) {
      router.push('/owner/login');
    }
  }, [user, loading, router, isPublic]);

  const handleLogout = async () => {
    try {
      await signOut(auth);
      window.location.href = 'https://callmadang-web.web.app';
    } catch (error) {
      console.error('로그아웃 에러:', error);
    }
  };

  // 공개 페이지는 레이아웃 없이 렌더링 (로딩 스피너/헤더 모두 건너뜀)
  if (isPublic) {
    return <>{children}</>;
  }

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="text-center">
          <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-amber-600"></div>
          <p className="mt-4 text-gray-600">로딩 중...</p>
        </div>
      </div>
    );
  }

  // 로그인 페이지에서는 레이아웃 없이 렌더링
  if (!user) {
    return <>{children}</>;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white shadow-sm border-b">
        <div className="max-w-4xl mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="w-8 h-8 bg-amber-500 rounded-lg flex items-center justify-center">
              <span className="text-white font-bold text-sm">CM</span>
            </div>
            <span className="text-lg font-semibold text-gray-900">콜마당</span>
          </div>
          <button
            onClick={handleLogout}
            className="flex items-center space-x-2 text-gray-500 hover:text-gray-700 transition-colors"
          >
            <LogOut className="h-4 w-4" />
            <span className="text-sm">로그아웃</span>
          </button>
        </div>
      </header>

      {/* Content */}
      <main className="max-w-4xl mx-auto px-4 py-8">
        {children}
      </main>
    </div>
  );
}
