export interface Office {
  id: string;
  name: string;
  region: string;
  provinceId: string;
  cityId: string;
  phoneNumber?: string;
  address?: string;
  qrCode?: string;
  inviteCode?: string;
  landingPageUrl?: string;
  attributionThreshold?: number;
  driverCount?: number;
  customerCount?: number;
  subscriptionTier?: 'small' | 'medium' | 'large';
  subscriptionStatus?: 'trial' | 'active' | 'inactive';
  createdAt?: any;
  updatedAt?: any;
}

export interface OfficeStats {
  totalDrivers: number;
  activeDrivers: number;
  totalCustomers: number;
  totalCalls: number;
  monthlyRevenue: number;
}
