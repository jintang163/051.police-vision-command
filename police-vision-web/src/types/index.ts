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

export interface EmergencyPlanTemplate {
  code: string;
  name: string;
  priority: number;
  description: string;
  nacosConfigKey: string;
}

export interface EmergencyPlanStartResult {
  planId: string;
  planName: string;
  eventId: string;
  eventName: string;
  startTime: string;
  policeCount: number;
  cameraCount: number;
  supplyCount: number;
  resourceRadius: number;
  videoRoomId?: string;
  videoRoomUrl?: string;
  fallback?: boolean;
  message?: string;
  templateCode?: string;
  nacosConfigKey?: string;
  configSource?: string;
  planSteps?: PlanStep[];
  requiredResources?: PlanRequiredResources;
  commandTemplates?: any[];
}

export interface EmergencyPlanStartDTO {
  eventId: string;
  planId?: string;
  templateCode?: string;
  resourceRadius?: number;
  autoAllocateResources?: boolean;
  autoStartVideoConference?: boolean;
  operatorName?: string;
  operatorId?: string;
}

export interface EmergencyCommand {
  id: string;
  commandNo: string;
  eventId: string;
  planId?: string;
  commandTitle: string;
  commandContent: string;
  priority: 1 | 2 | 3 | 4;
  status: 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7;
  senderId?: string;
  senderName?: string;
  receiverDeptIds?: string;
  receiverNames?: string;
  dispatchTime?: string;
  receiveTime?: string;
  executeStartTime?: string;
  feedbackTime?: string;
  completeTime?: string;
  feedbackContent?: string;
  feedbackAttachments?: string;
  deadlineMinutes: number;
  timeoutCount: number;
  remark?: string;
  parentCommandId?: string;
  createTime?: string;
  updateTime?: string;
}

export interface EmergencyCommandCreateDTO {
  eventId: string;
  planId?: string;
  commandTitle: string;
  commandContent: string;
  priority?: number;
  deadlineMinutes?: number;
  senderName?: string;
  senderId?: string;
  receiverDeptIds?: string[];
  receiverNames?: string[];
  remark?: string;
}

export interface EmergencyCommandFeedbackDTO {
  commandId: string;
  operatorId: string;
  operatorName?: string;
  operatorDept?: string;
  feedbackContent?: string;
  feedbackAttachments?: string;
  toStatus?: number;
  operateRemark?: string;
  extraData?: Record<string, any>;
}

export interface CommandStatusLog {
  id: string;
  commandId: string;
  fromStatus?: number;
  toStatus: number;
  operatorId?: string;
  operatorName?: string;
  operatorDept?: string;
  operateTime: string;
  operateRemark?: string;
  extraData?: string;
}

export interface ValidNextStatus {
  code: number;
  name: string;
  description: string;
}

export interface EmergencyCommandDetail {
  command: EmergencyCommand;
  statusLogs: CommandStatusLog[];
  validNextStatuses: ValidNextStatus[];
  isFinalStatus: boolean;
  isActiveStatus: boolean;
}

export interface EmergencyResourceQueryDTO {
  eventId: string;
  lng?: number;
  lat?: number;
  radiusMeters?: number;
  resourceType?: 'police' | 'camera' | 'supply';
}

export interface EmergencyResourceResult {
  centerLng: number;
  centerLat: number;
  radiusMeters: number;
  policeList?: EmergencyResourcePolice[];
  policeCount?: number;
  cameraList?: EmergencyResourceCamera[];
  cameraCount?: number;
  supplyList?: EmergencySupply[];
  supplyCount?: number;
}

export interface EmergencyResourcePolice {
  policeId: string;
  name: string;
  policeNo?: string;
  dept?: string;
  status?: number;
  lng: number;
  lat: number;
  phone?: string;
  deviceId?: string;
  distanceMeters: number;
}

export interface EmergencyResourceCamera {
  cameraId: string;
  name: string;
  deviceNo?: string;
  status?: number;
  lng: number;
  lat: number;
  address?: string;
  rtspUrl?: string;
  distanceMeters: number;
}

export interface EmergencySupply {
  id: string;
  eventId: string;
  supplyName: string;
  supplyType: string;
  quantity: number;
  unit: string;
  lng: number;
  lat: number;
  address?: string;
  contactPerson?: string;
  contactPhone?: string;
  status: number;
  distanceMeters?: number;
  description?: string;
}

export interface EmergencyFence {
  id: string;
  eventId: string;
  fenceName: string;
  fenceType: 'blockade' | 'control' | 'prevention' | 'assembly' | 'checkpoint';
  fenceGeometry: string;
  centerLng?: number;
  centerLat?: number;
  radiusMeters?: number;
  fillColor?: string;
  strokeColor?: string;
  strokeWeight?: number;
  opacity?: number;
  sortOrder?: number;
  creatorId?: string;
  creatorName?: string;
  status: number;
  description?: string;
  createTime?: string;
  updateTime?: string;
}

export interface EmergencyFenceCreateDTO {
  eventId: string;
  fenceName: string;
  fenceType?: string;
  fenceGeometry: string;
  centerLng?: number;
  centerLat?: number;
  radiusMeters?: number;
  fillColor?: string;
  strokeColor?: string;
  strokeWeight?: number;
  opacity?: number;
  sortOrder?: number;
  creatorId?: string;
  creatorName?: string;
  description?: string;
}

export interface WebrtcRoomJoinDTO {
  eventId: string;
  userId: string;
  userName?: string;
  userRole?: string;
  enableVideo?: boolean;
  enableAudio?: boolean;
}

export interface WebrtcRoomJoinResult {
  roomId: string;
  roomInfo: WebrtcRoomInfo;
  participants: WebrtcParticipant[];
  selfInfo: WebrtcParticipant;
  token: string;
  signalServerUrl: string;
}

export interface WebrtcRoomInfo {
  roomId: string;
  eventId: string;
  eventName: string;
  createTime: number;
  creatorId: string;
  creatorName: string;
  maxParticipants: number;
  status: string;
}

export interface WebrtcParticipant {
  userId: string;
  userName: string;
  userRole: string;
  enableVideo: boolean;
  enableAudio: boolean;
  joinTime: number;
  isMuted: boolean;
  isVideoOff: boolean;
  isHandRaised: boolean;
}

export interface WebrtcSignalDTO {
  roomId: string;
  userId: string;
  userName?: string;
  signalType: 'offer' | 'answer' | 'ice_candidate' | 'bye';
  fromUserId?: string;
  toUserId?: string;
  data?: Record<string, any>;
  eventId?: string;
}

export interface WebrtcActiveRoom {
  roomId: string;
  eventId: string;
  eventName: string;
  createTime: number;
  creatorId: string;
  creatorName: string;
  participantCount: number;
  maxParticipants: number;
  status: string;
}

export interface CommandTimelineItem {
  key: string;
  status: number;
  statusText: string;
  statusColor: string;
  time: string;
  operatorName?: string;
  operatorDept?: string;
  remark?: string;
  dotColor: string;
}

export interface MediaServerInfo {
  sfuType: string;
  sfuHost: string;
  sfuWsPort: number;
  sfuHttpPort: number;
  rtcAppName: string;
  enableSfu: boolean;
  maxBitrateKbps: number;
  defaultResolution: string;
  wsSignalUrl: string;
  httpApiUrl: string;
}

export interface PublisherInfo {
  roomId: string;
  userId: string;
  streamType: string;
  streamKey: string;
  publishUrl: string;
  webrtcPublishUrl: string;
  srtUrl: string;
  bitrateKbps: number;
  resolution: string;
  sfuType: string;
  enableSfu: boolean;
}

export interface PlayerInfo {
  roomId: string;
  userId: string;
  streamType: string;
  streamKey: string;
  webrtcPlayUrl: string;
  httpFlvPlayUrl: string;
  wsFlvPlayUrl: string;
  hlsPlayUrl: string;
  rtmpPlayUrl: string;
  sfuType: string;
  enableSfu: boolean;
}

export interface WebrtcRoomMediaInfo {
  mediaServer: MediaServerInfo;
  roomStats: Record<string, any>;
  streams: any[];
}

export interface WebrtcSignalFullDTO {
  roomId: string;
  userId: string;
  userName?: string;
  signalType: 'offer' | 'answer' | 'ice_candidate' | 'bye';
  fromUserId?: string;
  fromUserName?: string;
  toUserId?: string;
  toUserName?: string;
  data?: Record<string, any>;
  eventId?: string;
  sdp?: string;
  sdpType?: 'offer' | 'answer';
  candidate?: string;
  sdpMid?: string;
  sdpMLineIndex?: number;
  transId?: string;
  enableAudio?: boolean;
  enableVideo?: boolean;
  isHandRaised?: boolean;
  streamType?: string;
  action?: string;
}

export interface WebrtcSignalResult {
  success: boolean;
  signalType: string;
  fromUserId: string;
  toUserId: string;
  transId?: string;
  timestamp: number;
}

export interface CommandReceiptDTO {
  commandId: string;
  commandNo?: string;
  receiptType: 'RECEIVE' | 'EXECUTE' | 'FEEDBACK' | 'COMPLETE' | 'TIMEOUT' | 'CANCEL';
  operatorId?: string;
  operatorName?: string;
  operatorDept?: string;
  deviceId?: string;
  appVersion?: string;
  feedbackContent?: string;
  feedbackAttachments?: string[];
  lng?: number;
  lat?: number;
  locationDesc?: string;
  timestamp?: number;
  extraData?: string;
}

export interface CommandReceiptResult {
  commandId: string;
  commandNo: string;
  receiptType: string;
  processed: boolean;
  processTime: number;
  oldStatus?: number;
  newStatus?: number;
  success?: boolean;
  skipped?: boolean;
  reason?: string;
  warning?: string;
}

export interface PlanStep {
  stepNo: number;
  stepName: string;
  description: string;
  responsibleDept: string;
  deadlineMinutes: number;
  required: boolean;
}

export interface PlanRequiredResources {
  policeCount: number;
  cameraCount: number;
  supplyTypes?: string[];
  specialEquipments?: string[];
  radiusMeters: number;
}

export interface PlanConfig {
  templateCode: string;
  steps: PlanStep[];
  requiredResources: PlanRequiredResources;
  commandTemplates: any[];
}
