import { Timestamp } from 'firebase/firestore';

export interface EmergencyAlert {
  id: string;
  deviceId: string;
  type: 'EMERGENCY_CRASH_ALERT' | 'DEVICE_OFFLINE' | 'PERMISSION_DENIED' | 'OTHER';
  priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

  // Province, City & Office
  provinceId: string;
  cityId: string;
  officeId: string;
  officeName?: string;

  // Alert details
  message: string;
  crashTime?: number;
  timestamp: Date;

  // Status
  requiresImmediateAction: boolean;
  acknowledged: boolean;
  acknowledgedBy?: string;
  acknowledgedAt?: Date;
  resolved: boolean;
  resolvedBy?: string;
  resolvedAt?: Date;
  resolveNote?: string;
}

export interface DeviceStats {
  totalDevices: number;
  activeAlerts: number;
  criticalAlerts: number;
  acknowledgedAlerts: number;
  resolvedToday: number;
}
