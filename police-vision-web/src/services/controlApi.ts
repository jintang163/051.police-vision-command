import { get, post } from '@/utils/request';
import {
  PersonTrackPoint,
  TrajectoryPredictResult,
  TrajectoryPrediction,
  PredictionAlert,
  PredictionAlertStats,
  SensitiveAreaCheckResult,
  ActivityPattern,
  TargetPerson
} from '@/types';

export const addTrackPoint = (params: {
  personId: string;
  longitude: number;
  latitude: number;
  speed?: number;
  direction?: number;
  sourceType?: string;
  deviceId?: string;
}): Promise<{ data: { added: number } }> => {
  return post('/api/control/track/add', null, params);
};

export const getTrackHistory = (
  personId: string,
  startTime: string,
  endTime: string
): Promise<{ data: PersonTrackPoint[] }> => {
  return get('/api/control/track/history/' + personId, { startTime, endTime });
};

export const getRecentTrack = (
  personId: string,
  days = 30,
  limit = 1000
): Promise<{ data: PersonTrackPoint[] }> => {
  return get('/api/control/track/recent/' + personId, { days, limit });
};

export const getActivityPattern = (
  personId: string,
  days = 90
): Promise<{ data: ActivityPattern }> => {
  return get('/api/control/track/activity-pattern/' + personId, { days });
};

export const predictTrajectory = (
  personId: string
): Promise<{ data: TrajectoryPredictResult }> => {
  return post('/api/control/predict/trajectory/' + personId);
};

export const predictTrajectoryBatch = (
  personIds: string[]
): Promise<{ data: { total: number; success: number; failed: number; results: TrajectoryPredictResult[] } }> => {
  return post('/api/control/predict/trajectory/batch', personIds);
};

export const getLatestPredictions = (
  personId: string,
  limit = 3
): Promise<{ data: TrajectoryPrediction[] }> => {
  return get('/api/control/predict/latest/' + personId, { limit });
};

export const getHighRiskPredictions = (params?: {
  minProbability?: number;
  sensitiveOnly?: number;
  startTime?: string;
  endTime?: string;
}): Promise<{ data: TrajectoryPrediction[] }> => {
  return get('/api/control/predict/high-risk', params);
};

export const generatePredictionAlerts = (
  predictionBatch: string
): Promise<{ data: PredictionAlert[] }> => {
  return post('/api/control/predict-alert/generate/' + predictionBatch);
};

export const getPredictionAlerts = (params?: {
  status?: number;
  personId?: string;
  alertLevel?: number;
  startTime?: string;
  endTime?: string;
  pageNum?: number;
  pageSize?: number;
}): Promise<{ data: { list: PredictionAlert[]; total: number; pageNum: number; pageSize: number } }> => {
  return get('/api/control/predict-alert/page', { pageNum: 1, pageSize: 20, ...params });
};

export const handlePredictionAlert = (params: {
  alertId: string;
  targetStatus?: number;
  statusName?: string;
  remark?: string;
  officerId?: number;
  officerName?: string;
}): Promise<{ data: { handled: PredictionAlert } }> => {
  const { alertId, ...rest } = params;
  return post('/api/control/predict-alert/handle/' + alertId, null, rest);
};

export const autoDispatchPolice = (
  alertId: string
): Promise<{ data: { alertId: string; dispatched: boolean; message: string; policeStationCode?: string } }> => {
  return post('/api/control/predict-alert/auto-dispatch/' + alertId);
};

export const getPredictionAlertStats = (): Promise<{ data: PredictionAlertStats }> => {
  return get('/api/control/predict-alert/stats');
};

export const checkSensitiveArea = (
  lng: number,
  lat: number
): Promise<{ data: SensitiveAreaCheckResult }> => {
  return get('/api/control/sensitive/check', { lng, lat });
};

export const getTargetPersonPage = (params?: {
  personType?: string;
  personName?: string;
  idCardNo?: string;
  controlLevel?: number;
  status?: number;
  pageNum?: number;
  pageSize?: number;
}): Promise<{ data: { records: TargetPerson[]; total: number; size: number; current: number } }> => {
  return get('/api/control/person/page', { pageNum: 1, pageSize: 20, ...params });
};

export const getTargetPersonDetail = (
  personId: string
): Promise<{ data: TargetPerson }> => {
  return get('/api/control/person/' + personId);
};
