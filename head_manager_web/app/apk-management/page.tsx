'use client';

import { DashboardLayout } from '@/components/layout/DashboardLayout';
import { useEffect, useState, useRef } from 'react';
import { collection, query, getDocs, orderBy, doc, setDoc, updateDoc, where, Timestamp } from 'firebase/firestore';
import { ref, uploadBytesResumable, getDownloadURL } from 'firebase/storage';
import { db, storage } from '@/lib/firebase';
import { Package, Upload, CheckCircle, Clock, Trash2 } from 'lucide-react';

interface ApkRelease {
  id: string;
  appName: string;
  version: string;
  storagePath: string;
  fileSize: number;
  releaseNotes: string;
  isLatest: boolean;
  uploadedAt: Timestamp;
}

export default function ApkManagementPage() {
  const [releases, setReleases] = useState<ApkRelease[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);

  // 업로드 폼
  const [selectedApp, setSelectedApp] = useState<string>('call_detector');
  const [version, setVersion] = useState('');
  const [releaseNotes, setReleaseNotes] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    fetchReleases();
  }, []);

  const fetchReleases = async () => {
    try {
      setLoading(true);
      const q = query(
        collection(db, 'apk_releases'),
        orderBy('uploadedAt', 'desc')
      );
      const snapshot = await getDocs(q);
      const list: ApkRelease[] = [];
      snapshot.forEach((doc) => {
        list.push({ id: doc.id, ...doc.data() } as ApkRelease);
      });
      setReleases(list);
    } catch (error) {
      console.error('릴리즈 목록 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleUpload = async () => {
    const file = fileInputRef.current?.files?.[0];
    if (!file || !version) {
      alert('APK 파일과 버전을 입력해주세요.');
      return;
    }

    if (!file.name.endsWith('.apk')) {
      alert('APK 파일만 업로드할 수 있습니다.');
      return;
    }

    setUploading(true);
    setUploadProgress(0);

    try {
      // 1. Firebase Storage에 업로드
      const storagePath = `apks/${selectedApp}/${selectedApp}_v${version}.apk`;
      const storageRef = ref(storage, storagePath);
      const uploadTask = uploadBytesResumable(storageRef, file);

      await new Promise<void>((resolve, reject) => {
        uploadTask.on(
          'state_changed',
          (snapshot) => {
            const progress = (snapshot.bytesTransferred / snapshot.totalBytes) * 100;
            setUploadProgress(Math.round(progress));
          },
          reject,
          resolve
        );
      });

      // 2. 기존 isLatest를 false로 변경
      const existingQuery = await getDocs(
        query(
          collection(db, 'apk_releases'),
          where('appName', '==', selectedApp),
          where('isLatest', '==', true)
        )
      );
      for (const existingDoc of existingQuery.docs) {
        await updateDoc(doc(db, 'apk_releases', existingDoc.id), { isLatest: false });
      }

      // 3. 새 릴리즈 문서 생성
      const releaseId = `${selectedApp}_v${version}`;
      await setDoc(doc(db, 'apk_releases', releaseId), {
        appName: selectedApp,
        version,
        storagePath,
        fileSize: file.size,
        releaseNotes: releaseNotes.trim(),
        isLatest: true,
        uploadedAt: Timestamp.now(),
      });

      // 4. 폼 초기화 + 새로고침
      setVersion('');
      setReleaseNotes('');
      if (fileInputRef.current) fileInputRef.current.value = '';
      await fetchReleases();

      alert(`${selectedApp} v${version} 업로드 완료!`);
    } catch (error: any) {
      console.error('업로드 실패:', error);
      alert(`업로드 실패: ${error.message}`);
    } finally {
      setUploading(false);
      setUploadProgress(0);
    }
  };

  const formatFileSize = (bytes: number) => {
    if (!bytes) return '-';
    const mb = bytes / (1024 * 1024);
    return `${mb.toFixed(1)} MB`;
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

  return (
    <DashboardLayout>
      <div className="space-y-6">
        {/* Header */}
        <div>
          <h1 className="text-3xl font-bold text-gray-900">APK 관리</h1>
          <p className="mt-2 text-gray-600">
            사장님 다운로드 페이지에 제공할 APK를 관리합니다
          </p>
        </div>

        {/* Upload Form */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">새 APK 업로드</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">앱 선택</label>
              <select
                value={selectedApp}
                onChange={(e) => setSelectedApp(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
              >
                <option value="call_detector">콜 디텍터</option>
                <option value="call_manager">콜 매니저</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">버전</label>
              <input
                type="text"
                value={version}
                onChange={(e) => setVersion(e.target.value)}
                placeholder="1.0.0"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
              />
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">릴리즈 노트</label>
              <textarea
                value={releaseNotes}
                onChange={(e) => setReleaseNotes(e.target.value)}
                rows={2}
                placeholder="변경 사항을 간단히 입력하세요..."
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
              />
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">APK 파일</label>
              <input
                ref={fileInputRef}
                type="file"
                accept=".apk"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg"
              />
            </div>
          </div>

          {uploading && (
            <div className="mt-4">
              <div className="w-full bg-gray-200 rounded-full h-3">
                <div
                  className="bg-indigo-600 h-3 rounded-full transition-all"
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
              <p className="text-sm text-gray-500 mt-1">{uploadProgress}% 업로드 중...</p>
            </div>
          )}

          <button
            onClick={handleUpload}
            disabled={uploading || !version}
            className="mt-4 flex items-center space-x-2 px-6 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Upload className="h-5 w-5" />
            <span>{uploading ? '업로드 중...' : '업로드'}</span>
          </button>
        </div>

        {/* Release List */}
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <div className="px-6 py-4 border-b">
            <h2 className="text-lg font-semibold text-gray-900">릴리즈 목록</h2>
          </div>

          {loading ? (
            <div className="p-12 text-center">
              <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600"></div>
            </div>
          ) : releases.length === 0 ? (
            <div className="p-12 text-center">
              <Package className="mx-auto h-12 w-12 text-gray-400" />
              <h3 className="mt-4 text-lg font-medium text-gray-900">아직 업로드된 APK가 없습니다</h3>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">앱</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">버전</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">크기</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">릴리즈 노트</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">업로드일</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">상태</th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {releases.map((rel) => (
                    <tr key={rel.id} className="hover:bg-gray-50">
                      <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                        {rel.appName === 'call_detector' ? '콜 디텍터' : '콜 매니저'}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                        v{rel.version}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {formatFileSize(rel.fileSize)}
                      </td>
                      <td className="px-6 py-4 text-sm text-gray-500 max-w-xs truncate">
                        {rel.releaseNotes || '-'}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {formatDate(rel.uploadedAt)}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        {rel.isLatest ? (
                          <span className="flex items-center text-green-600 text-sm">
                            <CheckCircle className="h-4 w-4 mr-1" /> 최신
                          </span>
                        ) : (
                          <span className="flex items-center text-gray-400 text-sm">
                            <Clock className="h-4 w-4 mr-1" /> 이전
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </DashboardLayout>
  );
}
