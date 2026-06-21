import React, { useState } from 'react';
import { List, Tag, Button, Space, Popconfirm, Badge, Progress } from 'antd';
import { Alert } from '@/types';
import dayjs from 'dayjs';
import {
  VideoCameraOutlined,
  ClockCircleOutlined,
  CheckOutlined,
  CloseOutlined,
  EyeOutlined,
  RightOutlined,
  BgColorsOutlined
} from '@ant-design/icons';

interface AlertListProps {
  alerts: Alert[];
  onViewDetail?: (alert: Alert) => void;
  onHandle?: (alert: Alert, status: string) => void;
}

const AlertList: React.FC<AlertListProps> = ({ alerts, onViewDetail, onHandle }) => {
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const getLevelColor = (level: string) => {
    switch (level) {
      case 'high': return 'red';
      case 'medium': return 'orange';
      default: return 'green';
    }
  };

  const getLevelClass = (level: string) => {
    switch (level) {
      case 'high': return 'level-high';
      case 'medium': return 'level-medium';
      default: return 'level-low';
    }
  };

  const getTypeIcon = (type: string) => {
    switch (type) {
      case 'intrusion': return '🚨';
      case 'abnormal_behavior': return '⚠️';
      case 'crowd_gathering': return '👥';
      case 'vehicle_abnormal': return '🚗';
      case 'fire_detection': return '🔥';
      default: return '🔔';
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'unhandled': return 'red';
      case 'handled': return 'green';
      case 'false_alarm': return 'default';
      default: return 'default';
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case 'unhandled': return '未处理';
      case 'handled': return '已处理';
      case 'false_alarm': return '误报';
      default: return '未知';
    }
  };

  const getConfidenceColor = (confidence: number) => {
    if (confidence >= 80) return '#ff4d4f';
    if (confidence >= 60) return '#faad14';
    return '#52c41a';
  };

  const unhandledCount = alerts.filter(a => a.status === 'unhandled').length;
  const highLevelCount = alerts.filter(a => a.level === 'high' && a.status === 'unhandled').length;

  return (
    <div className="tech-card" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div className="corner-decoration corner-tl" />
      <div className="corner-decoration corner-tr" />
      <div className="corner-decoration corner-bl" />
      <div className="corner-decoration corner-br" />

      <div className="panel-header">
        <span className="panel-title">AI智能告警</span>
        <Space>
          <Badge count={highLevelCount} color="#ff4d4f" offset={[-4, 0]} />
          <Badge count={unhandledCount} color="#faad14" />
        </Space>
      </div>

      <div style={{ padding: '0 8px', marginBottom: 8 }}>
        <Space wrap size={[8, 8]}>
          <Tag color="red">高危 {highLevelCount}</Tag>
          <Tag color="orange">未处理 {unhandledCount}</Tag>
          <Tag color="green">今日 {alerts.length}</Tag>
        </Space>
      </div>

      <div
        style={{
          flex: 1,
          overflow: 'auto',
          padding: '0 8px'
        }}
      >
        <List
          dataSource={alerts}
          renderItem={(alert) => (
            <List.Item
              key={alert.id}
              style={{
                padding: '12px 8px',
                borderBottom: '1px solid #1f2940',
                borderLeft: alert.status === 'unhandled' ? `3px solid ${getLevelColor(alert.level)}` : '3px solid transparent',
                background: alert.status === 'unhandled' ? 'rgba(20, 24, 41, 0.5)' : 'transparent',
                transition: 'all 0.3s'
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = 'rgba(24, 144, 255, 0.1)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = alert.status === 'unhandled'
                  ? 'rgba(20, 24, 41, 0.5)'
                  : 'transparent';
              }}
            >
              <List.Item.Meta
                avatar={
                  <div style={{
                    width: 40,
                    height: 40,
                    borderRadius: '50%',
                    background: 'rgba(24, 144, 255, 0.1)',
                    border: `2px solid ${getLevelColor(alert.level)}`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 20
                  }}>
                    {getTypeIcon(alert.type)}
                  </div>
                }
                title={
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <Space>
                      <span style={{ color: '#e6f1ff', fontSize: 14, fontWeight: 500 }}>
                        {alert.title}
                      </span>
                      <Tag color={getLevelColor(alert.level)} className={getLevelClass(alert.level)}>
                        {alert.levelName}
                      </Tag>
                      <Badge
                        status={getStatusColor(alert.status) as any}
                        text={getStatusText(alert.status)}
                        style={{ color: '#8c9cb8', fontSize: 11 }}
                      />
                    </Space>
                    <Space>
                      <BgColorsOutlined style={{ color: getConfidenceColor(alert.confidence) }} />
                      <span style={{ color: getConfidenceColor(alert.confidence), fontSize: 12, fontWeight: 600 }}>
                        {alert.confidence}%
                      </span>
                    </Space>
                  </div>
                }
                description={
                  <div style={{ color: '#8c9cb8', fontSize: 12, marginTop: 8 }}>
                    <Space direction="vertical" size={4} style={{ width: '100%' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
                        <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                          <VideoCameraOutlined />
                          {alert.cameraName}
                        </span>
                        <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                          <ClockCircleOutlined />
                          {dayjs(alert.captureTime).format('MM-DD HH:mm:ss')}
                        </span>
                      </div>

                      {expandedId === alert.id && (
                        <div style={{
                          marginTop: 8,
                          padding: 8,
                          background: 'rgba(26, 31, 53, 0.8)',
                          borderRadius: 4,
                          border: '1px solid #1f2940'
                        }}>
                          <div style={{ marginBottom: 8 }}>
                            <span style={{ color: '#8c9cb8' }}>置信度：</span>
                            <Progress
                              percent={alert.confidence}
                              size="small"
                              showInfo={false}
                              strokeColor={getConfidenceColor(alert.confidence)}
                              style={{ width: 120, display: 'inline-block', marginLeft: 8 }}
                            />
                          </div>
                          <div>
                            <span style={{ color: '#8c9cb8' }}>告警类型：</span>
                            <Tag color="purple" style={{ margin: 0 }}>{alert.typeName}</Tag>
                          </div>
                        </div>
                      )}
                    </Space>
                  </div>
                }
              />
              <Space direction="vertical" size={4} align="end">
                <Button
                  type="text"
                  size="small"
                  onClick={() => setExpandedId(expandedId === alert.id ? null : alert.id)}
                  style={{ color: '#1890ff', fontSize: 11 }}
                >
                  {expandedId === alert.id ? '收起' : '展开'}
                </Button>
                <Space size={4}>
                  {alert.status === 'unhandled' && (
                    <>
                      <Popconfirm
                        title="确认标记为已处理？"
                        onConfirm={() => onHandle?.(alert, 'handled')}
                        okText="确认"
                        cancelText="取消"
                      >
                        <Button
                          type="text"
                          size="small"
                          icon={<CheckOutlined />}
                          style={{ color: '#52c41a' }}
                        >
                          处理
                        </Button>
                      </Popconfirm>
                      <Popconfirm
                        title="确认标记为误报？"
                        onConfirm={() => onHandle?.(alert, 'false_alarm')}
                        okText="确认"
                        cancelText="取消"
                      >
                        <Button
                          type="text"
                          size="small"
                          icon={<CloseOutlined />}
                          style={{ color: '#8c9cb8' }}
                        >
                          误报
                        </Button>
                      </Popconfirm>
                    </>
                  )}
                  <Button
                    type="text"
                    size="small"
                    icon={<EyeOutlined />}
                    style={{ color: '#1890ff' }}
                    onClick={() => onViewDetail?.(alert)}
                  >
                    详情
                  </Button>
                </Space>
              </Space>
            </List.Item>
          )}
        />
      </div>

      <div style={{
        padding: '8px 16px',
        borderTop: '1px solid #1f2940',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        fontSize: 12,
        color: '#8c9cb8'
      }}>
        <span>共 {alerts.length} 条告警</span>
        <Button
          type="text"
          size="small"
          style={{ color: '#1890ff', padding: 0, height: 'auto' }}
        >
          查看全部 <RightOutlined style={{ fontSize: 10 }} />
        </Button>
      </div>
    </div>
  );
};

export default AlertList;
