import { get, post, put, del } from '@/utils/request';
import {
  SecEvent,
  EventCreateDTO,
  EventUpdateDTO,
  EventQueryDTO,
  EventPageResult,
  EventResource,
  ResourcePolice,
  ResourceCamera,
  ResourceAllocateResult,
  AreaQueryDTO,
  SecurityPlan,
  SecurityPlanCreateDTO,
  SecRoute,
  RouteCreateDTO,
  RouteCamera,
  RoutePatrolDTO,
  PatrolStatus,
  SecPass,
  PassCreateDTO,
  PassVerifyResult,
  TrafficAlert,
  TrafficStats,
  TrafficMonitorConfig,
  TrafficMonitorDTO,
  EventReport,
  ReportGenerateDTO,
  Camera
} from '@/types';

export const getEventList = (params?: EventQueryDTO): Promise<{ data: EventPageResult }> => {
  return get('/api/event/list', params);
};

export const getEventDetail = (id: string): Promise<{ data: SecEvent }> => {
  return get(`/api/event/${id}`);
};

export const createEvent = (data: EventCreateDTO): Promise<{ data: SecEvent }> => {
  return post('/api/event/create', data);
};

export const updateEvent = (data: EventUpdateDTO): Promise<{ data: null }> => {
  return put('/api/event/update', data);
};

export const deleteEvent = (id: string): Promise<{ data: null }> => {
  return del(`/api/event/${id}`);
};

export const startEvent = (id: string): Promise<{ data: null }> => {
  return post(`/api/event/start/${id}`);
};

export const endEvent = (id: string): Promise<{ data: null }> => {
  return post(`/api/event/end/${id}`);
};

export const getPoliceResources = (eventId: string): Promise<{ data: EventResource[] }> => {
  return get(`/api/event/resource/police/${eventId}`);
};

export const getCameraResources = (eventId: string): Promise<{ data: EventResource[] }> => {
  return get(`/api/event/resource/camera/${eventId}`);
};

export const getEventCameraList = (eventId: string): Promise<{ data: Camera[] }> => {
  return get(`/api/event/resource/camera/${eventId}`);
};

export const allocateResources = (data: AreaQueryDTO): Promise<{ data: number }> => {
  return post('/api/event/resource/allocate', data);
};

export const getSecurityPlanList = (
  eventId: string,
  params?: { status?: number; page?: number; size?: number }
): Promise<{ data: { list: SecurityPlan[]; total: number } }> => {
  return get('/api/event/plan/list', { eventId, ...params });
};

export const getSecurityPlanDetail = (planId: string): Promise<{ data: SecurityPlan }> => {
  return get(`/api/event/plan/${planId}`);
};

export const createSecurityPlan = (data: SecurityPlanCreateDTO): Promise<{ data: SecurityPlan }> => {
  return post('/api/event/plan/create', data);
};

export const updateSecurityPlan = (planId: string, data: SecurityPlanCreateDTO): Promise<{ data: SecurityPlan }> => {
  return put(`/api/event/plan/${planId}`, data);
};

export const deleteSecurityPlan = (planId: string): Promise<{ data: null }> => {
  return del(`/api/event/plan/${planId}`);
};

export const publishSecurityPlan = (planId: string): Promise<{ data: null }> => {
  return post(`/api/event/plan/publish/${planId}`);
};

export const executeSecurityPlan = (planId: string): Promise<{ data: null }> => {
  return post(`/api/event/plan/execute/${planId}`);
};

export const archiveSecurityPlan = (planId: string): Promise<{ data: null }> => {
  return post(`/api/event/plan/archive/${planId}`);
};

export const getRouteList = (
  eventId: string,
  params?: { page?: number; size?: number }
): Promise<{ data: SecRoute[]; total: number }> => {
  return get('/api/event/route/list', { eventId, ...params });
};

export const getRouteDetail = (routeId: string): Promise<{ data: SecRoute }> => {
  return get(`/api/event/route/${routeId}`);
};

export const createRoute = (data: RouteCreateDTO): Promise<{ data: SecRoute }> => {
  return post('/api/event/route/create', data);
};

export const deleteRoute = (routeId: string): Promise<{ data: null }> => {
  return del(`/api/event/route/${routeId}`);
};

export const startPatrol = (routeId: string): Promise<{ data: RoutePatrolDTO }> => {
  return post(`/api/event/route/patrol/start/${routeId}`);
};

export const pausePatrol = (routeId: string): Promise<{ data: RoutePatrolDTO }> => {
  return post(`/api/event/route/patrol/pause/${routeId}`);
};

export const resumePatrol = (routeId: string): Promise<{ data: RoutePatrolDTO }> => {
  return post(`/api/event/route/patrol/resume/${routeId}`);
};

export const stopPatrol = (routeId: string): Promise<{ data: RoutePatrolDTO }> => {
  return post(`/api/event/route/patrol/stop/${routeId}`);
};

export const getPatrolStatus = (routeId: string): Promise<{ data: PatrolStatus }> => {
  return get(`/api/event/route/patrol/status/${routeId}`);
};

export const getRouteCameras = (routeId: string): Promise<{ data: RouteCamera[] }> => {
  return get(`/api/event/route/patrol/cameras/${routeId}`);
};

export const getPassList = (
  eventId: string,
  params?: { page?: number; size?: number; holderName?: string; status?: string }
): Promise<{ data: SecPass[]; total: number }> => {
  return get('/api/event/pass/list', { eventId, ...params });
};

export const getPassDetail = (passId: string): Promise<{ data: SecPass }> => {
  return get(`/api/event/pass/${passId}`);
};

export const generatePass = (data: PassCreateDTO): Promise<{ data: SecPass }> => {
  return post('/api/event/pass/generate', data);
};

export const batchGeneratePass = (data: PassCreateDTO[]): Promise<{ data: SecPass[] }> => {
  return post('/api/event/pass/batch-generate', data);
};

export const verifyPass = (passNo: string): Promise<{ data: PassVerifyResult }> => {
  return post('/api/event/pass/verify', { passNo });
};

export const verifyPassByQrcode = (token: string): Promise<{ data: PassVerifyResult }> => {
  return post('/api/event/pass/verify/qrcode', { qrCode: token });
};

export const revokePass = (passId: string): Promise<{ data: null }> => {
  return post(`/api/event/pass/revoke/${passId}`);
};

export const downloadPassQrcode = (passId: string): string => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8085';
  return `${baseUrl}/api/event/pass/qrcode/download/${passId}`;
};

export const getTrafficAlerts = (
  eventId: string,
  params?: { page?: number; size?: number; alertType?: string; handled?: number; alertLevel?: number }
): Promise<{ data: TrafficAlert[]; total: number }> => {
  return get('/api/event/alert/list', { eventId, ...params });
};

export const handleTrafficAlert = (alertId: string, handleRemark?: string): Promise<{ data: null }> => {
  return post(`/api/event/alert/handle/${alertId}`, { handleRemark });
};

export const getTrafficStats = (eventId: string): Promise<{ data: TrafficStats }> => {
  return get(`/api/event/alert/stats/${eventId}`);
};

export const startTrafficMonitor = (data: TrafficMonitorDTO): Promise<{ data: string }> => {
  return post('/api/event/alert/monitor/start', data);
};

export const stopTrafficMonitor = (eventId: string): Promise<{ data: null }> => {
  return post(`/api/event/alert/monitor/stop/${eventId}`);
};

export const getTrafficMonitorStatus = (eventId: string): Promise<{ data: { running: boolean; config: TrafficMonitorConfig } }> => {
  return get(`/api/event/alert/monitor/status/${eventId}`);
};

export const getReportList = (
  eventId: string,
  params?: { page?: number; size?: number }
): Promise<{ data: EventReport[]; total: number }> => {
  return get('/api/event/report/list', { eventId, ...params });
};

export const getReportDetail = (reportId: string): Promise<{ data: EventReport }> => {
  return get(`/api/event/report/${reportId}`);
};

export const generateReport = (data: ReportGenerateDTO): Promise<{ data: EventReport }> => {
  return post('/api/event/report/generate', data);
};

export const downloadReport = (reportId: string): string => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8085';
  return `${baseUrl}/api/event/report/download/${reportId}`;
};
