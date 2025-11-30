'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import {
  LayoutDashboard,
  Building2,
  TrendingUp,
  DollarSign,
  LogOut,
  Menu,
  Monitor,
  Archive,
  Search,
  FileText
} from 'lucide-react';
import { signOut } from 'firebase/auth';
import { auth } from '@/lib/firebase';
import { useState } from 'react';

const menuItems = [
  {
    title: '대시보드',
    href: '/dashboard',
    icon: LayoutDashboard
  },
  {
    title: '사무실 관리',
    href: '/offices',
    icon: Building2
  },
  {
    title: '락인 모니터링',
    href: '/lockin',
    icon: TrendingUp
  },
  {
    title: '환전 관리',
    href: '/withdrawals',
    icon: DollarSign
  },
  {
    title: '디바이스 모니터링',
    href: '/devices',
    icon: Monitor
  },
  {
    title: '아카이브 통계',
    href: '/archive/stats',
    icon: Archive
  },
  {
    title: '아카이브 검색',
    href: '/archive/search',
    icon: Search
  },
  {
    title: '월간 리포트',
    href: '/archive/report',
    icon: FileText
  },
];

interface SidebarProps {
  userEmail?: string;
}

export function Sidebar({ userEmail }: SidebarProps) {
  const pathname = usePathname();
  const router = useRouter();
  const [isOpen, setIsOpen] = useState(false);

  const handleLogout = async () => {
    try {
      await signOut(auth);
      router.push('/login');
    } catch (error) {
      console.error('로그아웃 에러:', error);
    }
  };

  return (
    <>
      {/* Mobile menu button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="lg:hidden fixed top-4 left-4 z-50 p-2 rounded-md bg-white shadow-md"
      >
        <Menu className="h-6 w-6" />
      </button>

      {/* Sidebar */}
      <aside
        className={`
          fixed top-0 left-0 z-40 h-screen w-64 bg-gray-900 text-white
          transition-transform duration-300 ease-in-out
          ${isOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
        `}
      >
        <div className="flex flex-col h-full">
          {/* Logo/Title */}
          <div className="p-6 border-b border-gray-800">
            <h1 className="text-xl font-bold">총관리자 시스템</h1>
            {userEmail && (
              <p className="text-sm text-gray-400 mt-2 truncate">{userEmail}</p>
            )}
          </div>

          {/* Menu Items */}
          <nav className="flex-1 p-4 space-y-2">
            {menuItems.map((item) => {
              const Icon = item.icon;
              const isActive = pathname === item.href || pathname.startsWith(item.href + '/');

              return (
                <Link
                  key={item.href}
                  href={item.href}
                  onClick={() => setIsOpen(false)}
                  className={`
                    flex items-center space-x-3 px-4 py-3 rounded-lg
                    transition-colors duration-150
                    ${
                      isActive
                        ? 'bg-indigo-600 text-white'
                        : 'text-gray-300 hover:bg-gray-800 hover:text-white'
                    }
                  `}
                >
                  <Icon className="h-5 w-5" />
                  <span className="font-medium">{item.title}</span>
                </Link>
              );
            })}
          </nav>

          {/* Logout Button */}
          <div className="p-4 border-t border-gray-800">
            <button
              onClick={handleLogout}
              className="flex items-center space-x-3 px-4 py-3 rounded-lg w-full
                       text-gray-300 hover:bg-gray-800 hover:text-white
                       transition-colors duration-150"
            >
              <LogOut className="h-5 w-5" />
              <span className="font-medium">로그아웃</span>
            </button>
          </div>
        </div>
      </aside>

      {/* Overlay for mobile */}
      {isOpen && (
        <div
          className="lg:hidden fixed inset-0 bg-black bg-opacity-50 z-30"
          onClick={() => setIsOpen(false)}
        />
      )}
    </>
  );
}
