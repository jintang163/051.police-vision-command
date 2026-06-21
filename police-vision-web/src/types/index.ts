export interface Position {
  lat: number;
  lng: number;
}

export interface PoliceForce {
  id: string;
  name: string;
  type: 'patrol' | 'station' | 'police_car';
  position: Position;
  status: 'online' | 'offline' | 'busy';
  contact?: string;
}

export interface Alarm {
  id: string;
  title: string;
  type: 'theft' | 'fight' | 'traffic' | 'fire' | 'other';
  typeName: string;
  level: 'high' | 'medium' | 'low';
  levelName: string;
  position: Position;
  address: string;
  description: string;
  reportTime: string;
  status: 'pending' | 'processing' | 'resolved';
  reporter?: string;
  reporterPhone?: string;
}

export interface Alert {
  id: string;
  title: string;
  type: 'intrusion' | 'abnormal_behavior' | 'crowd_gathering' | 'vehicle_abnormal' | 'fire_detection';
  typeName: string;
  level: 'high' | 'medium' | 'low';
  levelName: string;
  cameraId: string;
  cameraName: string;
  position: Position;
  captureTime: string;
  snapshot?: string;
  confidence: number;
  status: 'unhandled' | 'handled' | 'false_alarm';
}

export interface Camera {
  id: string;
  name: string;
  position: Position;
  address: string;
  status: 'online' | 'offline';
  rtspUrl?: string;
  previewUrl?: string;
}

export interface StatsData {
  totalAlarms: number;
  pendingAlarms: number;
  resolvedAlarms: number;
  totalAlerts: number;
  unhandledAlerts: number;
  policeForceCount: number;
  onlinePoliceCount: number;
  cameraCount: number;
  onlineCameraCount: number;
}

export interface ChartData {
  name: string;
  value: number;
}

export interface AlarmTypeDistribution {
  type: string;
  count: number;
}

export interface AlertLevelDistribution {
  level: string;
  count: number;
}

export interface HeatmapPoint {
  lat: number;
  lng: number;
  count: number;
}

export interface WebSocketMessage {
  type: 'alarm' | 'alert' | 'stats' | 'police' | 'heartbeat';
  data: Alarm | Alert | StatsData | PoliceForce;
  timestamp: string;
}

export interface MapLayer {
  id: string;
  name: string;
  visible: boolean;
}

export interface VideoChannel {
  id: string;
  name: string;
  url: string;
  status: 'playing' | 'paused' | 'loading' | 'error';
}
