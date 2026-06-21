import React, { useState, useEffect, useRef } from 'react';
import {
  Tabs,
  InputNumber,
  Button,
  Space,
  List,
  Avatar,
  Tag,
  Statistic,
  Row,
  Col,
  Card,
  message,
  Spin
} from 'antd';
import {
  TeamOutlined,
  VideoCameraOutlined,
  ReloadOutlined,
  PlayCircleOutlined,
  EnvironmentOutlined
} from '@ant-design/icons';
import {
  ResourcePolice,
  ResourceCamera,
  ResourceAllocateResult
} from '@/types';
import {
  getPoliceResources,
  getCameraResources,
  allocateResources
} from '@/services/eventApi';

declare global {
  interface Window {
    TMap: any;
  }
}

interface ResourceAllocateProps {
  eventId: string;
}

const ResourceAllocate: React.FC<ResourceAllocateProps> = ({ eventId }) => {
  const [radius, setRadius] = useState<number>(500);
  const [activeTab, setActiveTab] = useState<string>('police');
  const [loading, setLoading] = useState<boolean>(false);
  const [allocating, setAllocating] = useState<boolean>(false);
  const [policeList, setPoliceList] = useState<ResourcePolice[]>([]);
  const [cameraList, setCameraList] = useState<ResourceCamera[]>([]);

  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const markersRef = useRef<any>(null);

  useEffect(() => {
    fetchResources();
  }, [eventId, radius, activeTab]);

  useEffect(() => {
    const initMap = () => {
      if (!window.TMap) {
        setTimeout(initMap, 1000);
        return;
      }
      if (!mapContainerRef.current) return;

      const center = new window.TMap.LatLng(39.9042, 116.4074);

      mapRef.current = new window.TMap.Map(mapContainerRef.current, {
        center,
        zoom: 15,
        pitch: 0,
        rotation: 0,
        viewMode: '2D',
        mapStyleId: 'style1',
        baseMap: {
          type: 'vector',
          features: ['base', 'road', 'point']
        }
      });

      markersRef.current = new window.TMap.MultiMarker({
        map: mapRef.current,
        styles: {}
      });
    };

    initMap();

    return () => {
      if (mapRef.current) {
        mapRef.current.destroy();
      }
    };
  }, []);

  useEffect(() => {
    updateMarkers();
  }, [policeList, cameraList, activeTab]);

  const fetchResources = async () => {
    if (!eventId) return;
    setLoading(true);
    try {
      if (activeTab === 'police') {
        const res = await getPoliceResources(eventId, radius);
        setPoliceList(res.data || []);
      } else {
        const res = await getCameraResources(eventId, radius);
        setCameraList(res.data || []);
      }
    } catch (error: any) {
      message.error(error.message || '获取资源列表失败');
    } finally {
      setLoading(false);
    }
  };

  const updateMarkers = () => {
    if (!window.TMap || !mapRef.current || !markersRef.current) return;

    markersRef.current.setMap(null);
    markersRef.current = new window.TMap.MultiMarker({
      map: mapRef.current
    });

    let markers: any[] = [];

    if (activeTab === 'police') {
      markers = policeList.map((police, index) => ({
        id: `police_${police.id}`,
        position: new window.TMap.LatLng(police.position.lat, police.position.lng),
        properties: police,
        styles: police.status === 'online' ? 'police_online' : 'police_offline'
      }));

      markersRef.current.setStyles({
        police_online: new window.TMap.MarkerStyle({
          width: 28,
          height: 28,
          anchor: { x: 14, y: 14 },
          src: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyOCIgaGVpZ2h0PSIyOCIgdmlld0JveD0iMCAwIDI4IDI4Ij48Y2lyY2xlIGN4PSIxNCIgY3k9IjE0IiByPSIxMiIgZmlsbD0iIzE4OTBmZiIgc3Ryb2tlPSIjZmZmIiBzdHJva2Utd2lkdGg9IjIiLz48cGF0aCBmaWxsPSIjZmZmIiBkPSJNMTQgNmwzIDZoLTJ2NmgtMnYtNmgtM3oiLz48L3N2Zz4='
        }),
        police_offline: new window.TMap.MarkerStyle({
          width: 28,
          height: 28,
          anchor: { x: 14, y: 14 },
          src: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyOCIgaGVpZ2h0PSIyOCIgdmlld0JveD0iMCAwIDI4IDI4Ij48Y2lyY2xlIGN4PSIxNCIgY3k9IjE0IiByPSIxMiIgZmlsbD0iIzhjOWNiOCIgc3Ryb2tlPSIjZmZmIiBzdHJva2Utd2lkdGg9IjIiLz48cGF0aCBmaWxsPSIjZmZmIiBkPSJNMTQgNmwzIDZoLTJ2NmgtMnYtNmgtM3oiLz48L3N2Zz4='
        })
      });
    } else {
      markers = cameraList.map((camera) => ({
        id: `camera_${camera.id}`,
        position: new window.TMap.LatLng(camera.position.lat, camera.position.lng),
        properties: camera,
        styles: camera.status === 'online' ? 'camera_online' : 'camera_offline'
      }));

      markersRef.current.setStyles({
        camera_online: new window.TMap.MarkerStyle({
          width: 24,
          height: 24,
          anchor: { x: 12, y: 12 },
          src: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgdmlld0JveD0iMCAwIDI0IDI0Ij48cmVjdCB4PSIyIiB5PSI2IiB3aWR0aD0iMTQiIGhlaWdodD0iMTIiIHJ4PSIyIiBmaWxsPSIjNTJjNDFhIiBzdHJva2U9IiNmZmYiIHN0cm9rZS13aWR0aD0iMiIvPjxjaXJjbGUgY3g9IjE5IiBjeT0iMTIiIHI9IjMiIGZpbGw9IiM1MmM0MWEiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIyIi8+PHBhdGggZD0iTTE2IDEwbDQgMi00IDJ6IiBmaWxsPSIjZmZmIi8+PC9zdmc+'
        }),
        camera_offline: new window.TMap.MarkerStyle({
          width: 24,
          height: 24,
          anchor: { x: 12, y: 12 },
          src: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgdmlld0JveD0iMCAwIDI0IDI0Ij48cmVjdCB4PSIyIiB5PSI2IiB3aWR0aD0iMTQiIGhlaWdodD0iMTIiIHJ4PSIyIiBmaWxsPSIjOGM5Y2I4IiBzdHJva2U9IiNmZmYiIHN0cm9rZS13aWR0aD0iMiIvPjxjaXJjbGUgY3g9IjE5IiBjeT0iMTIiIHI9IjMiIGZpbGw9IiM4YzljYjgiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIyIi8+PHBhdGggZD0iTTE2IDEwbDQgMi00IDJ6IiBmaWxsPSIjZmZmIi8+PC9zdmc+'
        })
      });
    }

    if (markers.length > 0) {
      markersRef.current.setGeometries(markers);
    }
  };

  const handleAllocate = async () => {
    if (!eventId) return;
    setAllocating(true);
    try {
      const res: { data: ResourceAllocateResult } = await allocateResources(eventId, radius);
      message.success(`分配成功！共分配 ${res.data.totalAllocated} 个资源（警力：${res.data.policeCount}，摄像头：${res.data.cameraCount}）`);
      fetchResources();
    } catch (error: any) {
      message.error(error.message || '资源分配失败');
    } finally {
      setAllocating(false);
    }
  };

  const handleRefresh = () => {
    fetchResources();
  };

  const getPoliceStats = () => {
    const total = policeList.length;
    const allocated = policeList.filter(p => p.allocated).length;
    const online = policeList.filter(p => p.status === 'online').length;
    return { total, allocated, online };
  };

  const getCameraStats = () => {
    const total = cameraList.length;
    const allocated = cameraList.filter(c => c.allocated).length;
    const online = cameraList.filter(c => c.status === 'online').length;
    return { total, allocated, online };
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'online': return 'green';
      case 'offline': return 'default';
      case 'busy': return 'orange';
      default: return 'default';
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case 'online': return '在线';
      case 'offline': return '离线';
      case 'busy': return '忙碌';
      default: return '未知';
    }
  };

  const policeStats = getPoliceStats();
  const cameraStats = getCameraStats();

  const renderPoliceList = () => (
    <div style={{ height: '100%', overflow: 'auto' }}>
      <List
        dataSource={policeList}
        renderItem={(police) => (
          <List.Item
            key={police.id}
            style={{
              padding: '12px 8px',
              borderBottom: '1px solid #1f2940',
              background: police.allocated ? 'rgba(24, 144, 255, 0.1)' : 'transparent',
              transition: 'all 0.3s'
            }}
          >
            <List.Item.Meta
              avatar={
                <Avatar
                  style={{
                    background: police.status === 'online' ? '#1890ff' : '#8c9cb8',
                    border: '2px solid #fff'
                  }}
                  icon={<TeamOutlined />}
                />
              }
              title={
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ color: '#e6f1ff', fontSize: 14, fontWeight: 500 }}>
                    {police.name}
                  </span>
                  <Tag color={getStatusColor(police.status)} style={{ margin: 0 }}>
                    {getStatusText(police.status)}
                  </Tag>
                </div>
              }
              description={
                <div style={{ color: '#8c9cb8', fontSize: 12, marginTop: 4 }}>
                  <Space direction="vertical" size={4}>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <span>警号：{police.policeNo}</span>
                      <span style={{ color: '#1890ff', fontWeight: 500 }}>
                        <EnvironmentOutlined style={{ marginRight: 4 }} />
                        {police.distance}米
                      </span>
                    </div>
                    {police.dept && <div>部门：{police.dept}</div>}
                  </Space>
                </div>
              }
            />
            {police.allocated && (
              <Tag color="blue" style={{ marginTop: 8 }}>已分配</Tag>
            )}
          </List.Item>
        )}
      />
    </div>
  );

  const renderCameraList = () => (
    <div style={{ height: '100%', overflow: 'auto' }}>
      <List
        dataSource={cameraList}
        renderItem={(camera) => (
          <List.Item
            key={camera.id}
            style={{
              padding: '12px 8px',
              borderBottom: '1px solid #1f2940',
              background: camera.allocated ? 'rgba(24, 144, 255, 0.1)' : 'transparent',
              transition: 'all 0.3s'
            }}
          >
            <List.Item.Meta
              avatar={
                <div
                  style={{
                    width: 40,
                    height: 40,
                    borderRadius: 8,
                    background: camera.status === 'online'
                      ? 'linear-gradient(135deg, #52c41a, #13c2c2)'
                      : '#8c9cb8',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#fff',
                    fontSize: 20
                  }}
                >
                  <VideoCameraOutlined />
                </div>
              }
              title={
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ color: '#e6f1ff', fontSize: 14, fontWeight: 500 }}>
                    {camera.name}
                  </span>
                  <Tag color={getStatusColor(camera.status)} style={{ margin: 0 }}>
                    {getStatusText(camera.status)}
                  </Tag>
                </div>
              }
              description={
                <div style={{ color: '#8c9cb8', fontSize: 12, marginTop: 4 }}>
                  <Space direction="vertical" size={4} style={{ width: '100%' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <span>设备号：{camera.deviceNo}</span>
                      <span style={{ color: '#1890ff', fontWeight: 500 }}>
                        <EnvironmentOutlined style={{ marginRight: 4 }} />
                        {camera.distance}米
                      </span>
                    </div>
                    {camera.address && (
                      <div style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        地址：{camera.address}
                      </div>
                    )}
                  </Space>
                </div>
              }
            />
            {camera.allocated && (
              <Tag color="blue" style={{ marginTop: 8 }}>已分配</Tag>
            )}
          </List.Item>
        )}
      />
    </div>
  );

  const tabItems = [
    {
      key: 'police',
      label: (
        <span>
          <TeamOutlined style={{ marginRight: 6 }} />
          警力资源
        </span>
      )
    },
    {
      key: 'camera',
      label: (
        <span>
          <VideoCameraOutlined style={{ marginRight: 6 }} />
          摄像头资源
        </span>
      )
    }
  ];

  const currentStats = activeTab === 'police' ? policeStats : cameraStats;

  return (
    <div className="tech-card" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div className="corner-decoration corner-tl" />
      <div className="corner-decoration corner-tr" />
      <div className="corner-decoration corner-bl" />
      <div className="corner-decoration corner-br" />

      <div className="panel-header">
        <span className="panel-title">资源分配</span>
        <Space>
          <span style={{ color: '#8c9cb8', fontSize: 12 }}>搜索半径：</span>
          <InputNumber
            min={100}
            max={5000}
            step={100}
            value={radius}
            onChange={(value) => setRadius(value || 500)}
            size="small"
            style={{ width: 100 }}
            addonAfter="米"
          />
          <Button
            type="primary"
            size="small"
            icon={<PlayCircleOutlined />}
            loading={allocating}
            onClick={handleAllocate}
          >
            开始分配
          </Button>
          <Button
            size="small"
            icon={<ReloadOutlined />}
            onClick={handleRefresh}
            loading={loading}
          >
            刷新
          </Button>
        </Space>
      </div>

      <Row gutter={[12, 12]} style={{ padding: '0 16px', marginBottom: 12 }}>
        <Col span={8}>
          <Card
            size="small"
            styles={{
              body: { padding: '12px 8px', textAlign: 'center', background: 'rgba(26, 31, 53, 0.5)' }
            }}
            bordered={false}
          >
            <Statistic
              title={<span style={{ fontSize: 11, color: '#8c9cb8' }}>资源总数</span>}
              value={currentStats.total}
              valueStyle={{
                color: '#1890ff',
                fontSize: 18,
                fontWeight: 600,
                background: 'linear-gradient(180deg, #1890ff, #00d4ff)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent'
              }}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card
            size="small"
            styles={{
              body: { padding: '12px 8px', textAlign: 'center', background: 'rgba(26, 31, 53, 0.5)' }
            }}
            bordered={false}
          >
            <Statistic
              title={<span style={{ fontSize: 11, color: '#8c9cb8' }}>已分配</span>}
              value={currentStats.allocated}
              valueStyle={{
                color: '#52c41a',
                fontSize: 18,
                fontWeight: 600,
                background: 'linear-gradient(180deg, #52c41a, #13c2c2)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent'
              }}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card
            size="small"
            styles={{
              body: { padding: '12px 8px', textAlign: 'center', background: 'rgba(26, 31, 53, 0.5)' }
            }}
            bordered={false}
          >
            <Statistic
              title={<span style={{ fontSize: 11, color: '#8c9cb8' }}>在线</span>}
              value={currentStats.online}
              valueStyle={{
                color: '#faad14',
                fontSize: 18,
                fontWeight: 600,
                background: 'linear-gradient(180deg, #faad14, #ff7a00)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent'
              }}
            />
          </Card>
        </Col>
      </Row>

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={tabItems}
        style={{ padding: '0 16px', flex: 1, display: 'flex', flexDirection: 'column' }}
        itemsStyle={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}
        tabBarStyle={{ marginBottom: 12 }}
      />

      <div style={{ flex: 1, display: 'flex', overflow: 'hidden', padding: '0 16px 16px' }}>
        <Spin spinning={loading} style={{ width: '100%' }}>
          <div style={{ display: 'flex', height: '100%', width: '100%', gap: 12 }}>
            <div style={{ width: 320, height: '100%', border: '1px solid #1f2940', borderRadius: 8, overflow: 'hidden' }}>
              {activeTab === 'police' ? renderPoliceList() : renderCameraList()}
            </div>
            <div style={{ flex: 1, height: '100%', border: '1px solid #1f2940', borderRadius: 8, overflow: 'hidden', position: 'relative' }}>
              <div
                ref={mapContainerRef}
                style={{ width: '100%', height: '100%' }}
              />
              <div style={{
                position: 'absolute',
                top: 12,
                left: 12,
                background: 'rgba(20, 24, 41, 0.9)',
                border: '1px solid rgba(24, 144, 255, 0.3)',
                borderRadius: 6,
                padding: '8px 12px',
                fontSize: 12,
                color: '#8c9cb8'
              }}>
                <span style={{ color: '#e6f1ff' }}>{activeTab === 'police' ? '警力分布' : '摄像头分布'}</span>
                <span style={{ marginLeft: 8 }}>共 {currentStats.total} 个</span>
              </div>
            </div>
          </div>
        </Spin>
      </div>
    </div>
  );
};

export default ResourceAllocate;
