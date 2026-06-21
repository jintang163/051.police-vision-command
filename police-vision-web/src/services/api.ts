import { get } from '@/utils/request';
import {
  Alarm,
  Alert,
  PoliceForce,
  Camera,
  StatsData,
  ChartData,
  HeatmapPoint,
  DashboardOverview
} from '@/types';

export const getDashboardOverview = (): Promise<{ data: DashboardOverview }> => {
  return get('/api/dashboard/overview');
};

export const getPoliceForceList = (): Promise<{ data: PoliceForce[] }> => {
  return get('/api/dashboard/police');
};

export const getAlarmStats = (): Promise<{ data: any }> => {
  return get('/api/dashboard/alarm');
};

export const getVideoStats = (): Promise<{ data: any }> => {
  return get('/api/dashboard/video');
};

export const getCameraList = (): Promise<{ data: Camera[] }> => {
  return get('/api/dashboard/cameras');
};

export const getAlarmList = (params?: { page?: number; size?: number; status?: number; type?: number }): Promise<{ data: Alarm[]; total: number }> => {
  return get('/api/dashboard/alarms', params);
};

export const getAlertList = (params?: { page?: number; size?: number }): Promise<{ data: Alert[]; total: number }> => {
  return get('/api/dashboard/alerts', params);
};

export const getStats = (): Promise<{ data: StatsData }> => {
  return get('/api/dashboard/overview').then(res => ({ data: res.data.stats }));
};

export const getAlarmTypeDistribution = (): Promise<{ data: ChartData[] }> => {
  return get('/api/dashboard/alarm').then(res => ({ data: res.data.alarmTypeDistribution || [] }));
};

export const getAlertLevelDistribution = (): Promise<{ data: ChartData[] }> => {
  return get('/api/dashboard/alarm').then(res => ({ data: res.data.alertLevelDistribution || [] }));
};

export const getHeatmapData = (): Promise<{ data: HeatmapPoint[] }> => {
  return get('/api/dashboard/overview').then(res => ({ data: res.data.gis?.heatmapData || [] }));
};

export const getAlarmDetail = (id: string): Promise<{ data: Alarm }> => {
  return get(`/api/dashboard/alarms/${id}`);
};

export const getAlertDetail = (id: string): Promise<{ data: Alert }> => {
  return get(`/api/dashboard/alerts/${id}`);
};

export const getVideoFlvUrl = (cameraId: string): string => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8085';
  return `${baseUrl}/api/video/stream/flv/${cameraId}`;
};

export const getVideoSnapshotUrl = (cameraId: string): string => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8085';
  return `${baseUrl}/api/video/stream/snapshot/${cameraId}`;
};

export const getVideoUrl = (cameraId: string): Promise<{ data: { url: string } }> => {
  return Promise.resolve({ data: { url: getVideoFlvUrl(cameraId) } });
};

export const handleAlarm = (id: string, status: string): Promise<{ data: null }> => {
  return get(`/screen/alarm/${id}/handle`, { status });
};

export const handleAlert = (id: string, status: string): Promise<{ data: null }> => {
  return get(`/screen/alert/${id}/handle`, { status });
};
