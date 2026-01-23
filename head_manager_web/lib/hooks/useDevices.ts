'use client';

import { useEffect, useState } from 'react';
import { db } from '../firebase';
import {
  collection,
  query,
  onSnapshot,
  orderBy,
  doc,
  updateDoc,
  serverTimestamp,
  where,
} from 'firebase/firestore';
import { EmergencyAlert, DeviceStats } from '../types/device';
import { useAuth } from './useAuth';

export function useDevices() {
  const { user } = useAuth();
  const [alerts, setAlerts] = useState<EmergencyAlert[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // DashboardLayout에서 이미 로그인 체크를 하므로
    // 여기서는 user가 있을 때만 데이터를 가져옴
    if (!user) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);

    // emergency_alerts 컬렉션 실시간 구독
    const alertsQuery = query(
      collection(db, 'emergency_alerts'),
      orderBy('timestamp', 'desc')
    );

    const unsubscribe = onSnapshot(
      alertsQuery,
      (snapshot) => {
        const alertsList: EmergencyAlert[] = [];

        snapshot.forEach((docSnap) => {
          const data = docSnap.data();

          alertsList.push({
            id: docSnap.id,
            deviceId: data.deviceId || '',
            type: data.type || 'OTHER',
            priority: data.priority || 'MEDIUM',
            provinceId: data.provinceId || '',
            cityId: data.cityId || '',
            officeId: data.officeId || '',
            officeName: data.officeName || null,
            message: data.message || '',
            crashTime: data.crashTime || null,
            timestamp: data.timestamp?.toDate() || new Date(),
            requiresImmediateAction: data.requiresImmediateAction || false,
            acknowledged: data.acknowledged || false,
            acknowledgedBy: data.acknowledgedBy || null,
            acknowledgedAt: data.acknowledgedAt?.toDate() || null,
            resolved: data.resolved || false,
            resolvedBy: data.resolvedBy || null,
            resolvedAt: data.resolvedAt?.toDate() || null,
            resolveNote: data.resolveNote || null,
          });
        });

        setAlerts(alertsList);
        setLoading(false);
      },
      (err) => {
        console.error('Emergency alerts 구독 실패:', err);
        console.error('에러 코드:', err.code);
        console.error('에러 메시지:', err.message);
        setError(`알림 데이터를 불러오는데 실패했습니다. (${err.code}: ${err.message})`);
        setLoading(false);
      }
    );

    return () => unsubscribe();
  }, [user]);

  // 알림 확인 처리
  const acknowledgeAlert = async (alertId: string) => {
    if (!user) return;

    try {
      const alertRef = doc(db, 'emergency_alerts', alertId);
      await updateDoc(alertRef, {
        acknowledged: true,
        acknowledgedBy: user.uid,
        acknowledgedAt: serverTimestamp(),
      });
    } catch (err) {
      console.error('알림 확인 실패:', err);
      throw err;
    }
  };

  // 알림 해결 처리
  const resolveAlert = async (alertId: string, note?: string) => {
    if (!user) return;

    try {
      const alertRef = doc(db, 'emergency_alerts', alertId);
      await updateDoc(alertRef, {
        resolved: true,
        resolvedBy: user.uid,
        resolvedAt: serverTimestamp(),
        resolveNote: note || null,
      });
    } catch (err) {
      console.error('알림 해결 실패:', err);
      throw err;
    }
  };

  // 필터링된 알림들
  const activeAlerts = alerts.filter((a) => !a.resolved);
  const criticalAlerts = alerts.filter((a) => !a.resolved && a.priority === 'CRITICAL');
  const acknowledgedAlerts = alerts.filter((a) => a.acknowledged && !a.resolved);
  const resolvedAlerts = alerts.filter((a) => a.resolved);

  // 오늘 해결된 알림
  const now = new Date();
  const todayResolved = resolvedAlerts.filter((a) => {
    if (!a.resolvedAt) return false;
    return (
      a.resolvedAt.getDate() === now.getDate() &&
      a.resolvedAt.getMonth() === now.getMonth() &&
      a.resolvedAt.getFullYear() === now.getFullYear()
    );
  });

  const stats: DeviceStats = {
    totalDevices: new Set(alerts.map((a) => a.deviceId)).size,
    activeAlerts: activeAlerts.length,
    criticalAlerts: criticalAlerts.length,
    acknowledgedAlerts: acknowledgedAlerts.length,
    resolvedToday: todayResolved.length,
  };

  return {
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
  };
}
