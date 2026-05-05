'use client';

import { Suspense, useEffect, useState, useMemo } from 'react';
import { useSearchParams } from 'next/navigation';
import { AlertCircle, Download, Copy, Check, Smartphone, Shield, ChevronDown, ChevronUp } from 'lucide-react';

interface AppRelease {
  url: string;
  version: string;
  fileSize: number;
  releaseNotes: string;
}

interface RedeemResponse {
  success: boolean;
  token: string;
  officeName: string;
  ownerName: string;
  callManager: AppRelease;
  callDetector: AppRelease;
}

type ErrorCode = 'token_invalid' | 'token_expired' | 'token_used' | 'server' | null;

const FUNCTIONS_BASE = 'https://asia-northeast3-calldetector-5d61e.cloudfunctions.net';

function formatFileSize(bytes: number): string {
  if (!bytes) return '';
  const mb = bytes / (1024 * 1024);
  return `${mb.toFixed(1)} MB`;
}

export default function OwnerDownloadPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-gray-50 flex items-center justify-center">
          <div className="inline-block animate-spin rounded-full h-10 w-10 border-b-2 border-amber-600"></div>
        </div>
      }
    >
      <OwnerDownloadContent />
    </Suspense>
  );
}

function OwnerDownloadContent() {
  const searchParams = useSearchParams();
  const token = searchParams.get('t') || '';

  const [data, setData] = useState<RedeemResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorCode, setErrorCode] = useState<ErrorCode>(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [copied, setCopied] = useState(false);
  const [showGuide, setShowGuide] = useState(false);

  useEffect(() => {
    if (!token) {
      setErrorCode('token_invalid');
      setErrorMessage('유효하지 않은 링크입니다.');
      setLoading(false);
      return;
    }

    let cancelled = false;
    (async () => {
      try {
        const res = await fetch(`${FUNCTIONS_BASE}/redeemDownloadToken`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ token }),
        });
        const json = await res.json();
        if (cancelled) return;

        if (!res.ok || !json.success) {
          setErrorCode((json.code as ErrorCode) || 'server');
          setErrorMessage(json.error || '서버 오류가 발생했습니다.');
          return;
        }
        setData(json as RedeemResponse);
      } catch (err) {
        if (cancelled) return;
        console.error(err);
        setErrorCode('server');
        setErrorMessage('네트워크 오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [token]);

  const handleCopy = async () => {
    if (!data?.token) return;
    try {
      await navigator.clipboard.writeText(data.token);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // ignore
    }
  };

  const errorUi = useMemo(() => {
    if (!errorCode) return null;
    const title =
      errorCode === 'token_expired'
        ? '만료된 링크입니다'
        : errorCode === 'token_used'
        ? '이미 사용된 링크입니다'
        : errorCode === 'token_invalid'
        ? '유효하지 않은 링크입니다'
        : '서버 오류';
    const hint =
      errorCode === 'token_expired'
        ? '콜마당 관리자에게 새 링크를 요청해주세요 (링크 유효기간: 7일).'
        : errorCode === 'token_used'
        ? '이미 가입이 완료되셨다면 콜 매니저 앱에서 로그인해주세요. 다시 앱을 받아야 한다면 관리자에게 연락해주세요.'
        : errorCode === 'token_invalid'
        ? '링크가 정확한지 다시 확인해주세요. 이미 가입하셨다면 콜 매니저 앱에서 로그인하시면 됩니다.'
        : '잠시 후 다시 시도해주시고, 계속 오류가 나면 관리자에게 연락해주세요.';

    return (
      <div className="bg-white rounded-xl shadow p-8 text-center">
        <div className="w-16 h-16 bg-red-100 rounded-full flex items-center justify-center mx-auto">
          <AlertCircle className="w-8 h-8 text-red-600" />
        </div>
        <h2 className="mt-4 text-xl font-bold text-gray-900">{title}</h2>
        <p className="mt-3 text-gray-600 text-sm leading-relaxed whitespace-pre-line">{hint}</p>
        {errorMessage && (
          <p className="mt-3 text-xs text-gray-400">({errorMessage})</p>
        )}
      </div>
    );
  }, [errorCode, errorMessage]);

  return (
    <div className="min-h-screen bg-gray-50 py-8 px-4">
      <div className="max-w-2xl mx-auto space-y-6">
        {/* Header */}
        <div className="text-center">
          <div className="w-16 h-16 bg-amber-500 rounded-2xl flex items-center justify-center mx-auto">
            <span className="text-white font-bold text-2xl">CM</span>
          </div>
          <h1 className="mt-4 text-2xl font-bold text-gray-900">콜마당</h1>
          <p className="mt-2 text-gray-600">사장님 앱 다운로드</p>
        </div>

        {loading && (
          <div className="bg-white rounded-xl shadow p-10 flex items-center justify-center">
            <div className="inline-block animate-spin rounded-full h-10 w-10 border-b-2 border-amber-600"></div>
          </div>
        )}

        {!loading && errorUi}

        {!loading && data && (
          <>
            {/* Office Info */}
            <div className="bg-white rounded-xl shadow p-6">
              <p className="text-sm text-gray-500">사무실</p>
              <h2 className="text-xl font-bold text-gray-900">{data.officeName}</h2>
              <p className="mt-1 text-sm text-gray-600">{data.ownerName} 사장님 환영합니다.</p>
            </div>

            {/* APK Downloads */}
            <div className="space-y-4">
              <h3 className="text-lg font-semibold text-gray-900">앱 다운로드</h3>

              {[
                { key: 'call_manager', label: '콜 매니저', desc: '콜 목록, 기사 관리, 정산', rel: data.callManager },
                { key: 'call_detector', label: '콜 디텍터', desc: '전화 감지 + 즉시 배차 (서브폰용)', rel: data.callDetector },
              ].map((app) => (
                <div key={app.key} className="bg-white rounded-xl shadow p-6">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-4">
                      <div className="w-12 h-12 bg-amber-100 rounded-xl flex items-center justify-center">
                        <Smartphone className="h-6 w-6 text-amber-600" />
                      </div>
                      <div>
                        <h4 className="font-semibold text-gray-900">{app.label}</h4>
                        <p className="text-sm text-gray-500">{app.desc}</p>
                        <p className="text-xs text-gray-400 mt-1">
                          v{app.rel.version} {formatFileSize(app.rel.fileSize)}
                        </p>
                      </div>
                    </div>

                    <a
                      href={app.rel.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center justify-center space-x-2 px-4 py-2 bg-amber-500 text-white rounded-lg hover:bg-amber-600 transition-colors min-w-[120px]"
                    >
                      <Download className="h-4 w-4" />
                      <span>다운로드</span>
                    </a>
                  </div>

                  {app.rel.releaseNotes && (
                    <p className="mt-3 text-sm text-gray-500 border-t pt-3">{app.rel.releaseNotes}</p>
                  )}
                </div>
              ))}
            </div>

            {/* 가입 안내 + 초대 토큰 */}
            <div className="bg-amber-50 border border-amber-200 rounded-xl p-6">
              <h3 className="text-base font-bold text-amber-900">다음 단계: 콜 매니저 앱에서 가입</h3>
              <ol className="mt-3 space-y-2 text-sm text-amber-800 list-decimal list-inside">
                <li>위에서 받은 <strong>콜 매니저</strong> APK 를 설치합니다.</li>
                <li>앱을 열고 <strong>&ldquo;회원가입&rdquo;</strong> 버튼을 누릅니다.</li>
                <li>이메일 / 비밀번호 / 아래 <strong>초대 토큰</strong>을 입력하고 가입합니다.</li>
                <li>가입 완료 후 같은 이메일/비밀번호로 <strong>콜 디텍터</strong>(서브폰) 도 로그인합니다.</li>
              </ol>

              <div className="mt-4">
                <label className="block text-xs font-semibold text-amber-900 mb-1">초대 토큰</label>
                <div className="flex items-center gap-2">
                  <code className="flex-1 bg-white border border-amber-300 rounded-lg px-3 py-2 text-xs text-gray-700 break-all">
                    {data.token}
                  </code>
                  <button
                    onClick={handleCopy}
                    className="flex items-center gap-1 px-3 py-2 bg-amber-500 text-white text-xs rounded-lg hover:bg-amber-600 transition-colors"
                  >
                    {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
                    <span>{copied ? '복사됨' : '복사'}</span>
                  </button>
                </div>
                <p className="mt-2 text-xs text-amber-700">
                  이 링크는 7일간 유효하며, 가입이 완료되면 토큰은 1회 사용 후 만료됩니다.
                </p>
              </div>
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
                {showGuide ? <ChevronUp className="h-5 w-5 text-gray-400" /> : <ChevronDown className="h-5 w-5 text-gray-400" />}
              </button>

              {showGuide && (
                <div className="px-6 pb-6 space-y-4 text-sm text-gray-700">
                  <p className="text-gray-500">
                    콜 매니저와 콜 디텍터는 전화 감지 기능 때문에 Play Store 에 등록할 수 없습니다.
                    아래 절차에 따라 직접 설치해주세요.
                  </p>
                  <div className="space-y-3">
                    {[
                      '위 다운로드 버튼을 눌러 APK 파일을 다운로드합니다.',
                      '다운로드된 파일을 탭하면 "출처를 알 수 없는 앱" 안내가 나옵니다.',
                      '"설정 → 이 출처 허용" 을 켜주세요. (최초 1회만)',
                      '설치를 진행하면 완료됩니다.',
                    ].map((text, idx) => (
                      <div key={idx} className="flex items-start space-x-3">
                        <span className="flex-shrink-0 w-6 h-6 bg-amber-100 text-amber-700 rounded-full flex items-center justify-center text-xs font-bold">
                          {idx + 1}
                        </span>
                        <p>{text}</p>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
