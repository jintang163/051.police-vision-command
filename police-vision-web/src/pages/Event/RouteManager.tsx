import React, { useState, useEffect } from 'react';
import {
  Table,
  Button,
  Space,
  Modal,
  Form,
  Input,
  InputNumber,
  Switch,
  Drawer,
  Tag,
  message,
  Popconfirm,
  Card,
  List,
  Avatar,
  Tooltip
} from 'antd';
import {
  PlusOutlined,
  EyeOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  StopOutlined,
  DeleteOutlined,
  VideoCameraOutlined,
  EnvironmentOutlined,
  SafetyOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';

import { Route, RouteCamera, Camera, PatrolStatus } from '@/types';
import {
  getRouteList,
  createRoute,
  deleteRoute,
  startPatrol,
  pausePatrol,
  resumePatrol,
  stopPatrol,
  getPatrolStatus,
  getRouteCameras,
  getEventCameraList
} from '@/services/eventApi';

interface RouteManagerProps {
  eventId: string;
}

const RouteManager: React.FC<RouteManagerProps> = ({ eventId }) => {
  const [loading, setLoading] = useState(false);
  const [routes, setRoutes] = useState<Route[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const [modalVisible, setModalVisible] = useState(false);
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  const [drawerVisible, setDrawerVisible] = useState(false);
  const [selectedRoute, setSelectedRoute] = useState<Route | null>(null);
  const [routeCameras, setRouteCameras] = useState<RouteCamera[]>([]);
  const [patrolStatus, setPatrolStatus] = useState<PatrolStatus | null>(null);

  const [cameraList, setCameraList] = useState<Camera[]>([]);
  const [selectedCameraIds, setSelectedCameraIds] = useState<string[]>([]);

  const [autoSelectCamera, setAutoSelectCamera] = useState(true);

  const loadRoutes = async () => {
    setLoading(true);
    try {
      const res = await getRouteList(eventId, { page, size: pageSize });
      setRoutes(res.data);
      setTotal(res.total);
    } catch (error) {
      console.error('Load routes error:', error);
      message.error('加载路线列表失败');
    } finally {
      setLoading(false);
    }
  };

  const loadCameras = async () => {
    try {
      const res = await getEventCameraList(eventId);
      setCameraList(res.data);
    } catch (error) {
      console.error('Load cameras error:', error);
    }
  };

  useEffect(() => {
    loadRoutes();
    loadCameras();
  }, [eventId, page, pageSize]);

  const handleCreateRoute = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const res = await createRoute({
        eventId,
        routeName: values.routeName,
        startPoint: values.startPoint,
        endPoint: values.endPoint,
        waypoints: values.waypoints,
        autoSelectCamera,
        cameraIds: selectedCameraIds,
        playDuration: values.playDuration || 10
      });
      message.success('路线创建成功');
      setModalVisible(false);
      form.resetFields();
      setSelectedCameraIds([]);
      setAutoSelectCamera(true);
      loadRoutes();
    } catch (error) {
      console.error('Create route error:', error);
      message.error('创建路线失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteRoute = async (routeId: string) => {
    try {
      await deleteRoute(routeId);
      message.success('删除成功');
      loadRoutes();
    } catch (error) {
      console.error('Delete route error:', error);
      message.error('删除失败');
    }
  };

  const handleStartPatrol = async (routeId: string) => {
    try {
      await startPatrol(routeId);
      message.success('轮巡已开始');
      loadRoutes();
      if (selectedRoute?.id === routeId) {
        loadPatrolStatus(routeId);
      }
    } catch (error) {
      console.error('Start patrol error:', error);
      message.error('开始轮巡失败');
    }
  };

  const handleStopPatrol = async (routeId: string) => {
    try {
      await stopPatrol(routeId);
      message.success('轮巡已停止');
      loadRoutes();
      if (selectedRoute?.id === routeId) {
        loadPatrolStatus(routeId);
      }
    } catch (error) {
      console.error('Stop patrol error:', error);
      message.error('停止轮巡失败');
    }
  };

  const handlePausePatrol = async (routeId: string) => {
    try {
      await pausePatrol(routeId);
      message.success('轮巡已暂停');
      if (selectedRoute?.id === routeId) {
        loadPatrolStatus(routeId);
      }
    } catch (error) {
      console.error('Pause patrol error:', error);
      message.error('暂停轮巡失败');
    }
  };

  const handleResumePatrol = async (routeId: string) => {
    try {
      await resumePatrol(routeId);
      message.success('轮巡已恢复');
      if (selectedRoute?.id === routeId) {
        loadPatrolStatus(routeId);
      }
    } catch (error) {
      console.error('Resume patrol error:', error);
      message.error('恢复轮巡失败');
    }
  };

  const loadPatrolStatus = async (routeId: string) => {
    try {
      const res = await getPatrolStatus(routeId);
      setPatrolStatus(res.data);
    } catch (error) {
      console.error('Load patrol status error:', error);
    }
  };

  const loadRouteCameras = async (routeId: string) => {
    try {
      const res = await getRouteCameras(routeId);
      setRouteCameras(res.data);
    } catch (error) {
      console.error('Load route cameras error:', error);
    }
  };

  const handleViewDetail = async (route: Route) => {
    setSelectedRoute(route);
    setDrawerVisible(true);
    loadRouteCameras(route.id);
    loadPatrolStatus(route.id);
  };

  const getStatusTag = (status: number) => {
    switch (status) {
      case 1:
        return <Tag color="green">启用</Tag>;
      case 2:
        return <Tag color="red">停用</Tag>;
      default:
        return <Tag>未知</Tag>;
    }
  };

  const getPatrolStatusTag = (status?: string) => {
    switch (status) {
      case 'running':
        return <Tag color="green" icon={<PlayCircleOutlined />}>轮巡中</Tag>;
      case 'paused':
        return <Tag color="orange" icon={<PauseCircleOutlined />}>已暂停</Tag>;
      case 'stopped':
        return <Tag color="red" icon={<StopOutlined />}>已停止</Tag>;
      default:
        return <Tag color="default">空闲</Tag>;
    }
  };

  const columns: ColumnsType<Route> = [
    {
      title: '路线名称',
      dataIndex: 'routeName',
      key: 'routeName',
      render: (text) => (
        <Space>
          <EnvironmentOutlined style={{ color: '#1890ff' }} />
          <span style={{ color: '#e6f1ff' }}>{text}</span>
        </Space>
      )
    },
    {
      title: '起点',
      dataIndex: 'startPoint',
      key: 'startPoint',
      ellipsis: true,
      width: 150
    },
    {
      title: '终点',
      dataIndex: 'endPoint',
      key: 'endPoint',
      ellipsis: true,
      width: 150
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status) => getStatusTag(status)
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (time) => dayjs(time).format('YYYY-MM-DD HH:mm:ss')
    },
    {
      title: '操作',
      key: 'action',
      width: 280,
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record)}
          >
            详情
          </Button>
          {record.status === 1 && (
            <>
              <Button
                type="link"
                size="small"
                icon={<PlayCircleOutlined />}
                onClick={() => handleStartPatrol(record.id)}
              >
                开始轮巡
              </Button>
              <Button
                type="link"
                size="small"
                danger
                icon={<StopOutlined />}
                onClick={() => handleStopPatrol(record.id)}
              >
                停止轮巡
              </Button>
            </>
          )}
          <Popconfirm
            title="确定删除该路线?"
            onConfirm={() => handleDeleteRoute(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  const cameraColumns: ColumnsType<Camera> = [
    {
      title: '选择',
      key: 'select',
      width: 60,
      render: (_, record) => (
        <input
          type="checkbox"
          checked={selectedCameraIds.includes(record.id)}
          onChange={(e) => {
            if (e.target.checked) {
              setSelectedCameraIds([...selectedCameraIds, record.id]);
            } else {
              setSelectedCameraIds(selectedCameraIds.filter(id => id !== record.id));
            }
          }}
        />
      )
    },
    {
      title: '摄像头名称',
      dataIndex: 'name',
      key: 'name'
    },
    {
      title: '地址',
      dataIndex: 'address',
      key: 'address',
      ellipsis: true
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status) => (
        <Tag color={status === 'online' ? 'green' : 'red'}>
          {status === 'online' ? '在线' : '离线'}
        </Tag>
      )
    }
  ];

  return (
    <div className="tech-card" style={{ padding: 16, minHeight: '100%' }}>
      <div className="corner-decoration corner-tl" />
      <div className="corner-decoration corner-tr" />
      <div className="corner-decoration corner-bl" />
      <div className="corner-decoration corner-br" />

      <div className="panel-header" style={{ marginBottom: 16 }}>
        <span className="panel-title">路线管理</span>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadRoutes}>
            刷新
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setModalVisible(true)}
          >
            新增路线
          </Button>
        </Space>
      </div>

      <Table
        rowKey="id"
        loading={loading}
        dataSource={routes}
        columns={columns}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `共 ${total} 条`,
          onChange: (p, ps) => {
            setPage(p);
            setPageSize(ps);
          }
        }}
        style={{ background: 'transparent' }}
      />

      <Modal
        title={
          <Space>
            <SafetyOutlined style={{ color: '#1890ff' }} />
            <span>新增路线</span>
          </Space>
        }
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false);
          form.resetFields();
          setSelectedCameraIds([]);
          setAutoSelectCamera(true);
        }}
        onOk={handleCreateRoute}
        confirmLoading={submitting}
        width={800}
        okText="创建"
        cancelText="取消"
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ playDuration: 10 }}
        >
          <Form.Item
            name="routeName"
            label="路线名称"
            rules={[{ required: true, message: '请输入路线名称' }]}
          >
            <Input placeholder="请输入路线名称" />
          </Form.Item>

          <div style={{
            padding: 16,
            background: 'rgba(24, 144, 255, 0.05)',
            border: '1px dashed rgba(24, 144, 255, 0.3)',
            borderRadius: 4,
            marginBottom: 16
          }}>
            <div style={{ color: '#8c9cb8', fontSize: 12, marginBottom: 8 }}>
              <EnvironmentOutlined style={{ marginRight: 4 }} />
              地图选点区域（点击地图选择起点、终点和途经点）
            </div>
            <div style={{
              height: 200,
              background: 'linear-gradient(135deg, #0a1628 0%, #0d1f3c 100%)',
              borderRadius: 4,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              border: '1px solid #1f2940'
            }}>
              <div style={{ textAlign: 'center', color: '#8c9cb8' }}>
                <EnvironmentOutlined style={{ fontSize: 32, color: '#1890ff', marginBottom: 8 }} />
                <p style={{ margin: 0 }}>地图组件占位</p>
                <p style={{ margin: 0, fontSize: 12 }}>支持折线绘制路线</p>
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', gap: 16 }}>
            <Form.Item
              name="startPoint"
              label="起点"
              style={{ flex: 1 }}
              rules={[{ required: true, message: '请选择起点' }]}
            >
              <Input placeholder="点击地图选择" />
            </Form.Item>
            <Form.Item
              name="endPoint"
              label="终点"
              style={{ flex: 1 }}
              rules={[{ required: true, message: '请选择终点' }]}
            >
              <Input placeholder="点击地图选择" />
            </Form.Item>
          </div>

          <Form.Item
            name="waypoints"
            label="途经点"
          >
            <Input.TextArea
              rows={2}
              placeholder="途经点列表，用分号分隔"
            />
          </Form.Item>

          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: 16,
            padding: '12px 16px',
            background: 'rgba(24, 144, 255, 0.05)',
            borderRadius: 4
          }}>
            <span>自动选点（沿路线自动选摄像头）</span>
            <Switch
              checked={autoSelectCamera}
              onChange={setAutoSelectCamera}
              defaultChecked
            />
          </div>

          {!autoSelectCamera && (
            <div style={{ marginBottom: 16 }}>
              <div style={{ color: '#e6f1ff', marginBottom: 8, fontWeight: 500 }}>
                <VideoCameraOutlined style={{ color: '#1890ff', marginRight: 4 }} />
                手动选择摄像头
              </div>
              <div style={{
                maxHeight: 200,
                overflow: 'auto',
                border: '1px solid #1f2940',
                borderRadius: 4
              }}>
                <Table
                  rowKey="id"
                  dataSource={cameraList}
                  columns={cameraColumns}
                  pagination={false}
                  size="small"
                />
              </div>
            </div>
          )}

          <Form.Item
            name="playDuration"
            label="摄像头播放时长（秒）"
          >
            <InputNumber min={5} max={300} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={
          <Space>
            <EnvironmentOutlined style={{ color: '#1890ff' }} />
            <span>路线详情</span>
          </Space>
        }
        placement="right"
        width={420}
        open={drawerVisible}
        onClose={() => setDrawerVisible(false)}
      >
        {selectedRoute && (
          <div style={{ color: '#e6f1ff' }}>
            <Card
              size="small"
              title="基本信息"
              style={{
                marginBottom: 16,
                background: 'rgba(24, 144, 255, 0.05)',
                border: '1px solid rgba(24, 144, 255, 0.3)'
              }}
            >
              <p style={{ margin: '4px 0' }}>
                <span style={{ color: '#8c9cb8' }}>路线名称：</span>
                {selectedRoute.routeName}
              </p>
              <p style={{ margin: '4px 0' }}>
                <span style={{ color: '#8c9cb8' }}>起点：</span>
                {selectedRoute.startPoint}
              </p>
              <p style={{ margin: '4px 0' }}>
                <span style={{ color: '#8c9cb8' }}>终点：</span>
                {selectedRoute.endPoint}
              </p>
              <p style={{ margin: '4px 0' }}>
                <span style={{ color: '#8c9cb8' }}>状态：</span>
                {getStatusTag(selectedRoute.status)}
              </p>
              <p style={{ margin: '4px 0' }}>
                <span style={{ color: '#8c9cb8' }}>创建时间：</span>
                {dayjs(selectedRoute.createTime).format('YYYY-MM-DD HH:mm:ss')}
              </p>
            </Card>

            <Card
              size="small"
              title="轮巡状态"
              extra={patrolStatus && getPatrolStatusTag(patrolStatus.status)}
              style={{
                marginBottom: 16,
                background: 'rgba(114, 46, 209, 0.05)',
                border: '1px solid rgba(114, 46, 209, 0.3)'
              }}
            >
              {patrolStatus ? (
                <>
                  <p style={{ margin: '4px 0' }}>
                    <span style={{ color: '#8c9cb8' }}>当前摄像头：</span>
                    {patrolStatus.currentCameraName || '-'}
                  </p>
                  <p style={{ margin: '4px 0' }}>
                    <span style={{ color: '#8c9cb8' }}>开始时间：</span>
                    {patrolStatus.startTime ? dayjs(patrolStatus.startTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
                  </p>
                  <div style={{ marginTop: 12 }}>
                    <Space>
                      {patrolStatus.status === 'idle' && (
                        <Button
                          type="primary"
                          size="small"
                          icon={<PlayCircleOutlined />}
                          onClick={() => handleStartPatrol(selectedRoute.id)}
                        >
                          开始轮巡
                        </Button>
                      )}
                      {patrolStatus.status === 'running' && (
                        <>
                          <Button
                            size="small"
                            icon={<PauseCircleOutlined />}
                            onClick={() => handlePausePatrol(selectedRoute.id)}
                          >
                            暂停
                          </Button>
                          <Button
                            size="small"
                            danger
                            icon={<StopOutlined />}
                            onClick={() => handleStopPatrol(selectedRoute.id)}
                          >
                            停止
                          </Button>
                        </>
                      )}
                      {patrolStatus.status === 'paused' && (
                        <>
                          <Button
                            type="primary"
                            size="small"
                            icon={<PlayCircleOutlined />}
                            onClick={() => handleResumePatrol(selectedRoute.id)}
                          >
                            恢复
                          </Button>
                          <Button
                            size="small"
                            danger
                            icon={<StopOutlined />}
                            onClick={() => handleStopPatrol(selectedRoute.id)}
                          >
                            停止
                          </Button>
                        </>
                      )}
                      {patrolStatus.status === 'stopped' && (
                        <Button
                          type="primary"
                          size="small"
                          icon={<PlayCircleOutlined />}
                          onClick={() => handleStartPatrol(selectedRoute.id)}
                        >
                          重新开始
                        </Button>
                      )}
                    </Space>
                  </div>
                </>
              ) : (
                <p style={{ color: '#8c9cb8', margin: 0 }}>暂无轮巡状态</p>
              )}
            </Card>

            <Card
              size="small"
              title={
                <Space>
                  <VideoCameraOutlined style={{ color: '#1890ff' }} />
                  <span>沿线摄像头 ({routeCameras.length})</span>
                </Space>
              }
              style={{
                background: 'rgba(82, 196, 26, 0.05)',
                border: '1px solid rgba(82, 196, 26, 0.3)'
              }}
            >
              <List
                dataSource={routeCameras}
                size="small"
                renderItem={(item, index) => (
                  <List.Item
                    style={{
                      padding: '8px 0',
                      borderBottom: '1px solid #1f2940',
                      background: patrolStatus?.currentCameraIndex === index
                        ? 'rgba(24, 144, 255, 0.1)'
                        : 'transparent'
                    }}
                  >
                    <List.Item.Meta
                      avatar={
                        <Avatar
                          style={{
                            background: patrolStatus?.currentCameraIndex === index
                              ? '#1890ff'
                              : '#52c41a',
                            width: 28,
                            height: 28,
                            fontSize: 12
                          }}
                        >
                          {index + 1}
                        </Avatar>
                      }
                      title={
                        <span style={{
                          color: patrolStatus?.currentCameraIndex === index
                            ? '#1890ff'
                            : '#e6f1ff',
                          fontSize: 13
                        }}>
                          {item.cameraName}
                        </span>
                      }
                      description={
                        <span style={{ fontSize: 12, color: '#8c9cb8' }}>
                          播放时长: {item.playDuration}秒
                        </span>
                      }
                    />
                    {patrolStatus?.currentCameraIndex === index && (
                      <Tag color="blue">播放中</Tag>
                    )}
                  </List.Item>
                )}
              />
            </Card>
          </div>
        )}
      </Drawer>
    </div>
  );
};

export default RouteManager;
