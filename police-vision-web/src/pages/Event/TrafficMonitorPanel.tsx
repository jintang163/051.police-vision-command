import React, { useState, useEffect, useRef } from 'react';
import {
  Button,
  Space,
  Card,
  Row,
  Col,
  Statistic,
  List,
  Tag,
  Modal,
  Form,
  InputNumber,
  message,
  Popconfirm,
  Input
} from 'antd';
import {
  PlayCircleOutlined,
  PauseCircleOutlined,
  SettingOutlined,
  WarningOutlined,
  TeamOutlined,
  CarOutlined,
  ExclamationCircleOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  RiseOutlined
} from '@ant-design/icons';
import {
  TrafficAlertItem,
  TrafficStats,
  TrafficMonitorConfig,
  TrafficMonitorDTO
} from '@/types';
import {
  getTrafficStats,
  getTrafficAlerts,
  handleTrafficAlert,
  startTrafficMonitor,
  stopTrafficMonitor,
  getTrafficMonitorStatus
} from '@/services/eventApi';
import dayjs from 'dayjs';

interface TrafficMonitorPanelProps {
  eventId: string;
}

const TrafficMonitorPanel: React.FC<TrafficMonitorPanelProps> = ({ eventId }) => {
  const [stats, setStats] = useState<TrafficStats | null>(null);
  const [alerts, setAlerts] = useState<TrafficAlertItem[]>([]);
  const [monitoring, setMonitoring] = useState<boolean>(false);
  const [modalVisible, setModalVisible] = useState<boolean>(false);
  const [configForm] = Form.useForm();
  const [loading, setLoading] = useState<boolean>(false);
  const [displayPedestrian, setDisplayPedestrian] = useState<number>(0);
  const [displayVehicle, setDisplayVehicle] = useState<number>(0);
  const animationRef = useRef<number | null>(null);

  useEffect(() => {
    fetchStats();
    fetchAlerts();
    checkMonitorStatus();
  }, [eventId]);

  useEffect(() => {
    if (stats) {
      animateNumber(displayPedestrian, stats.pedestrianCount, setDisplayPedestrian);
      animateNumber(displayVehicle, stats.vehicleCount, setDisplayVehicle);
    }
  }, [stats?.pedestrianCount, stats?.vehicleCount]);

  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (monitoring) {
      interval = setInterval(() => {
        fetchStats();
        fetchAlerts();
      }, 5000);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [monitoring, eventId]);

  useEffect(() => {
    return () => {
      if (animationRef.current) {
        cancelAnimationFrame(animationRef.current);
      }
    };
  }, []);

  const animateNumber = (from: number, to: number, setter: React.Dispatch<React.SetStateAction<number>>) => {
    const duration = 1000;
    const startTime = performance.now();
    const diff = to - from;

    const step = (currentTime: number) => {
      const elapsed = currentTime - startTime;
      const progress = Math.min(elapsed / duration, 1);
      const easeProgress = 1 - Math.pow(1 - progress, 3);
      const currentValue = Math.round(from + diff * easeProgress);
      setter(currentValue);

      if (progress < 1) {
        animationRef.current = requestAnimationFrame(step);
      }
    };

    animationRef.current = requestAnimationFrame(step);
  };

  const fetchStats = async () => {
    if (!eventId) return;
    try {
      const res = await getTrafficStats(eventId);
      setStats(res.data);
    } catch (error: any) {
      console.error('获取流量统计失败:', error.message);
    }
  };

  const fetchAlerts = async () => {
    if (!eventId) return;
    setLoading(true);
    try {
      const res = await getTrafficAlerts(eventId, { page: 1, size: 20 });
      setAlerts(res.data || []);
    } catch (error: any) {
      console.error('获取预警列表失败:', error.message);
    } finally {
      setLoading(false);
    }
  };

  const checkMonitorStatus = async () => {
    if (!eventId) return;
    try {
      const res = await getTrafficMonitorStatus(eventId);
      setMonitoring(res.data.running);
      if (res.data.config) {
        configForm.setFieldsValue(res.data.config);
      }
    } catch (error: any) {
      console.error('获取监控状态失败:', error.message);
    }
  };

  const handleStartMonitor = () => {
    setModalVisible(true);
  };

  const handleStopMonitor = async () => {
    if (!eventId) return;
    try {
      await stopTrafficMonitor(eventId);
      setMonitoring(false);
      message.success('监控已停止');
    } catch (error: any) {
      message.error(error.message || '停止监控失败');
    }
  };

  const handleConfirmStart = async () => {
    try {
      const values = await configForm.validateFields();
      await startTrafficMonitor(eventId, values);
      setMonitoring(true);
      setModalVisible(false);
      message.success('监控已启动');
      fetchStats();
      fetchAlerts();
    } catch (error: any) {
      if (error.errorFields) {
        return;
      }
      message.error(error.message || '启动监控失败');
    }
  };

  const handleAlert = async (alertId: string) => {
    try {
      await handleTrafficAlert(alertId);
      message.success('处理成功');
      fetchAlerts();
      fetchStats();
    } catch (error: any) {
      message.error(error.message || '处理失败');
    }
  };

  const getLevelColor = (level: number) => {
    switch (level) {
      case 1: return 'green';
      case 2: return 'orange';
      case 3: return 'red';
      default: return 'default';
    }
  };

  const getLevelText = (level: number) => {
    switch (level) {
      case 1: return '低';
      case 2: return '中';
      case 3: return '高';
      default: return '未知';
    }
  };

  const getAlertTypeText = (type: string) => {
    switch (type) {
      case 'pedestrian': return '人流量';
      case 'vehicle': return '车流量';
      default: return '未知';
    }
  };

  const getAlertTypeIcon = (type: string) => {
    switch (type) {
      case 'pedestrian': return <TeamOutlined />;
      case 'vehicle': return <CarOutlined />;
      default: return <WarningOutlined />;
    }
  };

  const statCards = stats ? [
    {
      title: '今日预警',
      value: stats.todayAlertCount,
      icon: <WarningOutlined style={{ color: '#faad14' }} />,
      color: '#faad14',
      gradient: 'linear-gradient(180deg, #faad14, #ff7a00)'
    },
    {
      title: '未处理',
      value: stats.pendingAlertCount,
      icon: <ClockCircleOutlined style={{ color: '#ff4d4f' }} />,
      color: '#ff4d4f',
      gradient: 'linear-gradient(180deg, #ff4d4f, #ff7875)'
    },
    {
      title: '高级预警',
      value: stats.highLevelAlertCount,
      icon: <ExclamationCircleOutlined style={{ color: '#ff4d4f' }} />,
      color: '#ff4d4f',
      gradient: 'linear-gradient(180deg, #ff4d4f, #ff7875)'
    },
    {
      title: '人流量峰值',
      value: stats.pedestrianPeak,
      icon: <TeamOutlined style={{ color: '#1890ff' }} />,
      color: '#1890ff',
      gradient: 'linear-gradient(180deg, #1890ff, #00d4ff)'
    },
    {
      title: '车流量峰值',
      value: stats.vehiclePeak,
      icon: <CarOutlined style={{ color: '#52c41a' }} />,
      color: '#52c41a',
      gradient: 'linear-gradient(180deg, #52c41a, #13c2c2)'
    }
  ] : [];

  return (
    <div className="tech-card" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div className="corner-decoration corner-tl" />
      <div className="corner-decoration corner-tr" />
      <div className="corner-decoration corner-bl" />
      <div className="corner-decoration corner-br" />

      <div className="panel-header">
        <span className="panel-title">交通监控</span>
        <Space>
          <Button
            size="small"
            icon={<SettingOutlined />}
            onClick={handleStartMonitor}
          >
            阈值设置
          </Button>
          {monitoring ? (
            <Popconfirm
              title="确认停止监控？"
              onConfirm={handleStopMonitor}
              okText="确认"
              cancelText="取消"
            >
              <Button
                type="primary"
                danger
                size="small"
                icon={<PauseCircleOutlined />}
              >
                停止监控
              </Button>
            </Popconfirm>
          ) : (
            <Button
              type="primary"
              size="small"
              icon={<PlayCircleOutlined />}
              onClick={handleStartMonitor}
            >
              启动监控
            </Button>
          )}
          {monitoring && (
            <span style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              fontSize: 12,
              color: '#52c41a'
            }}>
              <span
                className="pulse-glow"
                style={{
                  width: 8,
                  height: 8,
                  borderRadius: '50%',
                  background: '#52c41a',
                  display: 'inline-block'
                }}
              />
              监控中
            </span>
          )}
        </Space>
      </div>

      <Row gutter={[12, 12]} style={{ padding: '0 16px', marginBottom: 12 }}>
        <Col span={12}>
          <Card
            style={{ height: '100%' }}
            styles={{
              body: { padding: 16, textAlign: 'center', background: 'rgba(26, 31, 53, 0.5)' }
            }}
            bordered={false}
          >
            <div style={{ fontSize: 32, marginBottom: 8, color: '#1890ff' }}>
              <TeamOutlined />
            </div>
            <Statistic
              title={<span style={{ fontSize: 12, color: '#8c9cb8' }}>当前人流量</span>}
              value={displayPedestrian}
              valueStyle={{
                fontSize: 32,
                fontWeight: 'bold',
                background: 'linear-gradient(180deg, #1890ff, #00d4ff)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
                lineHeight: 1.2
              }}
            />
            {stats && (
              <div style={{ marginTop: 8, fontSize: 11, color: '#8c9cb8' }}>
                阈值：{stats.pedestrianThreshold} 人
              </div>
            )}
          </Card>
        </Col>
        <Col span={12}>
          <Card
            style={{ height: '100%' }}
            styles={{
              body: { padding: 16, textAlign: 'center', background: 'rgba(26, 31, 53, 0.5)' }
            }}
            bordered={false}
          >
            <div style={{ fontSize: 32, marginBottom: 8, color: '#52c41a' }}>
              <CarOutlined />
            </div>
            <Statistic
              title={<span style={{ fontSize: 12, color: '#8c9cb8' }}>当前车流量</span>}
              value={displayVehicle}
              valueStyle={{
                fontSize: 32,
                fontWeight: 'bold',
                background: 'linear-gradient(180deg, #52c41a, #13c2c2)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
                lineHeight: 1.2
              }}
            />
            {stats && (
              <div style={{ marginTop: 8, fontSize: 11, color: '#8c9cb8' }}>
                阈值：{stats.vehicleThreshold} 辆
              </div>
            )}
          </Card>
        </Col>
      </Row>

      <Row gutter={[8, 8]} style={{ padding: '0 16px', marginBottom: 12 }}>
        {statCards.map((card, index) => (
          <Col span={8} key={index}>
            <Card
              size="small"
              styles={{
                body: { padding: '10px 8px', textAlign: 'center', background: 'rgba(26, 31, 53, 0.5)' }
              }}
              bordered={false}
            >
              <div style={{ fontSize: 18, marginBottom: 4 }}>{card.icon}</div>
              <Statistic
                title={<span style={{ fontSize: 10, color: '#8c9cb8' }}>{card.title}</span>}
                value={card.value}
                valueStyle={{
                  color: card.color,
                  fontSize: 16,
                  fontWeight: 600,
                  background: card.gradient,
                  WebkitBackgroundClip: 'text',
                  WebkitTextFillColor: 'transparent'
                }}
              />
            </Card>
          </Col>
        ))}
      </Row>

      <div style={{ padding: '0 16px', flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 8
        }}>
          <span style={{ color: '#e6f1ff', fontSize: 13, fontWeight: 500 }}>
            <WarningOutlined style={{ color: '#faad14', marginRight: 6 }} />
            预警列表
          </span>
          <span style={{ color: '#8c9cb8', fontSize: 11 }}>
            共 {alerts.length} 条
          </span>
        </div>
        <div style={{ flex: 1, overflow: 'auto', border: '1px solid #1f2940', borderRadius: 8 }}>
          <List
            dataSource={alerts}
            locale={{ emptyText: '暂无预警信息' }}
            loading={loading}
            renderItem={(alert) => (
              <List.Item
                key={alert.id}
                style={{
                  padding: '10px 12px',
                  borderBottom: '1px solid #1f2940',
                  background: alert.handleStatus === 'pending'
                    ? alert.level === 3
                      ? 'rgba(255, 77, 79, 0.1)'
                      : alert.level === 2
                        ? 'rgba(250, 173, 20, 0.1)'
                        : 'rgba(82, 196, 26, 0.05)'
                    : 'transparent',
                  transition: 'all 0.3s'
                }}
              >
                <List.Item.Meta
                  avatar={
                    <div style={{
                      width: 36,
                      height: 36,
                      borderRadius: '50%',
                      background: alert.level === 3
                        ? 'linear-gradient(135deg, #ff4d4f, #ff7875)'
                        : alert.level === 2
                          ? 'linear-gradient(135deg, #faad14, #ffc53d)'
                          : 'linear-gradient(135deg, #52c41a, #73d13d)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      color: '#fff',
                      fontSize: 16
                    }}>
                      {getAlertTypeIcon(alert.alertType)}
                    </div>
                  }
                  title={
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <Space size={[8, 4]} wrap>
                        <span style={{ color: '#e6f1ff', fontSize: 13, fontWeight: 500 }}>
                          {getAlertTypeText(alert.alertType)}预警
                        </span>
                        <Tag color={getLevelColor(alert.level)} style={{ margin: 0 }}>
                          {getLevelText(alert.level)}级
                        </Tag>
                        <Tag
                          color={alert.handleStatus === 'pending' ? 'red' : 'green'}
                          style={{ margin: 0 }}
                        >
                          {alert.handleStatus === 'pending' ? '未处理' : '已处理'}
                        </Tag>
                      </Space>
                      <span style={{ color: '#8c9cb8', fontSize: 11 }}>
                        {dayjs(alert.alertTime).format('HH:mm:ss')}
                      </span>
                    </div>
                  }
                  description={
                    <div style={{ color: '#8c9cb8', fontSize: 12, marginTop: 4 }}>
                      <Space direction="vertical" size={4} style={{ width: '100%' }}>
                        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
                          <span>位置：{alert.location}</span>
                          <span>
                            当前值：
                            <span style={{
                              color: alert.currentValue > alert.thresholdValue ? '#ff4d4f' : '#e6f1ff',
                              fontWeight: 500
                            }}>
                              {alert.currentValue}
                            </span>
                            / 阈值：{alert.thresholdValue}
                          </span>
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <RiseOutlined style={{
                            color: alert.currentValue > alert.thresholdValue ? '#ff4d4f' : '#52c41a'
                          }} />
                          <span style={{
                            color: alert.currentValue > alert.thresholdValue ? '#ff4d4f' : '#52c41a',
                            fontSize: 11
                          }}>
                            超出阈值 {(Math.round((alert.currentValue - alert.thresholdValue) / alert.thresholdValue * 10000) / 100).toFixed(1)}%
                          </span>
                        </div>
                      </Space>
                    </div>
                  }
                />
                {alert.handleStatus === 'pending' && (
                  <Popconfirm
                    title="确认处理该预警？"
                    onConfirm={() => handleAlert(alert.id)}
                    okText="确认"
                    cancelText="取消"
                  >
                    <Button
                      type="primary"
                      size="small"
                      icon={<CheckCircleOutlined />}
                    >
                      处理
                    </Button>
                  </Popconfirm>
                )}
              </List.Item>
            )}
          />
        </div>
      </div>

      <Modal
        title="启动监控配置"
        open={modalVisible}
        onOk={handleConfirmStart}
        onCancel={() => setModalVisible(false)}
        okText="确认启动"
        cancelText="取消"
        width={420}
        styles={{
          header: { background: '#1a1f35', color: '#e6f1ff', borderBottom: '1px solid #1f2940' },
          body: { background: '#141829' },
          content: { background: '#141829', border: '1px solid rgba(24, 144, 255, 0.3)' },
          footer: { background: '#1a1f35', borderTop: '1px solid #1f2940' }
        }}
      >
        <Form
          form={configForm}
          layout="vertical"
          initialValues={{
            pedestrianThreshold: 1000,
            vehicleThreshold: 500,
            windowSize: 300
          }}
          style={{ color: '#e6f1ff' }}
        >
          <Form.Item
            label="人流量阈值"
            name="pedestrianThreshold"
            rules={[{ required: true, message: '请输入人流量阈值' }]}
          >
            <InputNumber
              min={10}
              max={100000}
              step={10}
              style={{ width: '100%', background: '#141829' }}
              addonAfter="人"
            />
          </Form.Item>

          <Form.Item
            label="车流量阈值"
            name="vehicleThreshold"
            rules={[{ required: true, message: '请输入车流量阈值' }]}
          >
            <InputNumber
              min={10}
              max={10000}
              step={10}
              style={{ width: '100%', background: '#141829' }}
              addonAfter="辆"
            />
          </Form.Item>

          <Form.Item
            label="窗口大小"
            name="windowSize"
            rules={[{ required: true, message: '请输入窗口大小' }]}
            help="统计时间窗口大小，单位：秒"
          >
            <InputNumber
              min={60}
              max={3600}
              step={60}
              style={{ width: '100%', background: '#141829' }}
              addonAfter="秒"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TrafficMonitorPanel;
