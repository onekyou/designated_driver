'use client';

import { useEffect, useState } from 'react';
import { doc, getDoc, collection, query, where, getDocs } from 'firebase/firestore';
import { httpsCallable } from 'firebase/functions';
import { db, functions, auth } from '@/lib/firebase';
import { Download, Smartphone, ExternalLink, Shield, ChevronDown, ChevronUp } from 'lucide-react';

interface ApkRelease {
  appName: string;
  version: string;
  releaseNotes: string;
  fileSize: number;
}

interface OwnerInfo {
  name: string;
  officeName: string;
  phone: string;
}

export default function OwnerDashboardPage() {
  const [ownerInfo, setOwnerInfo] = useState<OwnerInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [downloading, setDownloading] = useState<string | null>(null);
  const [showGuide, setShowGuide] = useState(false);
  const [releases, setReleases] = useState<Record<string, ApkRelease>>({});

  useEffect(() => {
    fetchOwnerInfo();
    fetchReleases();
  }, []);

  const fetchOwnerInfo = async () => {
    try {
      const user = auth.currentUser;
      if (!user) return;

      const adminDoc = await getDoc(doc(db, 'admins', user.uid));
      if (!adminDoc.exists()) return;

      const adminData = adminDoc.data();
      const { associatedProvinceId, associatedCityId, associatedOfficeId } = adminData;

      let officeName = adminData.name || '';
      if (associatedProvinceId && associatedCityId && associatedOfficeId) {
        const officeDoc = await getDoc(
          doc(db, `provinces/${associatedProvinceId}/cities/${associatedCityId}/offices/${associatedOfficeId}`)
        );
        if (officeDoc.exists()) {
          officeName = officeDoc.data().name || officeName;
        }
      }

      setOwnerInfo({
        name: adminData.name || '',
        officeName,
        phone: adminData.phoneNumber || '',
      });
    } catch (error) {
      console.error('사장님 정보 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchReleases = async () => {
    try {
      const q = query(
        collection(db, 'apk_releases'),
        where('isLatest', '==', true)
      );
      const snapshot = await getDocs(q);
      const map: Record<string, ApkRelease> = {};
      snapshot.forEach((doc) => {
        const data = doc.data();
        map[data.appName] = {
          appName: data.appName,
          version: data.version,
          releaseNotes: data.releaseNotes || '',
          fileSize: data.fileSize || 0,
        };
      });
      setReleases(map);
    } catch (error) {
      console.error('릴리즈 정보 조회 실패:', error);
    }
  };

  const handleDownload = async (appName: string) => {
    setDownloading(appName);
    try {
      const getUrl = httpsCallable(functions, 'getApkDownloadUrl');
      const result = await getUrl({ appName });
      const data = result.data as any;

      if (data.success && data.downloadUrl) {
        window.open(data.downloadUrl, '_blank');
      }
    } catch (error: any) {
      alert(`다운로드 실패: ${error.message}`);
    } finally {
      setDownloading(null);
    }
  };

  const formatFileSize = (bytes: number) => {
    if (!bytes) return '';
    const mb = bytes / (1024 * 1024);
    return `${mb.toFixed(1)} MB`;
  };

  const apps = [
    {
      name: 'call_detector',
      label: '콜 디텍터',
      description: '전화 감지 + 즉시 배차',
      type: 'apk' as const,
      internalTestUrl: 'https://play.google.com/apps/internaltest/4700987182764154026',
    },
    {
      name: 'call_manager',
      label: '콜 매니저',
      description: '종합 관리 (콜 목록, 기사 관리, 정산)',
      type: 'apk' as const,
      internalTestUrl: 'https://play.google.com/apps/internaltest/4701631565785589489',
    },
    {
      name: 'driver_app',
      label: '기사앱',
      description: '기사용 운행 + 정산',
      type: 'playstore' as const,
      storeUrl: 'https://play.google.com/store/apps/details?id=com.designated.driverapp.app',
    },
    {
      name: 'customer_app',
      label: '손님앱',
      description: '고객용 콜 요청',
      type: 'playstore' as const,
      storeUrl: 'https://play.google.com/store/apps/details?id=com.designated.customerapp',
    },
  ];

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-amber-600"></div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Welcome */}
      <div className="bg-white rounded-xl shadow p-6">
        <h1 className="text-2xl font-bold text-gray-900">
          {ownerInfo?.officeName || '사무실'}
        </h1>
        <p className="mt-1 text-gray-600">
          {ownerInfo?.name}님, 환영합니다.
        </p>
      </div>

      {/* APK Downloads */}
      <div className="space-y-4">
        <h2 className="text-lg font-semibold text-gray-900">앱 다운로드</h2>

        {apps.map((app) => {
          const release = releases[app.name];
          const isApk = app.type === 'apk';

          return (
            <div key={app.name} className="bg-white rounded-xl shadow p-6">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-4">
                  <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${
                    isApk ? 'bg-amber-100' : 'bg-green-100'
                  }`}>
                    <Smartphone className={`h-6 w-6 ${
                      isApk ? 'text-amber-600' : 'text-green-600'
                    }`} />
                  </div>
                  <div>
                    <h3 className="font-semibold text-gray-900">{app.label}</h3>
                    <p className="text-sm text-gray-500">{app.description}</p>
                    {release && (
                      <p className="text-xs text-gray-400 mt-1">
                        v{release.version} {formatFileSize(release.fileSize)}
                      </p>
                    )}
                  </div>
                </div>

                {isApk ? (
                  <div className="flex items-center space-x-2">
                    <button
                      onClick={() => handleDownload(app.name)}
                      disabled={downloading === app.name || !release}
                      className="flex items-center space-x-2 px-4 py-2 bg-amber-500 text-white rounded-lg hover:bg-amber-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      <Download className="h-4 w-4" />
                      <span>{downloading === app.name ? '준비중...' : !release ? '준비중' : '바로 다운로드'}</span>
                    </button>
                    {'internalTestUrl' in app && (
                      <a
                        href={(app as any).internalTestUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center space-x-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors"
                      >
                        <ExternalLink className="h-4 w-4" />
                        <span>Play Store</span>
                      </a>
                    )}
                  </div>
                ) : (
                  <a
                    href={app.storeUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center space-x-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors"
                  >
                    <ExternalLink className="h-4 w-4" />
                    <span>Play Store</span>
                  </a>
                )}
              </div>

              {'internalTestUrl' in app && (
                <p className="mt-3 text-xs text-gray-400 border-t pt-3">
                  Play Store 설치: Google 계정 등록 후 약 20~30분 뒤 다운로드 가능합니다
                </p>
              )}

              {release?.releaseNotes && (
                <p className="mt-3 text-sm text-gray-500 border-t pt-3">
                  {release.releaseNotes}
                </p>
              )}
            </div>
          );
        })}
      </div>

      {/* 설치 가이드 */}
      <div className="bg-white rounded-xl shadow">
        <button
          onClick={() => setShowGuide(!showGuide)}
          className="w-full p-6 flex items-center justify-between text-left"
        >
          <div className="flex items-center space-x-3">
            <Shield className="h-5 w-5 text-amber-500" />
            <span className="font-semibold text-gray-900">APK 설치 가이드</span>
          </div>
          {showGuide ? (
            <ChevronUp className="h-5 w-5 text-gray-400" />
          ) : (
            <ChevronDown className="h-5 w-5 text-gray-400" />
          )}
        </button>

        {showGuide && (
          <div className="px-6 pb-6 space-y-4 text-sm text-gray-700">
            <p className="text-gray-500">
              콜 디텍터와 콜 매니저는 전화 감지 기능 때문에 Play Store에 등록할 수 없습니다.
              아래 절차에 따라 직접 설치해주세요.
            </p>

            <div className="space-y-3">
              <div className="flex items-start space-x-3">
                <span className="flex-shrink-0 w-6 h-6 bg-amber-100 text-amber-700 rounded-full flex items-center justify-center text-xs font-bold">1</span>
                <p>위 다운로드 버튼을 눌러 APK 파일을 다운로드합니다.</p>
              </div>
              <div className="flex items-start space-x-3">
                <span className="flex-shrink-0 w-6 h-6 bg-amber-100 text-amber-700 rounded-full flex items-center justify-center text-xs font-bold">2</span>
                <p>다운로드된 파일을 탭하면 <strong>&ldquo;출처를 알 수 없는 앱&rdquo;</strong> 안내가 나옵니다.</p>
              </div>
              <div className="flex items-start space-x-3">
                <span className="flex-shrink-0 w-6 h-6 bg-amber-100 text-amber-700 rounded-full flex items-center justify-center text-xs font-bold">3</span>
                <p><strong>설정 &rarr; &ldquo;이 출처 허용&rdquo;</strong>을 켜주세요. (최초 1회만)</p>
              </div>
              <div className="flex items-start space-x-3">
                <span className="flex-shrink-0 w-6 h-6 bg-amber-100 text-amber-700 rounded-full flex items-center justify-center text-xs font-bold">4</span>
                <p>설치를 진행하면 완료됩니다.</p>
              </div>
            </div>

            <div className="p-3 bg-amber-50 rounded-lg">
              <p className="text-amber-800 text-xs">
                콜마당 앱은 사업자 등록된 회사에서 제공하는 공식 앱입니다.
                설치에 문제가 있으시면 콜마당 관리자에게 연락해주세요.
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
