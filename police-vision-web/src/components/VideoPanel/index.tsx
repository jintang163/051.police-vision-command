import React, { useState } from 'react';
import { Card, Row, Col, Button, Space, Select, Tag, Empty, Spin, Tooltip } from 'antd';
import { VideoChannel, Camera } from '@/types';
import {
  PlayCircleOutlined,
  PauseCircleOutlined,
  FullscreenOutlined,
  ReloadOutlined,
  SwapOutlined,
  SettingOutlined,
  VideoCameraOutlined,
  VideoCameraAddOutlined
} from '@ant-design/icons';

const { Option } = Select;

interface VideoPanelProps {
  cameras: Camera[];
}

const VideoPanel: React.FC<VideoPanelProps> = ({ cameras }) => {
  const [channels, setChannels] = useState<VideoChannel[]>([
    { id: '1', name: '通道1', url: '', status: 'loading' },
    { id: '2', name: '通道2', url: '', status: 'paused' },
    { id: '3', name: '通道3', url: '', status: 'paused' },
    { id: '4', name: '通道4', url: '', status: 'paused' },
  ]);
  const [selectedChannel, setSelectedChannel] = useState<string | null>(null);

  const handleCameraSelect = (channelId: string, cameraId: string) => {
    const camera = cameras.find(c => c.id === cameraId);
    if (!camera) return;

    setChannels(prev => prev.map(ch =>
      ch.id === channelId
        ? { ...ch, name: camera.name, url: camera.previewUrl || '', status: 'playing' }
        : ch
    ));
  };

  const togglePlay = (channelId: string) => {
    setChannels(prev => prev.map(ch => {
      if (ch.id !== channelId) return ch;
      const newStatus = ch.status === 'playing' ? 'paused' : 'playing';
      return { ...ch, status: newStatus };
    }));
  };

  const reloadChannel = (channelId: string) => {
    setChannels(prev => prev.map(ch =>
      ch.id === channelId ? { ...ch, status: 'loading' } : ch
    ));

    setTimeout(() => {
      setChannels(prev => prev.map(ch =>
        ch.id === channelId ? { ...ch, status: 'playing' } : ch
      ));
    }, 1000);
  };

  const clearChannel = (channelId: string) => {
    setChannels(prev => prev.map(ch =>
      ch.id === channelId
        ? { ...ch, name: `通道${ch.id}`, url: '', status: 'paused' }
        : ch
    ));
  };

  const fullscreen = (channelId: string) => {
    const container = document.getElementById(`video-container-${channelId}`);
    if (container && container.requestFullscreen) {
      container.requestFullscreen();
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'playing': return <PlayCircleOutlined style={{ color: '#52c41a' }} />;
      case 'paused': return <PauseCircleOutlined style={{ color: '#8c9cb8' }} />;
      case 'loading': return <Spin size="small" indicator={<VideoCameraOutlined spin />} />;
      case 'error': return <VideoCameraOutlined style={{ color: '#ff4d4f' }} />;
      default: return <VideoCameraOutlined style={{ color: '#8c9cb8' }} />;
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case 'playing': return '播放中';
      case 'paused': return '已暂停';
      case 'loading': return '加载中';
      case 'error': return '加载失败';
      default: return '未连接';
    }
  };

  const onlineCameras = cameras.filter(c => c.status === 'online');

  return (
    <div className="tech-card" style={{ height: '100%', padding: 16, display: 'flex', flexDirection: 'column' }}>
      <div className="corner-decoration corner-tl" />
      <div className="corner-decoration corner-tr" />
      <div className="corner-decoration corner-bl" />
      <div className="corner-decoration corner-br" />

      <div className="panel-header">
        <span className="panel-title">视频监控</span>
        <Space>
          <Tag color="green">在线 {onlineCameras.length}</Tag>
          <Tag color="default">离线 {cameras.filter(c => c.status === 'offline').length}</Tag>
        </Space>
      </div>

      <Row gutter={[12, 12]} style={{ flex: 1, overflow: 'auto' }}>
        {channels.map((channel) => (
          <Col span={12} key={channel.id}>
            <Card
              size="small"
              styles={{
                body: { padding: 0, background: '#0a0e1a' }
              }}
              bordered
              style={{
                borderColor: selectedChannel === channel.id ? '#1890ff' : '#1f2940',
                boxShadow: selectedChannel === channel.id ? '0 0 10px rgba(24, 144, 255, 0.3)' : 'none'
              }}
              onClick={() => setSelectedChannel(channel.id)}
            >
              <div
                id={`video-container-${channel.id}`}
                style={{
                  position: 'relative',
                  width: '100%',
                  paddingBottom: '75%',
                  background: '#0a0e1a',
                  overflow: 'hidden'
                }}
              >
                {channel.url ? (
                  channel.status !== 'error' ? (
                    <div style={{
                      position: 'absolute',
                      top: 0,
                      left: 0,
                      width: '100%',
                      height: '100%',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      background: 'linear-gradient(135deg, #1a1f35 0%, #141829 100%)'
                    }}>
                      {channel.status === 'loading' ? (
                        <Space direction="vertical" align="center">
                          <Spin size="large" indicator={<VideoCameraOutlined spin style={{ fontSize: 32, color: '#1890ff' }} />} />
                          <span style={{ color: '#8c9cb8', fontSize: 12 }}>视频加载中...</span>
                        </Space>
                      ) : (
                        <div style={{
                          width: '100%',
                          height: '100%',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          flexDirection: 'column',
                          gap: 8
                        }}>
                          <VideoCameraOutlined style={{ fontSize: 48, color: '#1890ff', opacity: 0.5 }} />
                          <span style={{ color: '#8c9cb8', fontSize: 12 }}>{channel.name}</span>
                          <Tag color={channel.status === 'playing' ? 'green' : 'default'}>
                            {getStatusIcon(channel.status)} {getStatusText(channel.status)}
                          </Tag>
                        </div>
                      )}
                    </div>
                  ) : (
                    <div style={{
                      position: 'absolute',
                      top: 0,
                      left: 0,
                      width: '100%',
                      height: '100%',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexDirection: 'column',
                      gap: 8,
                      background: '#1a1f35'
                    }}>
                      <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description={<span style={{ color: '#8c9cb8' }}>视频加载失败</span>}
                      />
                      <Button type="primary" size="small" onClick={() => reloadChannel(channel.id)}>
                        重新加载
                      </Button>
                    </div>
                  )
                ) : (
                  <div style={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    width: '100%',
                    height: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexDirection: 'column',
                    gap: 12,
                    background: 'linear-gradient(135deg, #1a1f35 0%, #141829 100%)'
                  }}>
                    <VideoCameraAddOutlined style={{ fontSize: 48, color: '#1f2940' }} />
                    <span style={{ color: '#8c9cb8', fontSize: 12 }}>选择监控摄像头</span>
                    <Select
                      size="small"
                      placeholder="选择摄像头"
                      style={{ width: '80%' }}
                      onChange={(value) => handleCameraSelect(channel.id, value)}
                      options={onlineCameras.map(c => ({ label: c.name, value: c.id }))}
                    />
                  </div>
                )}

                <div style={{
                  position: 'absolute',
                  top: 8,
                  left: 8,
                  right: 8,
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center'
                }}>
                  <Tag
                    color={channel.status === 'playing' ? 'green' : 'default'}
                    style={{ margin: 0, fontSize: 11 }}
                  >
                    {getStatusIcon(channel.status)} {channel.name}
                  </Tag>
                  <div style={{ display: 'flex', gap: 4 }}>
                    {channel.url && channel.status !== 'error' && (
                      <Tooltip title={channel.status === 'playing' ? '暂停' : '播放'}>
                        <Button
                          type="text"
                          size="small"
                          icon={channel.status === 'playing' ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
                          style={{ color: '#e6f1ff', padding: '0 4px' }}
                          onClick={(e) => { e.stopPropagation(); togglePlay(channel.id); }}
                        />
                      </Tooltip>
                    )}
                    {channel.url && (
                      <>
                        <Tooltip title="重新加载">
                          <Button
                            type="text"
                            size="small"
                            icon={<ReloadOutlined />}
                            style={{ color: '#e6f1ff', padding: '0 4px' }}
                            onClick={(e) => { e.stopPropagation(); reloadChannel(channel.id); }}
                          />
                        </Tooltip>
                        <Tooltip title="全屏">
                          <Button
                            type="text"
                            size="small"
                            icon={<FullscreenOutlined />}
                            style={{ color: '#e6f1ff', padding: '0 4px' }}
                            onClick={(e) => { e.stopPropagation(); fullscreen(channel.id); }}
                          />
                        </Tooltip>
                      </>
                    )}
                  </div>
                </div>

                {channel.url && channel.status !== 'error' && channel.status !== 'loading' && (
                  <div style={{
                    position: 'absolute',
                    bottom: 8,
                    left: 8,
                    right: 8,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    background: 'rgba(0, 0, 0, 0.5)',
                    padding: '4px 8px',
                    borderRadius: 4,
                    fontSize: 11
                  }}>
                    <span style={{ color: '#8c9cb8' }}>
                      {new Date().toLocaleString('zh-CN', { hour12: false })}
                    </span>
                    <span style={{ color: '#52c41a' }}>● REC</span>
                  </div>
                )}
              </div>

              {channel.url && (
                <div style={{
                  padding: 8,
                  borderTop: '1px solid #1f2940',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center'
                }}>
                  <span style={{ color: '#8c9cb8', fontSize: 11 }}>{channel.name}</span>
                  <Space>
                    <Select
                      size="small"
                      value={channel.name.includes('通道') ? undefined : cameras.find(c => c.name === channel.name)?.id}
                      onChange={(value) => handleCameraSelect(channel.id, value)}
                      style={{ width: 120 }}
                      options={onlineCameras.map(c => ({ label: c.name, value: c.id }))}
                    />
                    <Button
                      type="text"
                      size="small"
                      icon={<SwapOutlined />}
                      style={{ color: '#8c9cb8', padding: '0 4px' }}
                      onClick={(e) => { e.stopPropagation(); clearChannel(channel.id); }}
                    />
                  </Space>
                </div>
              )}
            </Card>
          </Col>
        ))}
      </Row>

      <div style={{
        marginTop: 12,
        paddingTop: 12,
        borderTop: '1px solid #1f2940',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center'
      }}>
        <Space>
          <Button type="text" size="small" icon={<SettingOutlined />} style={{ color: '#8c9cb8' }}>
            视频设置
          </Button>
        </Space>
        <Space>
          <span style={{ color: '#8c9cb8', fontSize: 12 }}>共 {cameras.length} 路摄像头</span>
          <Button type="primary" size="small" icon={<VideoCameraOutlined />}>
            全部播放
          </Button>
        </Space>
      </div>
    </div>
  );
};

export default VideoPanel;
