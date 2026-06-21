import React, { useEffect, useRef, useState } from 'react';
import { List, Tag, Badge, Button, Space } from 'antd';
import { Alarm } from '@/types';
import dayjs from 'dayjs';
import {
  EnvironmentOutlined,
  ClockCircleOutlined,
  UserOutlined,
  PhoneOutlined,
  EyeOutlined,
  RightOutlined
} from '@ant-design/icons';

interface AlarmListProps {
  alarms: Alarm[];
  onAlarmClick?: (alarm: Alarm) => void;
  onViewDetail?: (alarm: Alarm) => void;
}

const AlarmList: React.FC<AlarmListProps> = ({ alarms, onAlarmClick, onViewDetail }) => {
  const listRef = useRef<HTMLDivElement>(null);
  const [newAlarmId, setNewAlarmId] = useState<string | null>(null);
  const autoScrollRef = useRef(true);

  useEffect(() => {
    if (alarms.length > 0) {
      const latestAlarm = alarms[0];
      setNewAlarmId(latestAlarm.id);
      const timer = setTimeout(() => {
        setNewAlarmId(null);
      }, 5000);
      return () => clearTimeout(timer);
    }
  }, [alarms]);

  useEffect(() => {
    if (!autoScrollRef.current || !listRef.current) return;

    const container = listRef.current;
    const scrollContent = container.querySelector('.ant-list-items');
    if (!scrollContent) return;

    let animationId: number;
    let scrollPosition = 0;

    const scroll = () => {
      if (!autoScrollRef.current || !container || !scrollContent) return;

      scrollPosition += 0.5;

      if (scrollPosition >= scrollContent.scrollHeight / 2) {
        scrollPosition = 0;
      }

      container.scrollTop = scrollPosition;
      animationId = requestAnimationFrame(scroll);
    };

    animationId = requestAnimationFrame(scroll);

    return () => {
      cancelAnimationFrame(animationId);
    };
  }, [alarms.length]);

  useEffect(() => {
    const container = listRef.current;
    if (!container) return;

    const handleMouseEnter = () => {
      autoScrollRef.current = false;
    };

    const handleMouseLeave = () => {
      autoScrollRef.current = true;
    };

    container.addEventListener('mouseenter', handleMouseEnter);
    container.addEventListener('mouseleave', handleMouseLeave);

    return () => {
      container.removeEventListener('mouseenter', handleMouseEnter);
      container.removeEventListener('mouseleave', handleMouseLeave);
    };
  }, []);

  const getLevelColor = (level: string) => {
    switch (level) {
      case 'high': return 'red';
      case 'medium': return 'orange';
      default: return 'green';
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'pending': return 'red';
      case 'processing': return 'orange';
      case 'resolved': return 'green';
      default: return 'default';
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case 'pending': return '待处理';
      case 'processing': return '处理中';
      case 'resolved': return '已处理';
      default: return '未知';
    }
  };

  const duplicatedAlarms = [...alarms, ...alarms];

  return (
    <div className="tech-card" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div className="corner-decoration corner-tl" />
      <div className="corner-decoration corner-tr" />
      <div className="corner-decoration corner-bl" />
      <div className="corner-decoration corner-br" />

      <div className="panel-header">
        <span className="panel-title">实时警情</span>
        <Badge count={alarms.filter(a => a.status === 'pending').length} color="#ff4d4f" />
      </div>

      <div
        ref={listRef}
        style={{
          flex: 1,
          overflow: 'hidden',
          padding: '0 8px'
        }}
      >
        <List
          dataSource={duplicatedAlarms}
          renderItem={(alarm) => (
            <List.Item
              key={alarm.id}
              className={newAlarmId === alarm.id ? 'alarm-flash' : ''}
              style={{
                padding: '12px 8px',
                borderBottom: '1px solid #1f2940',
                cursor: 'pointer',
                transition: 'background 0.3s',
                background: newAlarmId === alarm.id ? 'rgba(255, 77, 79, 0.1)' : 'transparent'
              }}
              onClick={() => onAlarmClick?.(alarm)}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = 'rgba(24, 144, 255, 0.1)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = newAlarmId === alarm.id
                  ? 'rgba(255, 77, 79, 0.1)'
                  : 'transparent';
              }}
            >
              <List.Item.Meta
                avatar={
                  <Badge
                    status={getStatusColor(alarm.status) as any}
                    text={getStatusText(alarm.status)}
                    style={{ color: '#8c9cb8', fontSize: 11 }}
                  />
                }
                title={
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <span style={{ color: '#e6f1ff', fontSize: 14, fontWeight: 500 }}>
                      {alarm.title}
                    </span>
                    <Tag color={getLevelColor(alarm.level)} style={{ margin: 0 }}>
                      {alarm.levelName}
                    </Tag>
                  </div>
                }
                description={
                  <div style={{ color: '#8c9cb8', fontSize: 12, marginTop: 8 }}>
                    <Space direction="vertical" size={4} style={{ width: '100%' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <Tag color="blue" style={{ margin: 0, fontSize: 11 }}>
                          {alarm.typeName}
                        </Tag>
                        <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                          <EnvironmentOutlined />
                          {alarm.address}
                        </span>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                          <ClockCircleOutlined />
                          {dayjs(alarm.reportTime).format('MM-DD HH:mm:ss')}
                        </span>
                        <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                          <UserOutlined />
                          {alarm.reporter || '匿名'}
                        </span>
                        <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                          <PhoneOutlined />
                          {alarm.reporterPhone || '未提供'}
                        </span>
                      </div>
                    </Space>
                  </div>
                }
              />
              <Button
                type="text"
                size="small"
                icon={<EyeOutlined />}
                style={{ color: '#1890ff' }}
                onClick={(e) => {
                  e.stopPropagation();
                  onViewDetail?.(alarm);
                }}
              >
                查看
              </Button>
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
        <span>共 {alarms.length} 条警情</span>
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

export default AlarmList;
