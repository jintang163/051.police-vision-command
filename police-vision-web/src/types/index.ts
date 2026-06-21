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

export interface DashboardOverview {
  gis: {
    policeForces: PoliceForce[];
    heatmapData: HeatmapPoint[];
    cameras: Camera[];
  };
  alarm: {
    alarmTypeDistribution: ChartData[];
    alertLevelDistribution: ChartData[];
    recentAlarms: Alarm[];
    pendingAlarmCount: number;
  };
  video: {
    onlineCameraCount: number;
    totalCameraCount: number;
    recentAlerts: Alert[];
  };
  stats: StatsData;
}

export interface WebSocketMessage {
  type: 'alarm' | 'alert' | 'stats' | 'police' | 'heartbeat' | 'new_alarm' | 'video_alert' | 'real_time_stats' | 'police_location' | 'dispatch_order' | 'new_dispatch';
  data: Alarm | Alert | StatsData | PoliceForce | any;
  timestamp: number;
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

export interface SecEvent {
  id: string;
  eventName: string;
  eventType: string;
  eventLevel: string;
  startTime: string;
  endTime: string;
  organizer: string;
  description: string;
  areaPolygon: string;
  status: number;
  createTime?: string;
}

export interface EventCreateDTO {
  eventName: string;
  eventType: string;
  eventLevel: string;
  startTime: string;
  endTime: string;
  organizer?: string;
  description?: string;
  areaPolygon?: string;
}

export interface EventUpdateDTO {
  id: string;
  eventName?: string;
  eventType?: string;
  eventLevel?: string;
  startTime?: string;
  endTime?: string;
  organizer?: string;
  description?: string;
  areaPolygon?: string;
}

export interface EventQueryDTO {
  keyword?: string;
  status?: number;
  page?: number;
  size?: number;
}

export interface EventPageResult {
  list: SecEvent[];
  total: number;
}

export { SecEvent as Event };

export interface EventResource {
  id: string;
  eventId: string;
  resourceType: string;
  resourceId: string;
  resourceName: string;
  lng: number;
  lat: number;
  distance: number;
}

export interface ResourcePolice {
  id: string;
  name: string;
  policeNo: string;
  avatar?: string;
  position: Position;
  distance: number;
  status: 'online' | 'offline' | 'busy';
  dept?: string;
  phone?: string;
  allocated?: boolean;
}

export interface ResourceCamera {
  id: string;
  name: string;
  deviceNo: string;
  position: Position;
  distance: number;
  status: 'online' | 'offline';
  address?: string;
  allocated?: boolean;
}

export interface ResourceAllocateResult {
  policeCount: number;
  cameraCount: number;
  totalAllocated: number;
}

export interface AreaQueryDTO {
  eventId: string;
  radius?: number;
}

export interface SecurityPlan {
  id: string;
  eventId: string;
  planName: string;
  planType: 'route' | 'point' | 'mixed';
  planTypeName: string;
  status: 0 | 1 | 2 | 3;
  statusName: string;
  taskGroupCount: number;
  postCount: number;
  createTime: string;
  updateTime: string;
  taskGroups?: TaskGroup[];
  description?: string;
}

export interface TaskGroup {
  id?: string;
  planId?: string;
  groupName: string;
  groupLeader: string;
  groupLeaderId?: string;
  description?: string;
  posts: Post[];
}

export interface Post {
  id?: string;
  planId?: string;
  groupId?: string;
  postName: string;
  postCode?: string;
  postNo?: string;
  dutyContent?: string;
  dutyDescription?: string;
  policeId?: string;
  policeName?: string;
  policeNo?: string;
  policeOfficers?: string[];
  lng?: number;
  lat?: number;
  position?: Position;
}

export interface SecurityPlanCreateDTO {
  eventId: string;
  planName: string;
  planType: 'route' | 'point' | 'mixed';
  taskGroups: TaskGroup[];
  description?: string;
}

export interface SecRoute {
  id: string;
  eventId: string;
  routeName: string;
  startPoint: string;
  endPoint: string;
  waypoints: string;
  status: number;
  createTime?: string;
}

export { SecRoute as Route };

export interface RouteCreateDTO {
  eventId: string;
  routeName: string;
  startPoint: string;
  endPoint: string;
  waypoints?: string;
  autoSelectCamera?: boolean;
  cameraIds?: string[];
  playDuration?: number;
}

export interface RouteCamera {
  id: string;
  routeId: string;
  cameraId: string;
  cameraName: string;
  cameraUrl: string;
  cameraIndex: number;
  playDuration: number;
}

export interface RoutePatrolDTO {
  routeId: string;
  eventId: string;
  patrolTaskId: string;
  cameras: RouteCamera[];
  status: string;
  startTime: string;
}

export interface PatrolStatus {
  status: 'idle' | 'running' | 'paused' | 'stopped';
  currentCameraIndex?: number;
  currentCameraName?: string;
  startTime?: string;
}

export interface SecPass {
  id: string;
  eventId: string;
  passNo: string;
  holderName: string;
  holderIdcard: string;
  holderPhone: string;
  passType: string;
  photoUrl?: string;
  qrCode?: string;
  jwtToken?: string;
  issueTime: string;
  expireTime: string;
  status: number;
  verifyCount: number;
}

export { SecPass as Pass };

export interface PassCreateDTO {
  eventId: string;
  holderName: string;
  holderIdcard: string;
  holderPhone: string;
  passType: string;
  expireDays?: number;
  photoUrl?: string;
}

export interface PassVerifyDTO {
  passNo?: string;
  token?: string;
}

export interface PassVerifyResult {
  success: boolean;
  message: string;
  verifyTime: string;
  pass?: SecPass;
}

export interface TrafficAlert {
  id: string;
  eventId: string;
  alertType: string;
  alertLevel: number;
  location: string;
  lng: number;
  lat: number;
  countValue: number;
  thresholdValue: number;
  alertTime: string;
  handled: number;
  handleRemark?: string;
}

export interface TrafficAlertItem {
  id: string;
  eventId: string;
  alertType: 'pedestrian' | 'vehicle';
  alertTypeName: string;
  level: 1 | 2 | 3;
  levelName: string;
  location: string;
  position: Position;
  currentValue: number;
  thresholdValue: number;
  handleStatus: 'pending' | 'handled';
  handleStatusName: string;
  alertTime: string;
  handleTime?: string;
  handleRemark?: string;
}

export interface TrafficStats {
  pedestrianCount: number;
  vehicleCount: number;
  pedestrianThreshold: number;
  vehicleThreshold: number;
  todayAlertCount: number;
  pendingAlertCount: number;
  highLevelAlertCount: number;
  pedestrianPeak: number;
  vehiclePeak: number;
}

export interface TrafficMonitorConfig {
  pedestrianThreshold: number;
  vehicleThreshold: number;
  windowSize: number;
}

export interface TrafficMonitorDTO {
  eventId: string;
  pedestrianThreshold: number;
  vehicleThreshold: number;
  windowSize: number;
}

export interface EventReport {
  id: string;
  eventId: string;
  reportName: string;
  reportUrl: string;
  generateTime: string;
  summary: string;
  pedestrianCount: number;
  vehicleCount: number;
  alertCount: number;
  policeCount: number;
  cameraCount: number;
  postCount: number;
}

export interface ReportGenerateDTO {
  eventId: string;
  reportName: string;
  summary?: string;
}

export interface LatLngPoint {
  lng: number;
  lat: number;
}

export interface EventArea {
  type: 'polygon' | 'circle';
  center?: Position;
  radius?: number;
  paths?: Position[];
}
