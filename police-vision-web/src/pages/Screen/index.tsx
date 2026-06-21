import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Row, Col, Layout, notification, Badge, Spin, Button, Space } from 'antd';
import {
  WarningOutlined,
  BellOutlined,
  ClockCircleOutlined,
  ReloadOutlined,
  SafetyOutlined,
  GlobalOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';

import GisMap, { GisMapRef } from '@/components/GisMap';
import StatsPanel from '@/components/StatsPanel';
import AlarmList from '@/components/AlarmList';
import AlertList from '@/components/AlertList';
import VideoPanel from '@/components/VideoPanel';

import {
  Alarm,
  Alert,
  PoliceForce,
  Camera,
  StatsData,
  ChartData,
  HeatmapPoint,
  WebSocketMessage
} from '@/types';

import {
  getStats,
  getAlarmList,
  getAlertList,
  getPoliceForceList,
  getCameraList,
  getAlarmTypeDistribution,
  getAlertLevelDistribution,
  getHeatmapData,
  handleAlarm as handleAlarmApi,
  handleAlert as handleAlertApi
} from '@/services/api';

import wsService from '@/services/websocket';

const { Header, Content } = Layout;

const Screen: React.FC = () => {
  const mapRef = useRef<GisMapRef>(null);
  const [loading, setLoading] = useState(true);
  const [currentTime, setCurrentTime] = useState(dayjs().format('YYYY-MM-DD HH:mm:ss'));
  const [wsConnected, setWsConnected] = useState(false);

  const [stats, setStats] = useState<StatsData>({
    totalAlarms: 0,
    pendingAlarms: 0,
    resolvedAlarms: 0,
    totalAlerts: 0,
    unhandledAlerts: 0,
    policeForceCount: 0,
    onlinePoliceCount: 0,
    cameraCount: 0,
    onlineCameraCount: 0
  });

  const [alarms, setAlarms] = useState<Alarm[]>([]);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [policeForces, setPoliceForces] = useState<PoliceForce[]>([]);
  const [cameras, setCameras] = useState<Camera[]>([]);
  const [alarmTypeData, setAlarmTypeData] = useState<ChartData[]>([]);
  const [alertLevelData, setAlertLevelData] = useState<ChartData[]>([]);
  const [heatmapData, setHeatmapData] = useState<HeatmapPoint[]>([]);

  const refreshInterval = useRef<ReturnType<typeof setInterval> | null>(null);

  const loadData = useCallback(async () => {
    try {
      const [
        statsRes,
        alarmsRes,
        alertsRes,
        policeRes,
        camerasRes,
        alarmTypeRes,
        alertLevelRes,
        heatmapRes
      ] = await Promise.all([
        getStats(),
        getAlarmList({ size: 20 }),
        getAlertList({ size: 20 }),
        getPoliceForceList(),
        getCameraList(),
        getAlarmTypeDistribution(),
        getAlertLevelDistribution(),
        getHeatmapData()
      ]);

      setStats(statsRes.data);
      setAlarms(alarmsRes.data);
      setAlerts(alertsRes.data);
      setPoliceForces(policeRes.data);
      setCameras(camerasRes.data);
      setAlarmTypeData(alarmTypeRes.data);
      setAlertLevelData(alertLevelRes.data);
      setHeatmapData(heatmapRes.data);
    } catch (error) {
      console.error('Load data error:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();

    refreshInterval.current = setInterval(() => {
      loadData();
    }, Number(import.meta.env.VITE_REFRESH_INTERVAL) || 5000);

    return () => {
      if (refreshInterval.current) {
        clearInterval(refreshInterval.current);
      }
    };
  }, [loadData]);

  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentTime(dayjs().format('YYYY-MM-DD HH:mm:ss'));
    }, 1000);

    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    wsService.connect();

    const handleAlarmMessage = (message: WebSocketMessage) => {
      const alarm = message.data as Alarm;
      setAlarms(prev => [alarm, ...prev.slice(0, 19)]);

      setStats(prev => ({
        ...prev,
        totalAlarms: prev.totalAlarms + 1,
        pendingAlarms: prev.pendingAlarms + 1
      }));

      if (mapRef.current) {
        mapRef.current.locateToPosition(alarm.position.lat, alarm.position.lng, 16);
        setTimeout(() => {
          mapRef.current?.showAlarmPopup(alarm);
        }, 1000);
      }

      notification.open({
        message: '新警情告警',
        description: `${alarm.levelName} - ${alarm.title}`,
        icon: <WarningOutlined style={{ color: '#ff4d4f' }} />,
        duration: 5,
        placement: 'topRight',
        style: {
          background: '#1a1f35',
          border: '1px solid #ff4d4f',
          color: '#e6f1ff'
        }
      });
    };

    const handleAlertMessage = (message: WebSocketMessage) => {
      const alert = message.data as Alert;
      setAlerts(prev => [alert, ...prev.slice(0, 19)]);

      setStats(prev => ({
        ...prev,
        totalAlerts: prev.totalAlerts + 1,
        unhandledAlerts: prev.unhandledAlerts + 1
      }));

      notification.open({
        message: 'AI智能告警',
        description: `${alert.levelName} - ${alert.title}`,
        icon: <BellOutlined style={{ color: '#722ed1' }} />,
        duration: 3,
        placement: 'topRight',
        style: {
          background: '#1a1f35',
          border: '1px solid #722ed1',
          color: '#e6f1ff'
        }
      });
    };

    const handleStatsMessage = (message: WebSocketMessage) => {
      setStats(message.data as StatsData);
    };

    const handlePoliceMessage = (message: WebSocketMessage) => {
      const police = message.data as PoliceForce;
      setPoliceForces(prev => prev.map(p =>
        p.id === police.id ? police : p
      ));
    };

    wsService.on('alarm', handleAlarmMessage);
    wsService.on('alert', handleAlertMessage);
    wsService.on('stats', handleStatsMessage);
    wsService.on('police', handlePoliceMessage);

    const checkConnection = setInterval(() => {
      setWsConnected(wsService.isConnected());
    }, 1000);

    return () => {
      wsService.off('alarm', handleAlarmMessage);
      wsService.off('alert', handleAlertMessage);
      wsService.off('stats', handleStatsMessage);
      wsService.off('police', handlePoliceMessage);
      wsService.close();
      clearInterval(checkConnection);
    };
  }, []);

  const handleAlarmClick = useCallback((alarm: Alarm) => {
    if (mapRef.current) {
      mapRef.current.locateToPosition(alarm.position.lat, alarm.position.lng, 16);
      mapRef.current.showAlarmPopup(alarm);
    }
  }, []);

  const handlePoliceClick = useCallback((police: PoliceForce) => {
    if (mapRef.current) {
      mapRef.current.locateToPosition(police.position.lat, police.position.lng, 15);
    }
  }, []);

  const handleCameraClick = useCallback((camera: Camera) => {
    if (mapRef.current) {
      mapRef.current.locateToPosition(camera.position.lat, camera.position.lng, 15);
    }
  }, []);

  const handleViewAlarmDetail = useCallback((alarm: Alarm) => {
    handleAlarmClick(alarm);
  }, [handleAlarmClick]);

  const handleViewAlertDetail = useCallback((alert: Alert) => {
    if (mapRef.current) {
      mapRef.current.locateToPosition(alert.position.lat, alert.position.lng, 16);
    }
  }, []);

  const handleAlarm = useCallback(async (alarm: Alarm, status: string) => {
    try {
      await handleAlarmApi(alarm.id, status);
      setAlarms(prev => prev.map(a =>
        a.id === alarm.id ? { ...a, status: status as any } : a
      ));

      if (status === 'processing') {
        setStats(prev => ({
          ...prev,
          pendingAlarms: prev.pendingAlarms - 1
        }));
      } else if (status === 'resolved') {
        setStats(prev => ({
          ...prev,
          pendingAlarms: prev.pendingAlarms > 0 ? prev.pendingAlarms - 1 : 0,
          resolvedAlarms: prev.resolvedAlarms + 1
        }));
      }
    } catch (error) {
      console.error('Handle alarm error:', error);
    }
  }, []);

  const handleAlert = useCallback(async (alert: Alert, status: string) => {
    try {
      await handleAlertApi(alert.id, status);
      setAlerts(prev => prev.map(a =>
        a.id === alert.id ? { ...a, status: status as any } : a
      ));

      if (status !== 'unhandled') {
        setStats(prev => ({
          ...prev,
          unhandledAlerts: prev.unhandledAlerts > 0 ? prev.unhandledAlerts - 1 : 0
        }));
      }
    } catch (error) {
      console.error('Handle alert error:', error);
    }
  }, []);

  if (loading) {
    return (
      <div style={{
        width: '100%',
        height: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#0a0e1a'
      }}>
        <Space direction="vertical" align="center" size={16}>
          <Spin size="large" tip="系统加载中..." style={{ color: '#1890ff' }} />
          <p style={{ color: '#8c9cb8' }}>公安智慧指挥大屏正在初始化...</p>
        </Space>
      </div>
    );
  }

  return (
    <Layout style={{ width: '100%', height: '100vh', background: '#0a0e1a' }}>
      <Header
        style={{
          background: 'linear-gradient(90deg, #0a0e1a 0%, #141829 50%, #0a0e1a 100%)',
          borderBottom: '1px solid #1f2940',
          height: 60,
          padding: '0 24px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <div style={{
            width: 40,
            height: 40,
            borderRadius: '50%',
            background: 'linear-gradient(135deg, #1890ff 0%, #00d4ff 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            <SafetyOutlined style={{ fontSize: 20, color: '#fff' }} />
          </div>
          <div>
            <h1 style={{
              margin: 0,
              fontSize: 20,
              fontWeight: 600,
              color: '#e6f1ff',
              background: 'linear-gradient(90deg, #1890ff, #00d4ff)',
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent'
            }}>
              公安智慧指挥大屏
            </h1>
            <p style={{ margin: 0, fontSize: 11, color: '#8c9cb8' }}>
              PUBLIC SECURITY COMMAND CENTER
            </p>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
          <Space size={16}>
            <Badge status={wsConnected ? 'success' : 'error'} text={wsConnected ? 'WebSocket连接正常' : 'WebSocket断开'} />
            <Space size={4}>
              <GlobalOutlined style={{ color: '#1890ff' }} />
              <span style={{ color: '#8c9cb8', fontSize: 12 }}>
                在线警力: {stats.onlinePoliceCount}/{stats.policeForceCount}
              </span>
            </Space>
            <Space size={4}>
              <WarningOutlined style={{ color: '#ff4d4f' }} />
              <span style={{ color: '#8c9cb8', fontSize: 12 }}>
                待处理警情: {stats.pendingAlarms}
              </span>
            </Space>
            <Space size={4}>
              <BellOutlined style={{ color: '#722ed1' }} />
              <span style={{ color: '#8c9cb8', fontSize: 12 }}>
                未处理告警: {stats.unhandledAlerts}
              </span>
            </Space>
          </Space>

          <div style={{
            padding: '8px 16px',
            background: 'rgba(24, 144, 255, 0.1)',
            borderRadius: 4,
            border: '1px solid rgba(24, 144, 255, 0.3)'
          }}>
            <Space size={8}>
              <ClockCircleOutlined style={{ color: '#1890ff' }} />
              <span style={{ color: '#e6f1ff', fontSize: 14, fontWeight: 500, fontFamily: 'monospace' }}>
                {currentTime}
              </span>
            </Space>
          </div>

          <Button
            type="primary"
            size="small"
            icon={<ReloadOutlined />}
            onClick={loadData}
          >
            刷新数据
          </Button>
        </div>
      </Header>

      <Content style={{ padding: 12, height: 'calc(100vh - 60px)' }}>
        <Row gutter={[12, 12]} style={{ height: '100%' }}>
          <Col span={5} style={{ height: '100%', display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ flex: 1, minHeight: 0 }}>
              <StatsPanel
                stats={stats}
                alarmTypeData={alarmTypeData}
                alertLevelData={alertLevelData}
              />
            </div>
            <div style={{ flex: 1, minHeight: 0 }}>
              <AlarmList
                alarms={alarms}
                onAlarmClick={handleAlarmClick}
                onViewDetail={handleViewAlarmDetail}
              />
            </div>
          </Col>

          <Col span={14} style={{ height: '100%' }}>
            <div className="tech-card" style={{ height: '100%', padding: 8, position: 'relative' }}>
              <div className="corner-decoration corner-tl" />
              <div className="corner-decoration corner-tr" />
              <div className="corner-decoration corner-bl" />
              <div className="corner-decoration corner-br" />

              <div className="panel-header">
                <span className="panel-title">GIS地理信息指挥平台</span>
                <Space>
                  <Badge count={policeForces.length} color="#1890ff" offset={[-2, 0]}>
                    <span style={{ color: '#8c9cb8', fontSize: 12 }}>警力</span>
                  </Badge>
                  <Badge count={alarms.filter(a => a.status === 'pending').length} color="#ff4d4f" offset={[-2, 0]}>
                    <span style={{ color: '#8c9cb8', fontSize: 12 }}>警情</span>
                  </Badge>
                  <Badge count={alerts.filter(a => a.status === 'unhandled').length} color="#722ed1" offset={[-2, 0]}>
                    <span style={{ color: '#8c9cb8', fontSize: 12 }}>告警</span>
                  </Badge>
                </Space>
              </div>

              <div style={{ height: 'calc(100% - 56px)', borderRadius: 4, overflow: 'hidden' }}>
                <GisMap
                  ref={mapRef}
                  policeForces={policeForces}
                  alarms={alarms}
                  alerts={alerts}
                  cameras={cameras}
                  heatmapData={heatmapData}
                  onAlarmClick={handleAlarmClick}
                  onPoliceClick={handlePoliceClick}
                  onCameraClick={handleCameraClick}
                />
              </div>
            </div>
          </Col>

          <Col span={5} style={{ height: '100%', display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ flex: 1, minHeight: 0 }}>
              <AlertList
                alerts={alerts}
                onViewDetail={handleViewAlertDetail}
                onHandle={handleAlert}
              />
            </div>
            <div style={{ flex: 1, minHeight: 0 }}>
              <VideoPanel cameras={cameras} />
            </div>
          </Col>
        </Row>
      </Content>
    </Layout>
  );
};

export default Screen;
