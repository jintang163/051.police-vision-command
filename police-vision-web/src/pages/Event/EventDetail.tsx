import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Tabs,
  Space,
  Tag,
  Descriptions,
  List,
  Avatar,
  Button,
  Spin,
  message
} from 'antd';
import {
  ArrowLeftOutlined,
  InfoCircleOutlined,
  TeamOutlined,
  SafetyCertificateOutlined,
  BellOutlined,
  FileTextOutlined,
  VideoCameraOutlined,
  EnvironmentOutlined,
  ThunderboltOutlined,
  RadarChartOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { SecEvent, PoliceForce, Camera } from '@/types';
import { getEventDetail } from '@/services/eventApi';
import EmergencyCommandPanel from './EmergencyCommandPanel';
import TrajectoryPredictionPanel from './TrajectoryPredictionPanel';

declare global {
  interface Window {
    TMap: any;
  }
}

interface EventDetailProps {
  eventId?: string;
  eventData?: Event;
  onBack?: () => void;
}

const mockPoliceForces: PoliceForce[] = [
  {
    id: '1',
    name: '巡逻一队',
    type: 'patrol',
    position: { lat: 39.9142, lng: 116.4074 },
    status: 'online',
    contact: '张三 13800138001'
  },
  {
    id: '2',
    name: '巡逻二队',
    type: 'patrol',
    position: { lat: 39.8942, lng: 116.4174 },
    status: 'online',
    contact: '李四 13800138002'
  },
  {
    id: '3',
    name: '警务站A',
    type: 'station',
    position: { lat: 39.9042, lng: 116.3974 },
    status: 'online',
    contact: '王五 13800138003'
  },
  {
    id: '4',
    name: '巡逻车01',
    type: 'police_car',
    position: { lat: 39.9092, lng: 116.4274 },
    status: 'busy',
    contact: '赵六 13800138004'
  }
];

const mockCameras: Camera[] = [
  {
    id: 'cam1',
    name: '东门摄像头',
    position: { lat: 39.9062, lng: 116.4094 },
    address: '活动场地东门入口',
    status: 'online'
  },
  {
    id: 'cam2',
    name: '西门摄像头',
    position: { lat: 39.9022, lng: 116.4014 },
    address: '活动场地西门入口',
    status: 'online'
  },
  {
    id: 'cam3',
    name: '主舞台摄像头',
    position: { lat: 39.9042, lng: 116.4074 },
    address: '活动主舞台区域',
    status: 'online'
  },
  {
    id: 'cam4',
    name: '观众区摄像头',
    position: { lat: 39.9052, lng: 116.4054 },
    address: '观众席区域',
    status: 'offline'
  }
];

const statusOptions = [
  { value: 0, label: '筹备中', color: 'default' },
  { value: 1, label: '进行中', color: 'processing' },
  { value: 2, label: '已结束', color: 'success' },
  { value: 3, label: '已取消', color: 'error' }
];

const eventTypeOptions = [
  { value: 'concert', label: '演唱会' },
  { value: 'sports', label: '体育赛事' },
  { value: 'exhibition', label: '展览展会' },
  { value: 'conference', label: '会议论坛' },
  { value: 'festival', label: '节庆活动' },
  { value: 'other', label: '其他活动' }
];

const eventLevelOptions = [
  { value: 'level1', label: '一级（特别重大）' },
  { value: 'level2', label: '二级（重大）' },
  { value: 'level3', label: '三级（较大）' },
  { value: 'level4', label: '四级（一般）' }
];

const EventDetail: React.FC<EventDetailProps> = ({
  eventId,
  eventData: initialEventData,
  onBack
}) => {
  const [eventData, setEventData] = useState<Event | null>(initialEventData || null);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('basic');
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);

  const loadEventDetail = useCallback(async () => {
    if (!eventId || initialEventData) return;

    setLoading(true);
    try {
      const res = await getEventDetail(eventId);
      if (res.data) {
        setEventData(res.data);
      }
    } catch (error) {
      console.error('Load event detail error:', error);
      message.error('加载活动详情失败');
    } finally {
      setLoading(false);
    }
  }, [eventId, initialEventData]);

  useEffect(() => {
    loadEventDetail();
  }, [loadEventDetail]);

  useEffect(() => {
    if (activeTab !== 'resources' || !eventData) return;

    const initMap = () => {
      if (!window.TMap) {
        setTimeout(initMap, 500);
        return;
      }

      if (mapRef.current) return;

      const center = new window.TMap.LatLng(39.9042, 116.4074);

      mapRef.current = new window.TMap.Map(mapContainerRef.current!, {
        center,
        zoom: 14,
        pitch: 0,
        rotation: 0,
        viewMode: '2D',
        mapStyleId: 'style1',
        baseMap: {
          type: 'vector',
          features: ['base', 'road', 'point']
        }
      });

      addEventPolygon();
      addPoliceMarkers();
      addCameraMarkers();
    };

    const addEventPolygon = () => {
      if (!window.TMap || !mapRef.current || !eventData?.polygonPoints?.length) return;

      const path = eventData.polygonPoints.map(
        (p) => new window.TMap.LatLng(p.lat, p.lng)
      );

      new window.TMap.MultiPolygon({
        map: mapRef.current,
        styles: {
          event: new window.TMap.PolygonStyle({
            color: 'rgba(24, 144, 255, 0.2)',
            borderColor: '#1890ff',
            borderWidth: 3,
            borderStyle: 'solid'
          })
        },
        geometries: [
          {
            id: 'event_area',
            styleId: 'event',
            paths: path
          }
        ]
      });
    };

    const addPoliceMarkers = () => {
      if (!window.TMap || !mapRef.current) return;

      const policeMarkers = mockPoliceForces.map((police) => ({
        id: `police_${police.id}`,
        position: new window.TMap.LatLng(police.position.lat, police.position.lng),
        properties: police,
        styles: 'police'
      }));

      new window.TMap.MultiMarker({
        map: mapRef.current,
        styles: {
          police: new window.TMap.MarkerStyle({
            width: 28,
            height: 28,
            anchor: { x: 14, y: 14 },
            src: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyOCIgaGVpZ2h0PSIyOCIgdmlld0JveD0iMCAwIDI4IDI4Ij48Y2lyY2xlIGN4PSIxNCIgY3k9IjE0IiByPSIxMiIgZmlsbD0iIzE4OTBmZiIgc3Ryb2tlPSIjZmZmIiBzdHJva2Utd2lkdGg9IjIiLz48cGF0aCBmaWxsPSIjZmZmIiBkPSJNMTQgNmwzIDZoLTJ2NmgtMnYtNmgtN3oiLz48L3N2Zz4='
          })
        },
        geometries: policeMarkers
      });
    };

    const addCameraMarkers = () => {
      if (!window.TMap || !mapRef.current) return;

      const cameraMarkers = mockCameras.map((camera) => ({
        id: `camera_${camera.id}`,
        position: new window.TMap.LatLng(camera.position.lat, camera.position.lng),
        properties: camera,
        styles: camera.status === 'online' ? 'camera_online' : 'camera_offline'
      }));

      new window.TMap.MultiMarker({
        map: mapRef.current,
        styles: {
          camera_online: new window.TMap.MarkerStyle({
            width: 22,
            height: 22,
            anchor: { x: 11, y: 11 },
            src: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyMiIgaGVpZ2h0PSIyMiIgdmlld0JveD0iMCAwIDIyIDIyIj48cmVjdCB4PSIyIiB5PSI2IiB3aWR0aD0iMTIiIGhlaWdodD0iMTAiIHJ4PSIyIiBmaWxsPSIjNTJjNDFhIiBzdHJva2U9IiNmZmYiIHN0cm9rZS13aWR0aD0iMiIvPjxjaXJjbGUgY3g9IjE3IiBjeT0iMTEiIHI9IjMiIGZpbGw9IiM1MmM0MWEiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIyIi8+PC9zdmc+'
          }),
          camera_offline: new window.TMap.MarkerStyle({
            width: 22,
            height: 22,
            anchor: { x: 11, y: 11 },
            src: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyMiIgaGVpZ2h0PSIyMiIgdmlld0JveD0iMCAwIDIyIDIyIj48cmVjdCB4PSIyIiB5PSI2IiB3aWR0aD0iMTIiIGhlaWdodD0iMTAiIHJ4PSIyIiBmaWxsPSIjOGM5Y2I4IiBzdHJva2U9IiNmZmYiIHN0cm9rZS13aWR0aD0iMiIvPjxjaXJjbGUgY3g9IjE3IiBjeT0iMTEiIHI9IjMiIGZpbGw9IiM4YzljYjgiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIyIi8+PC9zdmc+'
          })
        },
        geometries: cameraMarkers
      });
    };

    initMap();

    return () => {
      if (mapRef.current) {
        mapRef.current.destroy();
        mapRef.current = null;
      }
    };
  }, [activeTab, eventData]);

  const getStatusTag = (status: number) => {
    const option = statusOptions.find(opt => opt.value === status);
    if (option) {
      return <Tag color={option.color}>{option.label}</Tag>;
    }
    return <Tag>未知</Tag>;
  };

  const getTypeName = (type: string) => {
    const option = eventTypeOptions.find(opt => opt.value === type);
    return option ? option.label : type;
  };

  const getLevelName = (level: string) => {
    const option = eventLevelOptions.find(opt => opt.value === level);
    return option ? option.label : level;
  };

  const getLevelColor = (level: string) => {
    switch (level) {
      case 'level1':
        return '#ff4d4f';
      case 'level2':
        return '#faad14';
      case 'level3':
        return '#1890ff';
      case 'level4':
        return '#52c41a';
      default:
        return '#8c9cb8';
    }
  };

  const getPoliceTypeLabel = (type: string) => {
    switch (type) {
      case 'patrol':
        return '巡逻队';
      case 'station':
        return '警务站';
      case 'police_car':
        return '巡逻车';
      default:
        return type;
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'online':
        return '#52c41a';
      case 'offline':
        return '#8c9cb8';
      case 'busy':
        return '#faad14';
      default:
        return '#8c9cb8';
    }
  };

  const getStatusLabel = (status: string) => {
    switch (status) {
      case 'online':
        return '在线';
      case 'offline':
        return '离线';
      case 'busy':
        return '执行任务中';
      default:
        return status;
    }
  };

  const tabItems = [
    {
      key: 'basic',
      label: (
        <span>
          <InfoCircleOutlined />
          基本信息
        </span>
      )
    },
    {
      key: 'resources',
      label: (
        <span>
          <TeamOutlined />
          资源分布
        </span>
      )
    },
    {
      key: 'security',
      label: (
        <span>
          <SafetyCertificateOutlined />
          安保方案
        </span>
      )
    },
    {
      key: 'warning',
      label: (
        <span>
          <BellOutlined />
          预警监控
        </span>
      )
    },
    {
      key: 'report',
      label: (
        <span>
          <FileTextOutlined />
          统计报告
        </span>
      )
    },
    {
      key: 'emergency',
      label: (
        <span>
          <ThunderboltOutlined />
          应急调度
        </span>
      )
    },
    {
      key: 'trajectory',
      label: (
        <span>
          <RadarChartOutlined />
          轨迹预测
        </span>
      )
    }
  ];

  if (loading && !eventData) {
    return (
      <div
        style={{
          width: '100%',
          height: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: '#0a0e1a'
        }}
      >
        <Space direction="vertical" align="center" size={16}>
          <Spin size="large" tip="加载中..." style={{ color: '#1890ff' }} />
        </Space>
      </div>
    );
  }

  return (
    <div
      className="tech-card"
      style={{
        padding: 20,
        minHeight: 'calc(100vh - 40px)',
        margin: 20
      }}
    >
      <div className="corner-decoration corner-tl" />
      <div className="corner-decoration corner-tr" />
      <div className="corner-decoration corner-bl" />
      <div className="corner-decoration corner-br" />

      <div className="panel-header" style={{ marginBottom: 20 }}>
        <Space size={16}>
          {onBack && (
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={onBack}
              style={{ color: '#8c9cb8' }}
            >
              返回
            </Button>
          )}
          <span className="panel-title" style={{ marginBottom: 0 }}>
            {eventData?.name || '活动详情'}
          </span>
          {eventData && getStatusTag(eventData.status)}
        </Space>
      </div>

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={tabItems}
        style={{ color: '#e6f1ff' }}
        size="large"
        items={undefined}
        {...{ items: tabItems }}
      />

      {activeTab === 'basic' && eventData && (
        <div
          style={{
            background: 'rgba(26, 31, 53, 0.6)',
            border: '1px solid rgba(24, 144, 255, 0.15)',
            borderRadius: 8,
            padding: 24
          }}
        >
          <Descriptions
            column={2}
            bordered
            size="middle"
            labelStyle={{
              background: 'rgba(24, 144, 255, 0.1)',
              color: '#8c9cb8',
              width: 120
            }}
            contentStyle={{
              background: 'rgba(20, 24, 41, 0.4)',
              color: '#e6f1ff'
            }}
          >
            <Descriptions.Item label="活动名称">
              {eventData.name}
            </Descriptions.Item>
            <Descriptions.Item label="活动类型">
              {getTypeName(eventData.type)}
            </Descriptions.Item>
            <Descriptions.Item label="安保级别">
              <span style={{ color: getLevelColor(eventData.level), fontWeight: 500 }}>
                {getLevelName(eventData.level)}
              </span>
            </Descriptions.Item>
            <Descriptions.Item label="活动状态">
              {getStatusTag(eventData.status)}
            </Descriptions.Item>
            <Descriptions.Item label="开始时间">
              {dayjs(eventData.startTime).format('YYYY-MM-DD HH:mm:ss')}
            </Descriptions.Item>
            <Descriptions.Item label="结束时间">
              {dayjs(eventData.endTime).format('YYYY-MM-DD HH:mm:ss')}
            </Descriptions.Item>
            <Descriptions.Item label="主办单位">
              {eventData.organizer || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="创建时间">
              {eventData.createTime
                ? dayjs(eventData.createTime).format('YYYY-MM-DD HH:mm:ss')
                : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="活动描述" span={2}>
              {eventData.description || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="活动区域" span={2}>
              {eventData.polygonPoints && eventData.polygonPoints.length > 0 ? (
                <Space size={[8, 8]} wrap>
                  {eventData.polygonPoints.map((point, index) => (
                    <Tag key={index} color="blue">
                      顶点{index + 1}: {point.lng.toFixed(4)}, {point.lat.toFixed(4)}
                    </Tag>
                  ))}
                </Space>
              ) : (
                '-'
              )}
            </Descriptions.Item>
          </Descriptions>
        </div>
      )}

      {activeTab === 'resources' && (
        <div style={{ display: 'flex', gap: 20, height: 'calc(100vh - 200px)' }}>
          <div style={{ flex: 1, position: 'relative' }}>
            <div
              className="tech-card"
              style={{
                height: '100%',
                padding: 8,
                position: 'relative'
              }}
            >
              <div className="corner-decoration corner-tl" />
              <div className="corner-decoration corner-tr" />
              <div className="corner-decoration corner-bl" />
              <div className="corner-decoration corner-br" />

              <div className="panel-header" style={{ marginBottom: 8 }}>
                <span className="panel-title" style={{ fontSize: 14 }}>
                  <EnvironmentOutlined style={{ marginRight: 8 }} />
                  资源分布图
                </span>
              </div>

              <div
                ref={mapContainerRef}
                style={{ height: 'calc(100% - 48px)', borderRadius: 4, overflow: 'hidden' }}
              />
            </div>
          </div>

          <div style={{ width: 360, display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div
              className="tech-card"
              style={{
                flex: 1,
                padding: 16,
                overflow: 'hidden',
                display: 'flex',
                flexDirection: 'column'
              }}
            >
              <div className="panel-header" style={{ marginBottom: 12 }}>
                <span className="panel-title" style={{ fontSize: 14 }}>
                  <TeamOutlined style={{ marginRight: 8 }} />
                  警力部署
                  <Tag color="blue" style={{ marginLeft: 8 }}>
                    {mockPoliceForces.length}
                  </Tag>
                </span>
              </div>

              <div style={{ flex: 1, overflow: 'auto' }}>
                <List
                  dataSource={mockPoliceForces}
                  renderItem={(item) => (
                    <List.Item
                      style={{
                        borderBottom: '1px solid rgba(24, 144, 255, 0.1)',
                        padding: '12px 0'
                      }}
                    >
                      <List.Item.Meta
                        avatar={
                          <Avatar
                            icon={<TeamOutlined />}
                            style={{
                              background: getStatusColor(item.status),
                              verticalAlign: 'middle'
                            }}
                          />
                        }
                        title={
                          <span style={{ color: '#e6f1ff' }}>{item.name}</span>
                        }
                        description={
                          <Space direction="vertical" size={4}>
                            <Tag color="default" style={{ margin: 0 }}>
                              {getPoliceTypeLabel(item.type)}
                            </Tag>
                            <span style={{ color: getStatusColor(item.status), fontSize: 12 }}>
                              {getStatusLabel(item.status)}
                            </span>
                            {item.contact && (
                              <span style={{ color: '#8c9cb8', fontSize: 12 }}>
                                {item.contact}
                              </span>
                            )}
                          </Space>
                        }
                      />
                    </List.Item>
                  )}
                />
              </div>
            </div>

            <div
              className="tech-card"
              style={{
                flex: 1,
                padding: 16,
                overflow: 'hidden',
                display: 'flex',
                flexDirection: 'column'
              }}
            >
              <div className="panel-header" style={{ marginBottom: 12 }}>
                <span className="panel-title" style={{ fontSize: 14 }}>
                  <VideoCameraOutlined style={{ marginRight: 8 }} />
                  视频监控
                  <Tag color="green" style={{ marginLeft: 8 }}>
                    {mockCameras.filter(c => c.status === 'online').length}/{mockCameras.length}
                  </Tag>
                </span>
              </div>

              <div style={{ flex: 1, overflow: 'auto' }}>
                <List
                  dataSource={mockCameras}
                  renderItem={(item) => (
                    <List.Item
                      style={{
                        borderBottom: '1px solid rgba(24, 144, 255, 0.1)',
                        padding: '12px 0'
                      }}
                    >
                      <List.Item.Meta
                        avatar={
                          <Avatar
                            icon={<VideoCameraOutlined />}
                            style={{
                              background: item.status === 'online' ? '#52c41a' : '#8c9cb8',
                              verticalAlign: 'middle'
                            }}
                          />
                        }
                        title={
                          <span style={{ color: '#e6f1ff' }}>{item.name}</span>
                        }
                        description={
                          <Space direction="vertical" size={4}>
                            <span style={{ color: '#8c9cb8', fontSize: 12 }}>
                              {item.address}
                            </span>
                            <span
                              style={{
                                color: getStatusColor(item.status),
                                fontSize: 12
                              }}
                            >
                              {getStatusLabel(item.status)}
                            </span>
                          </Space>
                        }
                      />
                    </List.Item>
                  )}
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'security' && (
        <div
          style={{
            background: 'rgba(26, 31, 53, 0.6)',
            border: '1px solid rgba(24, 144, 255, 0.15)',
            borderRadius: 8,
            padding: 40,
            textAlign: 'center',
            color: '#8c9cb8'
          }}
        >
          <SafetyCertificateOutlined style={{ fontSize: 48, marginBottom: 16 }} />
          <p>安保方案功能开发中...</p>
        </div>
      )}

      {activeTab === 'warning' && (
        <div
          style={{
            background: 'rgba(26, 31, 53, 0.6)',
            border: '1px solid rgba(24, 144, 255, 0.15)',
            borderRadius: 8,
            padding: 40,
            textAlign: 'center',
            color: '#8c9cb8'
          }}
        >
          <BellOutlined style={{ fontSize: 48, marginBottom: 16 }} />
          <p>预警监控功能开发中...</p>
        </div>
      )}

      {activeTab === 'report' && (
        <div
          style={{
            background: 'rgba(26, 31, 53, 0.6)',
            border: '1px solid rgba(24, 144, 255, 0.15)',
            borderRadius: 8,
            padding: 40,
            textAlign: 'center',
            color: '#8c9cb8'
          }}
        >
          <FileTextOutlined style={{ fontSize: 48, marginBottom: 16 }} />
          <p>统计报告功能开发中...</p>
        </div>
      )}

      {activeTab === 'emergency' && eventId && (
        <EmergencyCommandPanel
          eventId={String(eventId)}
          eventName={eventData?.name}
          eventLevel={eventData?.securityLevel}
        />
      )}

      {activeTab === 'trajectory' && (
        <TrajectoryPredictionPanel
          eventId={String(eventId)}
          eventLng={eventData?.longitude as any}
          eventLat={eventData?.latitude as any}
        />
      )}
    </div>
  );
};

export default EventDetail;
