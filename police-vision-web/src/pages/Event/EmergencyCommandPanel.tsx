import React, { useState, useEffect } from 'react';
import {
  Tabs,
  Card,
  Row,
  Col,
  Button,
  Space,
  Tag,
  Modal,
  Form,
  Select,
  Input,
  InputNumber,
  Switch,
  List,
  Avatar,
  Statistic,
  Drawer,
  Timeline,
  Divider,
  Empty,
  Radio,
  Tooltip,
  Popconfirm,
  Spin,
  message,
  Descriptions,
  Badge
} from 'antd';
import {
  ThunderboltOutlined,
  SafetyCertificateOutlined,
  SendOutlined,
  VideoCameraOutlined,
  EnvironmentOutlined,
  TeamOutlined,
  CameraOutlined,
  ToolOutlined,
  HistoryOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  WarningOutlined,
  PlayCircleOutlined,
  RobotOutlined,
  FileProtectOutlined,
  InfoCircleOutlined,
  ReloadOutlined,
  SettingOutlined,
  PhoneOutlined,
  RiseOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  EmergencyPlanTemplate,
  EmergencyPlanStartResult,
  EmergencyCommand,
  EmergencyCommandCreateDTO,
  EmergencyCommandDetail,
  EmergencyResourceResult,
  EmergencyResourcePolice,
  EmergencyResourceCamera,
  EmergencySupply,
  CommandTimelineItem
} from '@/types';
import {
  getEmergencyPlanTemplates,
  startEmergencyPlan,
  dispatchEmergencyCommand,
  getEmergencyCommandList,
  getEmergencyCommandDetail,
  queryEmergencyResources,
  feedbackEmergencyCommand
} from '@/services/eventApi';
import WebrtcVideoPanel from './WebrtcVideoPanel';
import FenceDrawPanel from './FenceDrawPanel';

const { TextArea } = Input;
const { Option } = Select;
const { TabPane } = Tabs;

interface EmergencyCommandPanelProps {
  eventId: string;
  eventName?: string;
  eventLevel?: string;
}

const COMMAND_STATUS_MAP: Record<number, { text: string; color: string; icon: React.ReactNode }> = {
  0: { text: '已创建', color: 'default', icon: <ClockCircleOutlined /> },
  1: { text: '已下达', color: 'blue', icon: <SendOutlined /> },
  2: { text: '已接收', color: 'cyan', icon: <InfoCircleOutlined /> },
  3: { text: '执行中', color: 'processing', icon: <PlayCircleOutlined /> },
  4: { text: '已反馈', color: 'geekblue', icon: <RobotOutlined /> },
  5: { text: '已完成', color: 'success', icon: <CheckCircleOutlined /> },
  6: { text: '已取消', color: 'default', icon: <InfoCircleOutlined /> },
  7: { text: '已超时', color: 'error', icon: <WarningOutlined /> }
};

const PRIORITY_MAP: Record<number, { text: string; color: string; icon: React.ReactNode }> = {
  1: { text: '紧急', color: 'red', icon: <ExclamationCircleOutlined /> },
  2: { text: '高', color: 'orange', icon: <WarningOutlined /> },
  3: { text: '普通', color: 'blue', icon: <InfoCircleOutlined /> },
  4: { text: '低', color: 'green', icon: <InfoCircleOutlined /> }
};

const EmergencyCommandPanel: React.FC<EmergencyCommandPanelProps> = ({
  eventId,
  eventName,
  eventLevel
}) => {
  const [templates, setTemplates] = useState<EmergencyPlanTemplate[]>([]);
  const [planModalVisible, setPlanModalVisible] = useState(false);
  const [commandModalVisible, setCommandModalVisible] = useState(false);
  const [detailDrawerVisible, setDetailDrawerVisible] = useState(false);
  const [startLoading, setStartLoading] = useState(false);
  const [templatesLoading, setTemplatesLoading] = useState(false);
  const [commandsLoading, setCommandsLoading] = useState(false);
  const [resourcesLoading, setResourcesLoading] = useState(false);
  const [planForm] = Form.useForm();
  const [commandForm] = Form.useForm();
  const [commands, setCommands] = useState<EmergencyCommand[]>([]);
  const [selectedCommand, setSelectedCommand] = useState<EmergencyCommandDetail | null>(null);
  const [planResult, setPlanResult] = useState<EmergencyPlanStartResult | null>(null);
  const [resources, setResources] = useState<EmergencyResourceResult | null>(null);
  const [activeTab, setActiveTab] = useState('overview');

  useEffect(() => {
    if (eventId) {
      fetchTemplates();
      fetchCommands();
    }
  }, [eventId]);

  const fetchTemplates = async () => {
    setTemplatesLoading(true);
    try {
      const res = await getEmergencyPlanTemplates();
      setTemplates(res.data || []);
    } catch (error: any) {
      console.error('获取预案模板失败:', error.message);
    } finally {
      setTemplatesLoading(false);
    }
  };

  const fetchCommands = async () => {
    if (!eventId) return;
    setCommandsLoading(true);
    try {
      const res = await getEmergencyCommandList({ eventId, page: 1, size: 20 });
      setCommands(res.data?.list || []);
    } catch (error: any) {
      console.error('获取指令列表失败:', error.message);
    } finally {
      setCommandsLoading(false);
    }
  };

  const handleStartPlan = async () => {
    try {
      const values = await planForm.validateFields();
      setStartLoading(true);
      const dto: any = {
        eventId,
        templateCode: values.templateCode,
        resourceRadius: values.resourceRadius,
        autoAllocateResources: values.autoAllocateResources,
        autoStartVideoConference: values.autoStartVideoConference
      };
      const res = await startEmergencyPlan(dto);
      setPlanResult(res.data);
      if (res.data?.fallback) {
        message.warning(res.data.message || '预案启动降级处理，请稍后查看结果');
      } else {
        message.success('应急预案启动成功！');
      }
      setPlanModalVisible(false);
      planForm.resetFields();
      fetchResources(values.resourceRadius || 500);
      fetchCommands();
    } catch (error: any) {
      if (error.errorFields) return;
      message.error(error.message || '预案启动失败');
    } finally {
      setStartLoading(false);
    }
  };

  const handleDispatchCommand = async () => {
    try {
      const values = await commandForm.validateFields();
      const dto: EmergencyCommandCreateDTO = {
        eventId,
        commandTitle: values.commandTitle,
        commandContent: values.commandContent,
        priority: values.priority,
        deadlineMinutes: values.deadlineMinutes,
        receiverNames: values.receiverNames
      };
      await dispatchEmergencyCommand(dto);
      message.success('指令已下达并广播至所有参战单位！');
      setCommandModalVisible(false);
      commandForm.resetFields();
      fetchCommands();
    } catch (error: any) {
      if (error.errorFields) return;
      message.error(error.message || '指令下达失败');
    }
  };

  const fetchResources = async (radius: number = 500) => {
    if (!eventId) return;
    setResourcesLoading(true);
    try {
      const res = await queryEmergencyResources({ eventId, radiusMeters: radius });
      setResources(res.data);
    } catch (error: any) {
      console.error('查询应急资源失败:', error.message);
    } finally {
      setResourcesLoading(false);
    }
  };

  const handleViewCommandDetail = async (cmd: EmergencyCommand) => {
    try {
      const res = await getEmergencyCommandDetail(cmd.id);
      setSelectedCommand(res.data);
      setDetailDrawerVisible(true);
    } catch (error: any) {
      message.error(error.message || '获取指令详情失败');
    }
  };

  const handleCommandFeedback = async (cmdId: string, status: number) => {
    try {
      await feedbackEmergencyCommand({
        commandId: cmdId,
        operatorId: '1001',
        operatorName: '当前用户',
        operatorDept: '指挥中心',
        toStatus: status,
        operateRemark: '状态更新'
      });
      message.success('状态更新成功');
      fetchCommands();
      if (selectedCommand) {
        handleViewCommandDetail({ id: cmdId } as EmergencyCommand);
      }
    } catch (error: any) {
      message.error(error.message || '状态更新失败');
    }
  };

  const buildTimelineItems = (detail: EmergencyCommandDetail | null): CommandTimelineItem[] => {
    if (!detail) return [];
    const items: CommandTimelineItem[] = [];
    const statusOrder = [0, 1, 2, 3, 4, 5];
    const statusMap: Record<number, { key: string; text: string; color: string; dotColor: string }> = {
      0: { key: 'created', text: '指令创建', color: 'default', dotColor: '#8c8c8c' },
      1: { key: 'dispatched', text: '指令下达', color: 'blue', dotColor: '#1890ff' },
      2: { key: 'received', text: '单位接收', color: 'cyan', dotColor: '#13c2c2' },
      3: { key: 'executing', text: '执行处理', color: 'processing', dotColor: '#1890ff' },
      4: { key: 'feedback', text: '结果反馈', color: 'geekblue', dotColor: '#2f54eb' },
      5: { key: 'completed', text: '闭环完成', color: 'success', dotColor: '#52c41a' }
    };
    statusOrder.forEach((statusCode) => {
      const meta = statusMap[statusCode];
      const log = detail.statusLogs.find((l) => l.toStatus === statusCode);
      if (log || statusCode <= (detail.command.status || 0)) {
        items.push({
          key: meta.key,
          status: statusCode,
          statusText: meta.text,
          statusColor: meta.color,
          time: log?.operateTime ? dayjs(log.operateTime).format('YYYY-MM-DD HH:mm:ss') : '-',
          operatorName: log?.operatorName,
          operatorDept: log?.operatorDept,
          remark: log?.operateRemark,
          dotColor: meta.dotColor
        });
      }
    });
    return items;
  };

  const overviewStats = (
    <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
      <Col span={6}>
        <Card size="small" styles={{ body: { padding: 12, textAlign: 'center', background: 'rgba(26, 31, 53, 0.5)' } }} bordered={false}>
          <div style={{ fontSize: 24, marginBottom: 4, color: '#1890ff' }}><TeamOutlined /></div>
          <Statistic
            title={<span style={{ fontSize: 11, color: '#8c9cb8' }}>可用警力</span>}
            value={resources?.policeCount || 0}
            valueStyle={{ color: '#1890ff', fontSize: 22, fontWeight: 600 }}
          />
        </Card>
      </Col>
      <Col span={6}>
        <Card size="small" styles={{ body: { padding: 12, textAlign: 'center', background: 'rgba(26, 31, 53, 0.5)' } }} bordered={false}>
          <div style={{ fontSize: 24, marginBottom: 4, color: '#52c41a' }}><CameraOutlined /></div>
          <Statistic
            title={<span style={{ fontSize: 11, color: '#8c9cb8' }}>覆盖摄像头</span>}
            value={resources?.cameraCount || 0}
            valueStyle={{ color: '#52c41a', fontSize: 22, fontWeight: 600 }}
          />
        </Card>
      </Col>
      <Col span={6}>
        <Card size="small" styles={{ body: { padding: 12, textAlign: 'center', background: 'rgba(26, 31, 53, 0.5)' } }} bordered={false}>
          <div style={{ fontSize: 24, marginBottom: 4, color: '#faad14' }}><ToolOutlined /></div>
          <Statistic
            title={<span style={{ fontSize: 11, color: '#8c9cb8' }}>应急物资</span>}
            value={resources?.supplyCount || 0}
            valueStyle={{ color: '#faad14', fontSize: 22, fontWeight: 600 }}
          />
        </Card>
      </Col>
      <Col span={6}>
        <Card size="small" styles={{ body: { padding: 12, textAlign: 'center', background: 'rgba(26, 31, 53, 0.5)' } }} bordered={false}>
          <div style={{ fontSize: 24, marginBottom: 4, color: '#ff4d4f' }}><SendOutlined /></div>
          <Statistic
            title={<span style={{ fontSize: 11, color: '#8c9cb8' }}>调度指令</span>}
            value={commands.length}
            valueStyle={{ color: '#ff4d4f', fontSize: 22, fontWeight: 600 }}
          />
        </Card>
      </Col>
    </Row>
  );

  const getPoliceStatusColor = (status?: number) => {
    if (status === 1) return 'green';
    if (status === 2) return 'orange';
    return 'default';
  };

  const getPoliceStatusText = (status?: number) => {
    if (status === 1) return '在岗';
    if (status === 2) return '出警中';
    return '离线';
  };

  return (
    <div className="tech-card" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div className="corner-decoration corner-tl" />
      <div className="corner-decoration corner-tr" />
      <div className="corner-decoration corner-bl" />
      <div className="corner-decoration corner-br" />

      <div className="panel-header">
        <Space>
          <ThunderboltOutlined style={{ color: '#faad14', fontSize: 18 }} />
          <span className="panel-title">应急指挥调度</span>
          {planResult && (
            <Tag color="success" icon={<CheckCircleOutlined />}>
              预案已启动 · {planResult.planName}
            </Tag>
          )}
        </Space>
        <Space wrap>
          <Button
            size="small"
            icon={<ReloadOutlined />}
            onClick={() => {
              fetchCommands();
              if (planResult) {
                fetchResources(planResult.resourceRadius || 500);
              }
            }}
          >
            刷新
          </Button>
          <Button
            size="small"
            icon={<SafetyCertificateOutlined />}
            onClick={() => setPlanModalVisible(true)}
          >
            预案启动
          </Button>
          <Button
            type="primary"
            danger
            size="small"
            icon={<SendOutlined />}
            onClick={() => setCommandModalVisible(true)}
          >
            下达指令
          </Button>
        </Space>
      </div>

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        type="card"
        style={{ margin: '0 16px' }}
        size="small"
        items={[
          {
            key: 'overview',
            label: <span><ThunderboltOutlined /> 总览</span>,
            children: (
              <div style={{ flex: 1, overflow: 'auto', padding: '0 16px 16px' }}>
                {overviewStats}

                {planResult && (
                  <Card
                    size="small"
                    style={{ marginBottom: 12 }}
                    styles={{ body: { padding: 12, background: 'rgba(82, 196, 26, 0.08)' } }}
                    bordered={false}
                    title={
                      <Space>
                        <FileProtectOutlined style={{ color: '#52c41a' }} />
                        <span style={{ color: '#e6f1ff', fontSize: 13 }}>应急预案启动信息</span>
                      </Space>
                    }
                  >
                    <Descriptions column={3} size="small" labelStyle={{ color: '#8c9cb8' }} contentStyle={{ color: '#e6f1ff' }}>
                      <Descriptions.Item label="预案名称">{planResult.planName}</Descriptions.Item>
                      <Descriptions.Item label="启动时间">{dayjs(planResult.startTime).format('YYYY-MM-DD HH:mm:ss')}</Descriptions.Item>
                      <Descriptions.Item label="搜索半径">{planResult.resourceRadius} 米</Descriptions.Item>
                      <Descriptions.Item label="警力分配">
                        <Tag color="blue">{planResult.policeCount} 人</Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label="摄像头">
                        <Tag color="green">{planResult.cameraCount} 个</Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label="物资调配">
                        <Tag color="orange">{planResult.supplyCount} 项</Tag>
                      </Descriptions.Item>
                    </Descriptions>
                    {planResult.videoRoomId && (
                      <div style={{ marginTop: 8 }}>
                        <Tag color="geekblue" icon={<VideoCameraOutlined />}>
                          视频会议室：{planResult.videoRoomId}
                        </Tag>
                      </div>
                    )}
                  </Card>
                )}

                <Divider orientation="left" style={{ borderColor: '#1f2940', color: '#8c9cb8', fontSize: 12 }}>
                  最新调度指令
                </Divider>

                <Spin spinning={commandsLoading}>
                  {commands.length === 0 ? (
                    <Empty description="暂无指令，点击「下达指令」开始指挥" style={{ padding: 30 }} />
                  ) : (
                    <List
                      size="small"
                      dataSource={commands.slice(0, 8)}
                      renderItem={(cmd) => {
                        const statusInfo = COMMAND_STATUS_MAP[cmd.status] || COMMAND_STATUS_MAP[0];
                        const priorityInfo = PRIORITY_MAP[cmd.priority] || PRIORITY_MAP[3];
                        return (
                          <List.Item
                            key={cmd.id}
                            style={{
                              padding: '10px 12px',
                              marginBottom: 6,
                              background: 'rgba(26, 31, 53, 0.5)',
                              borderRadius: 6,
                              border: `1px solid ${cmd.status === 5 ? '#52c41a33' : '#1f2940'}`
                            }}
                            actions={[
                              <Button
                                type="link"
                                size="small"
                                icon={<HistoryOutlined />}
                                onClick={() => handleViewCommandDetail(cmd)}
                              >
                                闭环追踪
                              </Button>
                            ]}
                          >
                            <List.Item.Meta
                              avatar={
                                <Badge
                                  color={priorityInfo.color}
                                  dot
                                  offset={[-2, 2]}
                                >
                                  <Avatar
                                    size={36}
                                    style={{
                                      background: `linear-gradient(135deg, ${cmd.status === 5 ? '#52c41a' : '#1890ff'}, ${cmd.status === 5 ? '#73d13d' : '#096dd9'})`
                                    }}
                                    icon={statusInfo.icon}
                                  />
                                </Badge>
                              }
                              title={
                                <Space size={[8, 4]} wrap>
                                  <span style={{ color: '#e6f1ff', fontSize: 13, fontWeight: 500 }}>
                                    {cmd.commandTitle}
                                  </span>
                                  <Tag color={priorityInfo.color} style={{ margin: 0 }}>
                                    {priorityInfo.icon} {priorityInfo.text}
                                  </Tag>
                                  <Tag color={statusInfo.color} style={{ margin: 0 }}>
                                    {statusInfo.text}
                                  </Tag>
                                </Space>
                              }
                              description={
                                <div style={{ fontSize: 11, color: '#8c9cb8', marginTop: 4 }}>
                                  <Space size={[12, 4]} wrap>
                                    <span><ClockCircleOutlined /> {dayjs(cmd.dispatchTime || cmd.createTime).format('HH:mm:ss')}</span>
                                    <span><SendOutlined /> {cmd.senderName || '指挥中心'}</span>
                                    <span><RiseOutlined /> 时限 {cmd.deadlineMinutes}分钟</span>
                                    {cmd.receiverNames && (
                                      <span><TeamOutlined /> {cmd.receiverNames}</span>
                                    )}
                                  </Space>
                                </div>
                              }
                            />
                          </List.Item>
                        );
                      }}
                    />
                  )}
                </Spin>
              </div>
            )
          },
          {
            key: 'resources',
            label: <span><EnvironmentOutlined /> 应急资源</span>,
            children: (
              <div style={{ flex: 1, overflow: 'auto', padding: '0 16px 16px' }}>
                <div style={{ marginBottom: 12 }}>
                  <Space>
                    <Radio.Group
                      size="small"
                      value={resources?.radiusMeters || 500}
                      onChange={(e) => fetchResources(e.target.value)}
                    >
                      <Radio.Button value={300}>300米</Radio.Button>
                      <Radio.Button value={500}>500米</Radio.Button>
                      <Radio.Button value={1000}>1公里</Radio.Button>
                      <Radio.Button value={2000}>2公里</Radio.Button>
                    </Radio.Group>
                    <Button size="small" icon={<ReloadOutlined />} onClick={() => fetchResources(resources?.radiusMeters || 500)} loading={resourcesLoading}>
                      查询
                    </Button>
                  </Space>
                </div>

                <Spin spinning={resourcesLoading}>
                  <Divider orientation="left" style={{ borderColor: '#1f2940', color: '#1890ff', fontSize: 12 }}>
                    <TeamOutlined /> 周边警力 ({resources?.policeCount || 0})
                  </Divider>
                  <List
                    size="small"
                    grid={{ gutter: 8, xs: 1, sm: 2 }}
                    locale={{ emptyText: '暂无警力资源' }}
                    dataSource={resources?.policeList?.slice(0, 12) || []}
                    renderItem={(p: EmergencyResourcePolice) => (
                      <List.Item key={p.policeId}>
                        <Card size="small" styles={{ body: { padding: 10, background: 'rgba(26, 31, 53, 0.5)' } }} bordered={false}>
                          <Space size={8}>
                            <Avatar size={32} style={{ background: '#1890ff33', color: '#1890ff' }}>
                              {p.name?.charAt(0)}
                            </Avatar>
                            <div style={{ flex: 1 }}>
                              <div style={{ color: '#e6f1ff', fontSize: 13, fontWeight: 500 }}>{p.name}</div>
                              <div style={{ fontSize: 11, color: '#8c9cb8', marginTop: 2 }}>
                                {p.policeNo || ''} {p.dept || ''}
                              </div>
                            </div>
                          </Space>
                          <Divider style={{ margin: '8px 0', borderColor: '#1f2940' }} />
                          <Space size={[12, 4]} wrap style={{ fontSize: 11 }}>
                            <Tag color={getPoliceStatusColor(p.status)} style={{ margin: 0 }}>
                              {getPoliceStatusText(p.status)}
                            </Tag>
                            <span style={{ color: '#faad14' }}>距离 {Math.round(p.distanceMeters)}米</span>
                            {p.phone && (
                              <Tooltip title={p.phone}>
                                <PhoneOutlined style={{ color: '#52c41a' }} />
                              </Tooltip>
                            )}
                          </Space>
                        </Card>
                      </List.Item>
                    )}
                  />

                  <Divider orientation="left" style={{ borderColor: '#1f2940', color: '#52c41a', fontSize: 12 }}>
                    <CameraOutlined /> 周边摄像头 ({resources?.cameraCount || 0})
                  </Divider>
                  <List
                    size="small"
                    grid={{ gutter: 8, xs: 1, sm: 2 }}
                    locale={{ emptyText: '暂无摄像头资源' }}
                    dataSource={resources?.cameraList?.slice(0, 12) || []}
                    renderItem={(c: EmergencyResourceCamera) => (
                      <List.Item key={c.cameraId}>
                        <Card size="small" styles={{ body: { padding: 10, background: 'rgba(26, 31, 53, 0.5)' } }} bordered={false}>
                          <Space size={8} style={{ width: '100%' }}>
                            <div
                              style={{
                                width: 32, height: 32, borderRadius: 4,
                                background: 'linear-gradient(135deg, #52c41a, #13c2c2)',
                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                color: '#fff'
                              }}
                            >
                              <CameraOutlined />
                            </div>
                            <div style={{ flex: 1, minWidth: 0 }}>
                              <div
                                style={{
                                  color: '#e6f1ff', fontSize: 13, fontWeight: 500,
                                  overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap'
                                }}
                                title={c.name}
                              >
                                {c.name}
                              </div>
                              <div style={{ fontSize: 11, color: '#8c9cb8', marginTop: 2 }}>
                                {c.address || '位置未知'}
                              </div>
                            </div>
                          </Space>
                          <Divider style={{ margin: '8px 0', borderColor: '#1f2940' }} />
                          <Space size={[12, 4]} wrap style={{ fontSize: 11 }}>
                            <Tag color={c.status === 1 ? 'success' : 'default'} style={{ margin: 0 }}>
                              {c.status === 1 ? '在线' : '离线'}
                            </Tag>
                            <span style={{ color: '#faad14' }}>距离 {Math.round(c.distanceMeters)}米</span>
                            {c.rtspUrl && <VideoCameraOutlined style={{ color: '#1890ff' }} />}
                          </Space>
                        </Card>
                      </List.Item>
                    )}
                  />

                  <Divider orientation="left" style={{ borderColor: '#1f2940', color: '#faad14', fontSize: 12 }}>
                    <ToolOutlined /> 应急物资 ({resources?.supplyCount || 0})
                  </Divider>
                  <List
                    size="small"
                    grid={{ gutter: 8, xs: 1, sm: 2, md: 3 }}
                    locale={{ emptyText: '暂无应急物资' }}
                    dataSource={resources?.supplyList?.slice(0, 15) || []}
                    renderItem={(s: EmergencySupply) => (
                      <List.Item key={s.id}>
                        <Card
                          size="small"
                          styles={{ body: { padding: 10, background: 'rgba(26, 31, 53, 0.5)' } }}
                          bordered={false}
                          title={
                            <Space size={4} style={{ fontSize: 12, color: '#e6f1ff' }}>
                              <ToolOutlined style={{ color: '#faad14' }} />
                              <span style={{ fontWeight: 500 }}>{s.supplyName}</span>
                            </Space>
                          }
                          extra={
                            <Tag color="orange" style={{ margin: 0, fontSize: 11 }}>
                              {s.quantity}{s.unit}
                            </Tag>
                          }
                        >
                          <div style={{ fontSize: 11, color: '#8c9cb8' }}>
                            <div>存放：{s.address || '-'}</div>
                            <div>距离：{Math.round(s.distanceMeters || 0)}米</div>
                            {s.contactPerson && <div>联络：{s.contactPerson} {s.contactPhone || ''}</div>}
                          </div>
                        </Card>
                      </List.Item>
                    )}
                  />
                </Spin>
              </div>
            )
          },
          {
            key: 'video',
            label: <span><VideoCameraOutlined /> 视频会商</span>,
            children: <WebrtcVideoPanel eventId={eventId} eventName={eventName} />
          },
          {
            key: 'fence',
            label: <span><EnvironmentOutlined /> 图上作业</span>,
            children: <FenceDrawPanel eventId={eventId} />
          }
        ]}
      />

      <Modal
        title={
          <Space>
            <SafetyCertificateOutlined style={{ color: '#52c41a' }} />
            <span>一键启动应急预案</span>
          </Space>
        }
        open={planModalVisible}
        onOk={handleStartPlan}
        onCancel={() => setPlanModalVisible(false)}
        okText="立即启动"
        cancelText="取消"
        confirmLoading={startLoading}
        width={560}
        styles={{
          header: { background: '#1a1f35', color: '#e6f1ff', borderBottom: '1px solid #1f2940' },
          body: { background: '#141829' },
          content: { background: '#141829', border: '1px solid rgba(82, 196, 26, 0.3)' },
          footer: { background: '#1a1f35', borderTop: '1px solid #1f2940' }
        }}
      >
        <Spin spinning={templatesLoading}>
          <Form form={planForm} layout="vertical" initialValues={{
            templateCode: templates[0]?.code || 'terrorism',
            resourceRadius: 500,
            autoAllocateResources: true,
            autoStartVideoConference: true
          }} style={{ color: '#e6f1ff' }}>
            <Form.Item label="选择预案模板" name="templateCode" rules={[{ required: true, message: '请选择预案模板' }]}>
              <Select placeholder="选择预设预案模板">
                {templates.map((t) => (
                  <Option key={t.code} value={t.code}>
                    <Space size={8}>
                      <Tag color={t.priority <= 2 ? 'red' : t.priority <= 4 ? 'orange' : 'blue'} style={{ margin: 0 }}>
                        优先级{t.priority}
                      </Tag>
                      <span>{t.name}</span>
                      <span style={{ color: '#8c9cb8', fontSize: 11 }}>{t.nacosConfigKey}</span>
                    </Space>
                  </Option>
                ))}
              </Select>
            </Form.Item>

            {planForm.getFieldValue('templateCode') && (
              <Card size="small" style={{ marginBottom: 12 }} bordered={false}
                styles={{ body: { padding: 10, background: 'rgba(26, 31, 53, 0.6)', fontSize: 11, color: '#8c9cb8' } }}>
                <InfoCircleOutlined style={{ color: '#1890ff' }} />
                {' '}{templates.find((t) => t.code === planForm.getFieldValue('templateCode'))?.description}
              </Card>
            )}

            <Row gutter={12}>
              <Col span={12}>
                <Form.Item label="资源搜索半径（米）" name="resourceRadius">
                  <InputNumber min={100} max={5000} step={100} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="事件信息" style={{ fontSize: 11, color: '#8c9cb8' }}>
                  <div style={{ padding: '8px 12px', background: 'rgba(26, 31, 53, 0.5)', borderRadius: 4, fontSize: 12 }}>
                    <div>事件ID：{eventId}</div>
                    <div>名称：{eventName || '-'}</div>
                    <div>级别：{eventLevel || '-'}</div>
                  </div>
                </Form.Item>
              </Col>
            </Row>

            <Row gutter={12}>
              <Col span={12}>
                <Form.Item name="autoAllocateResources" valuePropName="checked" label="自动调配资源">
                  <Switch checkedChildren="启用" unCheckedChildren="关闭" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="autoStartVideoConference" valuePropName="checked" label="启动视频会商">
                  <Switch checkedChildren="启用" unCheckedChildren="关闭" />
                </Form.Item>
              </Col>
            </Row>

            <Card size="small" bordered={false}
              styles={{ body: { padding: 8, background: 'rgba(250, 173, 20, 0.08)', fontSize: 11, color: '#faad14' } }}>
              <WarningOutlined /> 启动后将触发 Sentinel 熔断保护，所有操作均有日志记录
            </Card>
          </Form>
        </Spin>
      </Modal>

      <Modal
        title={
          <Space>
            <SendOutlined style={{ color: '#ff4d4f' }} />
            <span>下达应急指令</span>
          </Space>
        }
        open={commandModalVisible}
        onOk={handleDispatchCommand}
        onCancel={() => setCommandModalVisible(false)}
        okText="立即下达（广播）"
        cancelText="取消"
        width={600}
        styles={{
          header: { background: '#1a1f35', color: '#e6f1ff', borderBottom: '1px solid #1f2940' },
          body: { background: '#141829' },
          content: { background: '#141829', border: '1px solid rgba(255, 77, 79, 0.3)' },
          footer: { background: '#1a1f35', borderTop: '1px solid #1f2940' }
        }}
      >
        <Form form={commandForm} layout="vertical" initialValues={{
          priority: 3,
          deadlineMinutes: 60,
          receiverNames: ['一线指挥部', '交通警察大队', '辖区派出所', '特警支队']
        }} style={{ color: '#e6f1ff' }}>
          <Row gutter={12}>
            <Col span={16}>
              <Form.Item label="指令标题" name="commandTitle" rules={[{ required: true, message: '请输入指令标题' }]}>
                <Input placeholder="如：立即启动事发地点周边封控措施" maxLength={50} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="优先级" name="priority">
                <Select>
                  <Option value={1}>
                    <Tag color="red" style={{ margin: 0 }}>紧急</Tag>
                  </Option>
                  <Option value={2}>
                    <Tag color="orange" style={{ margin: 0 }}>高</Tag>
                  </Option>
                  <Option value={3}>
                    <Tag color="blue" style={{ margin: 0 }}>普通</Tag>
                  </Option>
                  <Option value={4}>
                    <Tag color="green" style={{ margin: 0 }}>低</Tag>
                  </Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Form.Item label="指令内容" name="commandContent" rules={[{ required: true, message: '请输入指令内容' }]}>
            <TextArea rows={4} placeholder="请详细描述指令要求、处置措施及预期效果..." maxLength={500} showCount />
          </Form.Item>

          <Row gutter={12}>
            <Col span={12}>
              <Form.Item label="时限要求（分钟）" name="deadlineMinutes">
                <InputNumber min={5} max={1440} step={5} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="接收单位" name="receiverNames">
                <Select mode="tags" placeholder="输入或选择接收单位">
                  <Option value="一线指挥部">一线指挥部</Option>
                  <Option value="交通警察大队">交通警察大队</Option>
                  <Option value="辖区派出所">辖区派出所</Option>
                  <Option value="特警支队">特警支队</Option>
                  <Option value="消防救援">消防救援</Option>
                  <Option value="医疗救护">医疗救护</Option>
                  <Option value="情报部门">情报部门</Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Card size="small" bordered={false}
            styles={{ body: { padding: 8, background: 'rgba(24, 144, 255, 0.08)', fontSize: 11, color: '#1890ff' } }}>
            <InfoCircleOutlined /> 指令将通过 RocketMQ 广播至所有参战单位 App，并记录完整闭环追踪时间线
          </Card>
        </Form>
      </Modal>

      <Drawer
        title={
          <Space>
            <HistoryOutlined style={{ color: '#1890ff' }} />
            <span>指令闭环追踪</span>
          </Space>
        }
        placement="right"
        width={520}
        open={detailDrawerVisible}
        onClose={() => setDetailDrawerVisible(false)}
        styles={{
          header: { background: '#1a1f35', color: '#e6f1ff', borderBottom: '1px solid #1f2940' },
          body: { background: '#141829', padding: 16 },
          content: { background: '#141829' }
        }}
      >
        {selectedCommand && (
          <div style={{ color: '#e6f1ff' }}>
            <Card size="small" style={{ marginBottom: 12 }} bordered={false}
              styles={{ body: { padding: 12, background: 'rgba(26, 31, 53, 0.6)' } }}>
              <Descriptions column={1} size="small" labelStyle={{ color: '#8c9cb8' }} contentStyle={{ color: '#e6f1ff' }}>
                <Descriptions.Item label="指令编号">{selectedCommand.command.commandNo}</Descriptions.Item>
                <Descriptions.Item label="指令标题">
                  <Space size={8}>
                    {selectedCommand.command.commandTitle}
                    {(() => {
                      const s = COMMAND_STATUS_MAP[selectedCommand.command.status];
                      return <Tag color={s.color}>{s.icon} {s.text}</Tag>;
                    })()}
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="指令内容">
                  <div style={{ whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>
                    {selectedCommand.command.commandContent}
                  </div>
                </Descriptions.Item>
                <Descriptions.Item label="接收单位">{selectedCommand.command.receiverNames || '-'}</Descriptions.Item>
                <Descriptions.Item label="优先级">
                  {(() => {
                    const p = PRIORITY_MAP[selectedCommand.command.priority];
                    return <Tag color={p.color}>{p.icon} {p.text}</Tag>;
                  })()}
                </Descriptions.Item>
                <Descriptions.Item label="时限">{selectedCommand.command.deadlineMinutes} 分钟</Descriptions.Item>
                <Descriptions.Item label="超时次数">{selectedCommand.command.timeoutCount || 0} 次</Descriptions.Item>
                {selectedCommand.command.feedbackContent && (
                  <Descriptions.Item label="执行反馈">{selectedCommand.command.feedbackContent}</Descriptions.Item>
                )}
              </Descriptions>
            </Card>

            <Divider orientation="left" style={{ borderColor: '#1f2940', color: '#8c9cb8', fontSize: 12 }}>
              状态流转时间线
            </Divider>

            <Card size="small" bordered={false} styles={{ body: { padding: 12, background: 'rgba(26, 31, 53, 0.6)' } }}>
              <Timeline
                mode="left"
                items={buildTimelineItems(selectedCommand).map((item) => ({
                  dot: <div style={{
                    width: 14, height: 14, borderRadius: '50%',
                    background: item.dotColor, border: '2px solid #141829'
                  }} />,
                  color: item.dotColor,
                  children: (
                    <div style={{ paddingBottom: 12 }}>
                      <Space size={8} wrap style={{ marginBottom: 4 }}>
                        <span style={{ color: '#e6f1ff', fontWeight: 500, fontSize: 13 }}>{item.statusText}</span>
                        <Tag color={item.statusColor} style={{ margin: 0, fontSize: 11 }}>{item.time}</Tag>
                      </Space>
                      {item.operatorName && (
                        <div style={{ fontSize: 11, color: '#8c9cb8' }}>
                          <span>{item.operatorName}</span>
                          {item.operatorDept && <span> · {item.operatorDept}</span>}
                          {item.remark && (
                            <div style={{ marginTop: 4, color: '#a6adc8' }}>
                              <InfoCircleOutlined style={{ marginRight: 4 }} />{item.remark}
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  )
                }))}
              />
            </Card>

            <Divider style={{ borderColor: '#1f2940', margin: '16px 0' }} />

            <Space wrap>
              {selectedCommand.command.status < 5 && (
                <>
                  {selectedCommand.command.status < 2 && (
                    <Button
                      type="primary"
                      size="small"
                      icon={<InfoCircleOutlined />}
                      onClick={() => handleCommandFeedback(selectedCommand.command.id, 2)}
                    >
                      标记已接收
                    </Button>
                  )}
                  {selectedCommand.command.status >= 2 && selectedCommand.command.status < 3 && (
                    <Button
                      type="primary"
                      size="small"
                      icon={<PlayCircleOutlined />}
                      onClick={() => handleCommandFeedback(selectedCommand.command.id, 3)}
                    >
                      开始执行
                    </Button>
                  )}
                  {selectedCommand.command.status >= 3 && selectedCommand.command.status < 4 && (
                    <Button
                      size="small"
                      icon={<RobotOutlined />}
                      onClick={() => handleCommandFeedback(selectedCommand.command.id, 4)}
                    >
                      反馈结果
                    </Button>
                  )}
                  {selectedCommand.command.status >= 4 && selectedCommand.command.status < 5 && (
                    <Popconfirm
                      title="确认指令执行完毕并关闭闭环？"
                      onConfirm={() => handleCommandFeedback(selectedCommand.command.id, 5)}
                      okText="确认"
                      cancelText="取消"
                    >
                      <Button
                        type="primary"
                        size="small"
                        style={{ background: '#52c41a', borderColor: '#52c41a' }}
                        icon={<CheckCircleOutlined />}
                      >
                        闭环完成
                      </Button>
                    </Popconfirm>
                  )}
                </>
              )}
              <Button
                size="small"
                icon={<SettingOutlined />}
                onClick={() => fetchCommands()}
              >
                刷新
              </Button>
            </Space>
          </div>
        )}
      </Drawer>
    </div>
  );
};

export default EmergencyCommandPanel;
