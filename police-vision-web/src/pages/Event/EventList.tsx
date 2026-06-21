import React, { useState, useEffect, useCallback } from 'react';
import {
  Table,
  Form,
  Button,
  Space,
  Tag,
  DatePicker,
  Input,
  Select,
  Modal,
  message,
  Popconfirm
} from 'antd';
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  EyeOutlined,
  EditOutlined,
  DeleteOutlined,
  TeamOutlined,
  SafetyCertificateOutlined,
  EnvironmentOutlined,
  IdcardOutlined,
  BellOutlined,
  FileTextOutlined,
  PlayCircleOutlined,
  StopOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import EventForm from './EventForm';
import { SecEvent, EventCreateDTO, EventUpdateDTO, EventQueryDTO } from '@/types';
import {
  getEventList,
  createEvent,
  updateEvent,
  deleteEvent,
  startEvent,
  endEvent
} from '@/services/eventApi';

const { RangePicker } = DatePicker;
const { Option } = Select;

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

const EventList: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<Event[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const [formModalVisible, setFormModalVisible] = useState(false);
  const [formMode, setFormMode] = useState<'create' | 'edit'>('create');
  const [currentEvent, setCurrentEvent] = useState<Event | null>(null);
  const [formLoading, setFormLoading] = useState(false);

  const loadData = useCallback(async (params?: EventQueryDTO) => {
    setLoading(true);
    try {
      const res = await getEventList({
        page,
        size: pageSize,
        ...params
      });
      if (res.data) {
        setData(res.data.list || []);
        setTotal(res.data.total || 0);
      }
    } catch (error) {
      console.error('Load event list error:', error);
      message.error('加载活动列表失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleSearch = () => {
    const values = form.getFieldsValue();
    const params: EventQueryDTO = {};

    if (values.name) {
      params.name = values.name;
    }
    if (values.status !== undefined && values.status !== null) {
      params.status = values.status;
    }
    if (values.timeRange && values.timeRange.length === 2) {
      params.startTime = values.timeRange[0].format('YYYY-MM-DD HH:mm:ss');
      params.endTime = values.timeRange[1].format('YYYY-MM-DD HH:mm:ss');
    }

    setPage(1);
    loadData(params);
  };

  const handleReset = () => {
    form.resetFields();
    setPage(1);
    loadData();
  };

  const handleCreate = () => {
    setFormMode('create');
    setCurrentEvent(null);
    setFormModalVisible(true);
  };

  const handleEdit = (record: Event) => {
    setFormMode('edit');
    setCurrentEvent(record);
    setFormModalVisible(true);
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteEvent(id);
      message.success('删除成功');
      loadData();
    } catch (error) {
      console.error('Delete event error:', error);
      message.error('删除失败');
    }
  };

  const handleStart = async (id: string) => {
    try {
      await startEvent(id);
      message.success('活动已启动');
      loadData();
    } catch (error) {
      console.error('Start event error:', error);
      message.error('启动活动失败');
    }
  };

  const handleEnd = async (id: string) => {
    try {
      await endEvent(id);
      message.success('活动已结束');
      loadData();
    } catch (error) {
      console.error('End event error:', error);
      message.error('结束活动失败');
    }
  };

  const handleFormSubmit = async (data: EventCreateDTO | EventUpdateDTO) => {
    setFormLoading(true);
    try {
      if (formMode === 'create') {
        await createEvent(data as EventCreateDTO);
        message.success('创建成功');
      } else {
        await updateEvent((data as EventUpdateDTO).id, data as EventUpdateDTO);
        message.success('更新成功');
      }
      setFormModalVisible(false);
      loadData();
    } catch (error) {
      console.error('Submit event error:', error);
      message.error(formMode === 'create' ? '创建失败' : '更新失败');
    } finally {
      setFormLoading(false);
    }
  };

  const handleViewDetail = (record: Event) => {
    message.info(`查看活动详情: ${record.name}`);
  };

  const handleResourceAllocation = (record: Event) => {
    message.info(`资源分配: ${record.name}`);
  };

  const handleSecurityPlan = (record: Event) => {
    message.info(`安保方案: ${record.name}`);
  };

  const handleRouteManagement = (record: Event) => {
    message.info(`路线管理: ${record.name}`);
  };

  const handlePassManagement = (record: Event) => {
    message.info(`通行证管理: ${record.name}`);
  };

  const handleWarningMonitor = (record: Event) => {
    message.info(`预警监控: ${record.name}`);
  };

  const handleReport = (record: Event) => {
    message.info(`报告: ${record.name}`);
  };

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

  const columns = [
    {
      title: '活动名称',
      dataIndex: 'name',
      key: 'name',
      width: 200,
      render: (text: string, record: Event) => (
        <a
          onClick={() => handleViewDetail(record)}
          style={{ color: '#1890ff' }}
        >
          {text}
        </a>
      )
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 120,
      render: (type: string) => getTypeName(type)
    },
    {
      title: '级别',
      dataIndex: 'level',
      key: 'level',
      width: 140,
      render: (level: string) => (
        <span style={{ color: getLevelColor(level), fontWeight: 500 }}>
          {getLevelName(level)}
        </span>
      )
    },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      key: 'startTime',
      width: 170,
      render: (time: string) => dayjs(time).format('YYYY-MM-DD HH:mm')
    },
    {
      title: '结束时间',
      dataIndex: 'endTime',
      key: 'endTime',
      width: 170,
      render: (time: string) => dayjs(time).format('YYYY-MM-DD HH:mm')
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: number) => getStatusTag(status)
    },
    {
      title: '操作',
      key: 'actions',
      width: 420,
      fixed: 'right' as const,
      render: (_: any, record: Event) => (
        <Space size={[4, 8]} wrap>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record)}
          >
            详情
          </Button>
          <Button
            type="link"
            size="small"
            icon={<TeamOutlined />}
            onClick={() => handleResourceAllocation(record)}
          >
            资源分配
          </Button>
          <Button
            type="link"
            size="small"
            icon={<SafetyCertificateOutlined />}
            onClick={() => handleSecurityPlan(record)}
          >
            安保方案
          </Button>
          <Button
            type="link"
            size="small"
            icon={<EnvironmentOutlined />}
            onClick={() => handleRouteManagement(record)}
          >
            路线管理
          </Button>
          <Button
            type="link"
            size="small"
            icon={<IdcardOutlined />}
            onClick={() => handlePassManagement(record)}
          >
            通行证
          </Button>
          <Button
            type="link"
            size="small"
            icon={<BellOutlined />}
            onClick={() => handleWarningMonitor(record)}
          >
            预警监控
          </Button>
          <Button
            type="link"
            size="small"
            icon={<FileTextOutlined />}
            onClick={() => handleReport(record)}
          >
            报告
          </Button>
          {record.status === 0 && (
            <Button
              type="link"
              size="small"
              icon={<PlayCircleOutlined />}
              onClick={() => handleStart(record.id)}
              style={{ color: '#52c41a' }}
            >
              启动
            </Button>
          )}
          {record.status === 1 && (
            <Button
              type="link"
              size="small"
              icon={<StopOutlined />}
              onClick={() => handleEnd(record.id)}
              style={{ color: '#faad14' }}
            >
              结束
            </Button>
          )}
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确定要删除这个活动吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  const handleTableChange = (pagination: any) => {
    setPage(pagination.current);
    setPageSize(pagination.pageSize);
  };

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
        <span className="panel-title">活动管理</span>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={handleCreate}
          style={{
            background: 'linear-gradient(135deg, #1890ff 0%, #00d4ff 100%)',
            border: 'none'
          }}
        >
          新增活动
        </Button>
      </div>

      <div
        style={{
          background: 'rgba(26, 31, 53, 0.6)',
          border: '1px solid rgba(24, 144, 255, 0.15)',
          borderRadius: 8,
          padding: 16,
          marginBottom: 20
        }}
      >
        <Form
          form={form}
          layout="inline"
          style={{ color: '#e6f1ff' }}
          labelCol={{ style: { color: '#8c9cb8' } }}
        >
          <Form.Item name="name" label="活动名称">
            <Input
              placeholder="请输入活动名称"
              allowClear
              style={{
                width: 200,
                background: 'rgba(20, 24, 41, 0.8)',
                border: '1px solid rgba(24, 144, 255, 0.3)',
                color: '#e6f1ff'
              }}
            />
          </Form.Item>

          <Form.Item name="status" label="状态">
            <Select
              placeholder="全部状态"
              allowClear
              style={{
                width: 150,
                background: 'rgba(20, 24, 41, 0.8)'
              }}
              dropdownStyle={{
                background: '#1a1f35',
                border: '1px solid rgba(24, 144, 255, 0.3)'
              }}
            >
              {statusOptions.map(opt => (
                <Option key={opt.value} value={opt.value}>
                  {opt.label}
                </Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item name="timeRange" label="活动时间">
            <RangePicker
              showTime
              style={{
                width: 380,
                background: 'rgba(20, 24, 41, 0.8)',
                border: '1px solid rgba(24, 144, 255, 0.3)',
                color: '#e6f1ff'
              }}
            />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button
                type="primary"
                icon={<SearchOutlined />}
                onClick={handleSearch}
                style={{
                  background: 'linear-gradient(135deg, #1890ff 0%, #00d4ff 100%)',
                  border: 'none'
                }}
              >
                查询
              </Button>
              <Button
                icon={<ReloadOutlined />}
                onClick={handleReset}
                style={{
                  background: 'rgba(24, 144, 255, 0.1)',
                  border: '1px solid rgba(24, 144, 255, 0.3)',
                  color: '#1890ff'
                }}
              >
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </div>

      <div
        style={{
          background: 'rgba(26, 31, 53, 0.4)',
          border: '1px solid rgba(24, 144, 255, 0.1)',
          borderRadius: 8,
          overflow: 'hidden'
        }}
      >
        <Table
          columns={columns}
          dataSource={data}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1200 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条`,
            style: { color: '#8c9cb8', padding: '12px 16px' }
          }}
          onChange={handleTableChange}
          style={{ color: '#e6f1ff' }}
        />
      </div>

      <EventForm
        open={formModalVisible}
        mode={formMode}
        initialData={currentEvent || undefined}
        onCancel={() => setFormModalVisible(false)}
        onSubmit={handleFormSubmit}
        confirmLoading={formLoading}
      />
    </div>
  );
};

export default EventList;
