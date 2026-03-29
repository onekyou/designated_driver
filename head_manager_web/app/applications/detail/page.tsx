'use client';

import { DashboardLayout } from '@/components/layout/DashboardLayout';
import { Suspense, useEffect, useState } from 'react';
import { doc, getDoc, collection, getDocs, Timestamp } from 'firebase/firestore';
import { httpsCallable } from 'firebase/functions';
import { db, functions } from '@/lib/firebase';
import { useSearchParams, useRouter } from 'next/navigation';
import { ArrowLeft, CheckCircle, XCircle, Phone, MapPin, Clock, MessageSquare, Copy, Check } from 'lucide-react';
import Link from 'next/link';

export default function ApplicationDetailWrapper() {
  return (
    <Suspense fallback={
      <DashboardLayout>
        <div className="flex items-center justify-center h-64">
          <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600"></div>
        </div>
      </DashboardLayout>
    }>
      <ApplicationDetailPage />
    </Suspense>
  );
}

interface Application {
  id: string;
  officeName: string;
  ownerName: string;
  phone: string;
  gmail: string;
  region: string;
  dailyCalls: string;
  message: string;
  ref: string;
  status: 'pending' | 'approved' | 'rejected';
  rejectedReason?: string;
  createdAt: Timestamp;
  reviewedAt?: Timestamp;
  ownerAuthUid?: string;
  officeRef?: string;
}

interface Province {
  id: string;
  name: string;
  cities: { id: string; name: string }[];
}

function ApplicationDetailPage() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const applicationId = searchParams.get('id') || '';

  const [app, setApp] = useState<Application | null>(null);
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(false);
  const [provinces, setProvinces] = useState<Province[]>([]);
  const [selectedProvince, setSelectedProvince] = useState('');
  const [selectedCity, setSelectedCity] = useState('');
  const [rejectReason, setRejectReason] = useState('');
  const [showRejectForm, setShowRejectForm] = useState(false);
  const [result, setResult] = useState<{
    success: boolean;
    loginEmail?: string;
    tempPassword?: string;
    officeId?: string;
  } | null>(null);
  const [copied, setCopied] = useState('');

  useEffect(() => {
    if (applicationId) {
      fetchApplication();
      fetchProvinces();
    }
  }, [applicationId]);

  const fetchApplication = async () => {
    try {
      const docRef = doc(db, 'office_applications', applicationId);
      const docSnap = await getDoc(docRef);

      if (docSnap.exists()) {
        setApp({ id: docSnap.id, ...docSnap.data() } as Application);
      }
    } catch (error) {
      console.error('신청 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchProvinces = async () => {
    try {
      const snapshot = await getDocs(collection(db, 'provinces'));
      const list: Province[] = [];

      for (const provinceDoc of snapshot.docs) {
        const citiesSnapshot = await getDocs(
          collection(db, `provinces/${provinceDoc.id}/cities`)
        );
        const cities = citiesSnapshot.docs.map((c) => ({
          id: c.id,
          name: c.data().name || c.id,
        }));

        list.push({
          id: provinceDoc.id,
          name: provinceDoc.data().name || provinceDoc.id,
          cities,
        });
      }

      setProvinces(list);
    } catch (error) {
      console.error('지역 목록 조회 실패:', error);
    }
  };

  const handleApprove = async () => {
    if (!selectedProvince || !selectedCity) {
      alert('도/시를 선택해주세요.');
      return;
    }

    if (!confirm(`"${app?.officeName}" 신청을 승인하시겠습니까?`)) return;

    setProcessing(true);
    try {
      const approveFn = httpsCallable(functions, 'approveOfficeApplication');
      const response = await approveFn({
        applicationId,
        provinceId: selectedProvince,
        cityId: selectedCity,
        officeName: app?.officeName,
      });

      const data = response.data as any;
      setResult({
        success: true,
        loginEmail: data.loginEmail,
        tempPassword: data.tempPassword,
        officeId: data.officeId,
      });

      await fetchApplication();
    } catch (error: any) {
      alert(`승인 실패: ${error.message}`);
    } finally {
      setProcessing(false);
    }
  };

  const handleReject = async () => {
    if (!confirm(`"${app?.officeName}" 신청을 거부하시겠습니까?`)) return;

    setProcessing(true);
    try {
      const rejectFn = httpsCallable(functions, 'rejectOfficeApplication');
      await rejectFn({
        applicationId,
        reason: rejectReason,
      });

      await fetchApplication();
      setShowRejectForm(false);
    } catch (error: any) {
      alert(`거부 실패: ${error.message}`);
    } finally {
      setProcessing(false);
    }
  };

  const handleCopy = async (text: string, field: string) => {
    await navigator.clipboard.writeText(text);
    setCopied(field);
    setTimeout(() => setCopied(''), 2000);
  };

  const formatDate = (timestamp: Timestamp | undefined) => {
    if (!timestamp) return '-';
    const date = timestamp.toDate();
    return date.toLocaleDateString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const selectedProvinceCities = provinces.find((p) => p.id === selectedProvince)?.cities || [];

  if (loading) {
    return (
      <DashboardLayout>
        <div className="flex items-center justify-center h-64">
          <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600"></div>
        </div>
      </DashboardLayout>
    );
  }

  if (!app) {
    return (
      <DashboardLayout>
        <div className="text-center py-12">
          <p className="text-gray-600">신청서를 찾을 수 없습니다.</p>
          <Link href="/applications" className="text-indigo-600 hover:underline mt-4 inline-block">
            목록으로 돌아가기
          </Link>
        </div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex items-center space-x-4">
          <Link
            href="/applications"
            className="p-2 rounded-lg hover:bg-gray-100 transition-colors"
          >
            <ArrowLeft className="h-5 w-5 text-gray-600" />
          </Link>
          <div>
            <h1 className="text-3xl font-bold text-gray-900">신청 상세</h1>
            <p className="mt-1 text-gray-600">{app.officeName}</p>
          </div>
          <div className="ml-auto">
            {app.status === 'pending' && (
              <span className="px-3 py-1 rounded-full text-sm font-medium bg-yellow-100 text-yellow-800">대기중</span>
            )}
            {app.status === 'approved' && (
              <span className="px-3 py-1 rounded-full text-sm font-medium bg-green-100 text-green-800">승인됨</span>
            )}
            {app.status === 'rejected' && (
              <span className="px-3 py-1 rounded-full text-sm font-medium bg-red-100 text-red-800">거부됨</span>
            )}
          </div>
        </div>

        {/* 신청 정보 카드 */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">신청 정보</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="text-sm font-medium text-gray-500">사무실 이름</label>
              <p className="mt-1 text-lg text-gray-900">{app.officeName}</p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-500">대표자</label>
              <p className="mt-1 text-lg text-gray-900">{app.ownerName}</p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-500">연락처</label>
              <p className="mt-1 text-lg text-gray-900 flex items-center">
                <Phone className="h-4 w-4 mr-2 text-gray-400" />{app.phone}
              </p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-500">지역</label>
              <p className="mt-1 text-lg text-gray-900 flex items-center">
                <MapPin className="h-4 w-4 mr-2 text-gray-400" />{app.region || '-'}
              </p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-500">Gmail (Play Store 등록용)</label>
              {app.gmail ? (
                <div className="mt-1 flex items-center space-x-2">
                  <p className="text-lg text-gray-900">{app.gmail}</p>
                  <button onClick={() => handleCopy(app.gmail, 'gmail')} className="p-1 hover:bg-gray-100 rounded">
                    {copied === 'gmail' ? <Check className="h-4 w-4 text-green-600" /> : <Copy className="h-4 w-4 text-gray-400" />}
                  </button>
                </div>
              ) : (
                <p className="mt-1 text-lg text-gray-400">미입력</p>
              )}
            </div>
            <div>
              <label className="text-sm font-medium text-gray-500">일일 콜수</label>
              <p className="mt-1 text-lg text-gray-900">{app.dailyCalls || '-'}</p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-500">유입경로</label>
              <p className="mt-1 text-lg text-gray-900">{app.ref === 'direct' ? '직접 방문' : app.ref}</p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-500">신청일</label>
              <p className="mt-1 text-lg text-gray-900 flex items-center">
                <Clock className="h-4 w-4 mr-2 text-gray-400" />{formatDate(app.createdAt)}
              </p>
            </div>
            {app.reviewedAt && (
              <div>
                <label className="text-sm font-medium text-gray-500">처리일</label>
                <p className="mt-1 text-lg text-gray-900">{formatDate(app.reviewedAt)}</p>
              </div>
            )}
          </div>

          {app.message && (
            <div className="mt-6">
              <label className="text-sm font-medium text-gray-500">문의사항</label>
              <div className="mt-1 p-4 bg-gray-50 rounded-lg flex items-start">
                <MessageSquare className="h-4 w-4 mr-2 mt-1 text-gray-400 flex-shrink-0" />
                <p className="text-gray-900">{app.message}</p>
              </div>
            </div>
          )}

          {app.status === 'rejected' && app.rejectedReason && (
            <div className="mt-6 p-4 bg-red-50 rounded-lg">
              <label className="text-sm font-medium text-red-700">거부 사유</label>
              <p className="mt-1 text-red-900">{app.rejectedReason}</p>
            </div>
          )}
        </div>

        {/* 승인 결과 */}
        {result?.success && (
          <div className="bg-green-50 border border-green-200 rounded-lg p-6">
            <h2 className="text-lg font-semibold text-green-800 flex items-center">
              <CheckCircle className="h-5 w-5 mr-2" />
              승인 완료 - 사장님에게 전달할 정보
            </h2>
            <div className="mt-4 space-y-3">
              <div className="flex items-center justify-between bg-white p-3 rounded-lg">
                <div>
                  <span className="text-sm text-gray-500">로그인 이메일</span>
                  <p className="font-mono text-gray-900">{result.loginEmail}</p>
                </div>
                <button onClick={() => handleCopy(result.loginEmail!, 'email')} className="p-2 hover:bg-gray-100 rounded">
                  {copied === 'email' ? <Check className="h-4 w-4 text-green-600" /> : <Copy className="h-4 w-4 text-gray-400" />}
                </button>
              </div>
              <div className="flex items-center justify-between bg-white p-3 rounded-lg">
                <div>
                  <span className="text-sm text-gray-500">임시 비밀번호</span>
                  <p className="font-mono text-gray-900">{result.tempPassword}</p>
                </div>
                <button onClick={() => handleCopy(result.tempPassword!, 'password')} className="p-2 hover:bg-gray-100 rounded">
                  {copied === 'password' ? <Check className="h-4 w-4 text-green-600" /> : <Copy className="h-4 w-4 text-gray-400" />}
                </button>
              </div>
              <p className="text-sm text-green-700 mt-2">
                이 정보를 사장님에게 전화 또는 카톡으로 전달해주세요.
              </p>
            </div>
          </div>
        )}

        {/* 승인/거부 액션 */}
        {app.status === 'pending' && !result && (
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">승인 처리</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">도/광역시 *</label>
                <select
                  value={selectedProvince}
                  onChange={(e) => { setSelectedProvince(e.target.value); setSelectedCity(''); }}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                >
                  <option value="">선택해주세요</option>
                  {provinces.map((p) => (
                    <option key={p.id} value={p.id}>{p.name}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">시/군/구 *</label>
                <select
                  value={selectedCity}
                  onChange={(e) => setSelectedCity(e.target.value)}
                  disabled={!selectedProvince}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent disabled:bg-gray-100"
                >
                  <option value="">선택해주세요</option>
                  {selectedProvinceCities.map((c) => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="flex space-x-4">
              <button
                onClick={handleApprove}
                disabled={processing || !selectedProvince || !selectedCity}
                className="flex items-center space-x-2 px-6 py-3 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <CheckCircle className="h-5 w-5" />
                <span>{processing ? '처리중...' : '승인'}</span>
              </button>
              <button
                onClick={() => setShowRejectForm(!showRejectForm)}
                disabled={processing}
                className="flex items-center space-x-2 px-6 py-3 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors disabled:opacity-50"
              >
                <XCircle className="h-5 w-5" />
                <span>거부</span>
              </button>
            </div>

            {showRejectForm && (
              <div className="mt-4 p-4 bg-red-50 rounded-lg">
                <label className="block text-sm font-medium text-red-700 mb-2">거부 사유 (선택)</label>
                <textarea
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  rows={3}
                  className="w-full px-3 py-2 border border-red-300 rounded-lg focus:ring-2 focus:ring-red-500"
                  placeholder="거부 사유를 입력해주세요..."
                />
                <button
                  onClick={handleReject}
                  disabled={processing}
                  className="mt-3 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50"
                >
                  {processing ? '처리중...' : '거부 확정'}
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </DashboardLayout>
  );
}
