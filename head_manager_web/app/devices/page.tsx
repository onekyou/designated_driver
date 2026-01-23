'use client';

import { useState } from 'react';
import { DashboardLayout } from '@/components/layout/DashboardLayout';
import { useDevices } from '@/lib/hooks/useDevices';
import { EmergencyAlert } from '@/lib/types/device';
import { formatDistanceToNow } from 'date-fns';
import { ko } from 'date-fns/locale';

export default function DevicesPage() {
  const {
    alerts,
    loading,
    error,
    stats,
    activeAlerts,
    criticalAlerts,
    acknowledgedAlerts,
    resolvedAlerts,
    acknowledgeAlert,
    resolveAlert,
  } = useDevices();

  const [selectedAlert, setSelectedAlert] = useState<EmergencyAlert | null>(null);
  const [resolveNote, setResolveNote] = useState('');
  const [isResolving, setIsResolving] = useState(false);

  const handleAcknowledge = async (alertId: string) => {
    try {
      await acknowledgeAlert(alertId);
    } catch (err) {
      alert('알림 확인에 실패했습니다.');
    }
  };

  const handleResolve = async () => {
    if (!selectedAlert) return;

    try {
      setIsResolving(true);
      await resolveAlert(selectedAlert.id, resolveNote);
      setSelectedAlert(null);
      setResolveNote('');
    } catch (err) {
      alert('알림 해결 처리에 실패했습니다.');
    } finally {
      setIsResolving(false);
    }
  };

  const getPriorityBadge = (priority: string) => {
    const colors = {
      CRITICAL: 'bg-red-100 text-red-800 border-red-300',
      HIGH: 'bg-orange-100 text-orange-800 border-orange-300',
      MEDIUM: 'bg-yellow-100 text-yellow-800 border-yellow-300',
      LOW: 'bg-gray-100 text-gray-800 border-gray-300',
    };

    return (
      <span
        className={`px-2 py-1 rounded-md border text-xs font-semibold ${
          colors[priority as keyof typeof colors] || colors.MEDIUM
        }`}
      >
        {priority}
      </span>
    );
  };

  const getTypeBadge = (type: string) => {
    const labels = {
      EMERGENCY_CRASH_ALERT: '앱 크래시',
      DEVICE_OFFLINE: '디바이스 오프라인',
      PERMISSION_DENIED: '권한 거부',
      OTHER: '기타',
    };

    return (
      <span className="px-2 py-1 rounded-md bg-blue-100 text-blue-800 text-xs font-medium">
        {labels[type as keyof typeof labels] || labels.OTHER}
      </span>
    );
  };

  if (loading) {
    return (
      <DashboardLayout>
        <div className="flex items-center justify-center min-h-screen">
          <div className="text-lg">로딩 중...</div>
        </div>
      </DashboardLayout>
    );
  }

  if (error) {
    return (
      <DashboardLayout>
        <div className="flex items-center justify-center min-h-screen">
          <div className="text-lg text-red-600">{error}</div>
        </div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <div className="max-w-7xl mx-auto">
      <h1 className="text-3xl font-bold mb-6">디바이스 모니터링</h1>

      {/* 통계 카드 */}
      <div className="grid grid-cols-1 md:grid-cols-5 gap-4 mb-8">
        <div className="bg-white rounded-lg shadow p-4">
          <div className="text-sm text-gray-600 mb-1">총 디바이스</div>
          <div className="text-2xl font-bold">{stats.totalDevices}</div>
        </div>

        <div className="bg-white rounded-lg shadow p-4">
          <div className="text-sm text-gray-600 mb-1">활성 알림</div>
          <div className="text-2xl font-bold text-orange-600">{stats.activeAlerts}</div>
        </div>

        <div className="bg-white rounded-lg shadow p-4">
          <div className="text-sm text-gray-600 mb-1">긴급 알림</div>
          <div className="text-2xl font-bold text-red-600">{stats.criticalAlerts}</div>
        </div>

        <div className="bg-white rounded-lg shadow p-4">
          <div className="text-sm text-gray-600 mb-1">확인됨</div>
          <div className="text-2xl font-bold text-blue-600">{stats.acknowledgedAlerts}</div>
        </div>

        <div className="bg-white rounded-lg shadow p-4">
          <div className="text-sm text-gray-600 mb-1">오늘 해결</div>
          <div className="text-2xl font-bold text-green-600">{stats.resolvedToday}</div>
        </div>
      </div>

      {/* 긴급 알림 (CRITICAL & 미해결) */}
      {criticalAlerts.length > 0 && (
        <div className="bg-red-50 border-2 border-red-300 rounded-lg p-4 mb-6">
          <h2 className="text-xl font-bold text-red-800 mb-4">🚨 긴급 알림 ({criticalAlerts.length})</h2>
          <div className="space-y-3">
            {criticalAlerts.map((alert) => (
              <div key={alert.id} className="bg-white rounded-lg p-4 border-2 border-red-200">
                <div className="flex items-start justify-between mb-2">
                  <div className="flex items-center gap-2">
                    {getPriorityBadge(alert.priority)}
                    {getTypeBadge(alert.type)}
                    <span className="font-mono text-sm text-gray-600">{alert.deviceId}</span>
                  </div>
                  <div className="text-xs text-gray-500">
                    {formatDistanceToNow(alert.timestamp, { addSuffix: true, locale: ko })}
                  </div>
                </div>

                <div className="text-lg font-semibold mb-2">{alert.message}</div>

                <div className="text-sm text-gray-600 mb-3">
                  사무실: {alert.officeName || `${alert.provinceId}/${alert.cityId} / ${alert.officeId}`}
                </div>

                <div className="flex gap-2">
                  {!alert.acknowledged && (
                    <button
                      onClick={() => handleAcknowledge(alert.id)}
                      className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 text-sm font-medium"
                    >
                      확인
                    </button>
                  )}
                  {alert.acknowledged && (
                    <span className="px-3 py-2 bg-blue-100 text-blue-800 rounded-md text-sm">
                      ✓ 확인됨
                    </span>
                  )}
                  <button
                    onClick={() => setSelectedAlert(alert)}
                    className="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 text-sm font-medium"
                  >
                    해결
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 활성 알림 (긴급 제외) */}
      {activeAlerts.filter((a) => a.priority !== 'CRITICAL').length > 0 && (
        <div className="bg-white rounded-lg shadow mb-6">
          <div className="p-4 border-b">
            <h2 className="text-xl font-bold">
              활성 알림 ({activeAlerts.filter((a) => a.priority !== 'CRITICAL').length})
            </h2>
          </div>
          <div className="divide-y">
            {activeAlerts
              .filter((a) => a.priority !== 'CRITICAL')
              .map((alert) => (
                <div key={alert.id} className="p-4 hover:bg-gray-50">
                  <div className="flex items-start justify-between mb-2">
                    <div className="flex items-center gap-2">
                      {getPriorityBadge(alert.priority)}
                      {getTypeBadge(alert.type)}
                      <span className="font-mono text-sm text-gray-600">{alert.deviceId}</span>
                    </div>
                    <div className="text-xs text-gray-500">
                      {formatDistanceToNow(alert.timestamp, { addSuffix: true, locale: ko })}
                    </div>
                  </div>

                  <div className="font-medium mb-1">{alert.message}</div>

                  <div className="text-sm text-gray-600 mb-3">
                    사무실: {alert.officeName || `${alert.provinceId}/${alert.cityId} / ${alert.officeId}`}
                  </div>

                  <div className="flex gap-2">
                    {!alert.acknowledged && (
                      <button
                        onClick={() => handleAcknowledge(alert.id)}
                        className="px-3 py-1 bg-blue-600 text-white rounded-md hover:bg-blue-700 text-sm"
                      >
                        확인
                      </button>
                    )}
                    {alert.acknowledged && (
                      <span className="px-3 py-1 bg-blue-100 text-blue-800 rounded-md text-sm">
                        ✓ 확인됨
                      </span>
                    )}
                    <button
                      onClick={() => setSelectedAlert(alert)}
                      className="px-3 py-1 bg-green-600 text-white rounded-md hover:bg-green-700 text-sm"
                    >
                      해결
                    </button>
                  </div>
                </div>
              ))}
          </div>
        </div>
      )}

      {/* 해결된 알림 (접을 수 있음) */}
      {resolvedAlerts.length > 0 && (
        <details className="bg-white rounded-lg shadow">
          <summary className="p-4 cursor-pointer font-bold text-gray-700 hover:bg-gray-50">
            해결된 알림 ({resolvedAlerts.length})
          </summary>
          <div className="divide-y">
            {resolvedAlerts.slice(0, 20).map((alert) => (
              <div key={alert.id} className="p-4 bg-gray-50">
                <div className="flex items-start justify-between mb-2">
                  <div className="flex items-center gap-2">
                    {getTypeBadge(alert.type)}
                    <span className="font-mono text-sm text-gray-500">{alert.deviceId}</span>
                    <span className="px-2 py-1 bg-green-100 text-green-800 rounded-md text-xs font-medium">
                      ✓ 해결됨
                    </span>
                  </div>
                  <div className="text-xs text-gray-500">
                    {alert.resolvedAt &&
                      formatDistanceToNow(alert.resolvedAt, { addSuffix: true, locale: ko })}
                  </div>
                </div>

                <div className="text-sm text-gray-700 mb-1">{alert.message}</div>

                {alert.resolveNote && (
                  <div className="text-sm text-gray-600 mt-2 p-2 bg-white rounded border">
                    해결 메모: {alert.resolveNote}
                  </div>
                )}
              </div>
            ))}
          </div>
        </details>
      )}

      {/* 알림이 없을 때 */}
      {alerts.length === 0 && (
        <div className="bg-white rounded-lg shadow p-12 text-center">
          <div className="text-4xl mb-4">✅</div>
          <div className="text-xl font-bold text-gray-700 mb-2">모든 디바이스 정상</div>
          <div className="text-gray-600">현재 활성화된 알림이 없습니다.</div>
        </div>
      )}

      {/* 해결 모달 */}
      {selectedAlert && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl p-6 max-w-md w-full mx-4">
            <h3 className="text-xl font-bold mb-4">알림 해결</h3>

            <div className="mb-4">
              <div className="text-sm text-gray-600 mb-2">디바이스 ID</div>
              <div className="font-mono text-sm bg-gray-100 p-2 rounded">{selectedAlert.deviceId}</div>
            </div>

            <div className="mb-4">
              <div className="text-sm text-gray-600 mb-2">메시지</div>
              <div className="text-sm bg-gray-100 p-2 rounded">{selectedAlert.message}</div>
            </div>

            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                해결 메모 (선택사항)
              </label>
              <textarea
                value={resolveNote}
                onChange={(e) => setResolveNote(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-green-500"
                rows={3}
                placeholder="해결 방법이나 참고사항을 입력하세요..."
              />
            </div>

            <div className="flex gap-3">
              <button
                onClick={handleResolve}
                disabled={isResolving}
                className="flex-1 px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 disabled:opacity-50 font-medium"
              >
                {isResolving ? '처리 중...' : '해결 완료'}
              </button>
              <button
                onClick={() => {
                  setSelectedAlert(null);
                  setResolveNote('');
                }}
                disabled={isResolving}
                className="px-4 py-2 bg-gray-200 text-gray-700 rounded-md hover:bg-gray-300 disabled:opacity-50"
              >
                취소
              </button>
            </div>
          </div>
        </div>
      )}
      </div>
    </DashboardLayout>
  );
}
