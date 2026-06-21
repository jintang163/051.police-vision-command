import { get } from '@/utils/request';
import {
  Alarm,
  Alert,
  PoliceForce,
  Camera,
  StatsData,
  ChartData,
  HeatmapPoint
} from '@/types';

export const getStats = (): Promise<{ data: StatsData }> => {
  return get('/screen/stats');
};

export const getAlarmList = (params?: { page?: number; size?: number }): Promise<{ data: Alarm[]; total: number }> => {
  return get('/screen/alarms', params);
};

export const getAlertList = (params?: { page?: number; size?: number }): Promise<{ data: Alert[]; total: number }> => {
  return get('/screen/alerts', params);
};

export const getPoliceForceList = (): Promise<{ data: PoliceForce[] }> => {
  return get('/screen/police');
};

export const getCameraList = (): Promise<{ data: Camera[] }> => {
  return get('/screen/cameras');
};

export const getAlarmTypeDistribution = (): Promise<{ data: ChartData[] }> => {
  return get('/screen/alarm-type-distribution');
};

export const getAlertLevelDistribution = (): Promise<{ data: ChartData[] }> => {
  return get('/screen/alert-level-distribution');
};

export const getHeatmapData = (): Promise<{ data: HeatmapPoint[] }> => {
  return get('/screen/heatmap');
};

export const getAlarmDetail = (id: string): Promise<{ data: Alarm }> => {
  return get(`/screen/alarm/${id}`);
};

export const getAlertDetail = (id: string): Promise<{ data: Alert }> => {
  return get(`/screen/alert/${id}`);
};

export const getVideoUrl = (cameraId: string): Promise<{ data: { url: string } }> => {
  return get(`/screen/video/${cameraId}`);
};

export const handleAlarm = (id: string, status: string): Promise<{ data: null }> => {
  return get(`/screen/alarm/${id}/handle`, { status });
};

export const handleAlert = (id: string, status: string): Promise<{ data: null }> => {
  return get(`/screen/alert/${id}/handle`, { status });
};
