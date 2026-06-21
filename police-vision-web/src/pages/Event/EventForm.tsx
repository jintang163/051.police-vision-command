import React, { useEffect, useState } from 'react';
import { Modal, Form, Input, Select, DatePicker, Button, Space, Tag, message } from 'antd';
import { EnvironmentOutlined, SaveOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import EventMapDrawer from './EventMapDrawer';
import { Position, EventCreateDTO, EventUpdateDTO } from '@/types';

const { TextArea } = Input;
const { RangePicker } = DatePicker;
const { Option } = Select;

interface EventFormProps {
  open: boolean;
  mode: 'create' | 'edit';
  initialData?: any;
  onCancel: () => void;
  onSubmit: (data: EventCreateDTO | EventUpdateDTO) => void;
  confirmLoading?: boolean;
}

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

const EventForm: React.FC<EventFormProps> = ({
  open,
  mode,
  initialData,
  onCancel,
  onSubmit,
  confirmLoading = false
}) => {
  const [form] = Form.useForm();
  const [mapDrawerOpen, setMapDrawerOpen] = useState(false);
  const [polygonPoints, setPolygonPoints] = useState<Position[]>([]);

  useEffect(() => {
    if (open && initialData) {
      form.setFieldsValue({
        name: initialData.name,
        type: initialData.type,
        level: initialData.level,
        timeRange: [
          dayjs(initialData.startTime),
          dayjs(initialData.endTime)
        ],
        organizer: initialData.organizer,
        description: initialData.description
      });
      setPolygonPoints(initialData.polygonPoints || []);
    } else if (open) {
      form.resetFields();
      setPolygonPoints([]);
    }
  }, [open, initialData, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const [startTime, endTime] = values.timeRange;

      const data: EventCreateDTO | EventUpdateDTO = {
        ...(mode === 'edit' && initialData ? { id: initialData.id } : {}),
        name: values.name,
        type: values.type,
        level: values.level,
        startTime: startTime.format('YYYY-MM-DD HH:mm:ss'),
        endTime: endTime.format('YYYY-MM-DD HH:mm:ss'),
        organizer: values.organizer,
        description: values.description,
        polygonPoints
      } as any;

      onSubmit(data);
    } catch (error) {
      console.error('Form validation failed:', error);
    }
  };

  const handleMapDrawerConfirm = (points: Position[]) => {
    setPolygonPoints(points);
    message.success(`已选择 ${points.length} 个顶点');
  };

  return (
    <>
      <Modal
        title={mode === 'create' ? '创建活动' : '编辑活动'}
        open={open}
        onCancel={onCancel}
        onOk={handleSubmit}
        confirmLoading={confirmLoading}
        width={720}
        okText="保存"
        cancelText="取消"
        styles={{
          header: {
            background: 'linear-gradient(90deg, #1a1f35 0%, #141829 100%)',
            color: '#e6f1ff',
            borderBottom: '1px solid #1f2940'
          },
          body: {
            background: '#141829',
            padding: '24px'
          },
          content: {
            background: '#141829',
            border: '1px solid rgba(24, 144, 255, 0.3)',
            borderRadius: 8
          },
          footer: {
            background: '#1a1f35',
            borderTop: '1px solid #1f2940'
          }
        }}
        okButtonProps={{
          style: {
            background: 'linear-gradient(135deg, #1890ff 0%, #00d4ff 100%)',
            border: 'none'
          }
        }}
      >
        <Form
          form={form}
          layout="vertical"
          style={{ color: '#e6f1ff' }}
          labelCol={{ style: { color: '#8c9cb8' } }}
        >
          <Form.Item
            name="name"
            label="活动名称"
            rules={[{ required: true, message: '请输入活动名称' }]}
          >
            <Input
              placeholder="请输入活动名称"
              style={{
                background: 'rgba(26, 31, 53, 0.8)',
                border: '1px solid rgba(24, 144, 255, 0.3)',
                color: '#e6f1ff'
              }}
            />
          </Form.Item>

          <Space direction="horizontal" style={{ width: '100%' }}>
            <Form.Item
              name="type"
              label="活动类型"
              style={{ flex: 1, marginRight: 12 }}
            >
              <Select
                placeholder="请选择活动类型"
                allowClear
                style={{
                  background: 'rgba(26, 31, 53, 0.8)'
                }}
                dropdownStyle={{
                  background: '#1a1f35',
                  border: '1px solid rgba(24, 144, 255, 0.3)'
                }}
              >
                {eventTypeOptions.map((opt => (
                  <Option key={opt.value} value={opt.value}>
                    {opt.label}
                  </Option>
                ))}
              </Select>
            </Form.Item>

            <Form.Item
              name="level"
              label="安保级别"
              style={{ flex: 1, marginLeft: 12 }}
            >
              <Select
                placeholder="请选择安保级别"
                allowClear
                dropdownStyle={{
                  background: '#1a1f35',
                  border: '1px solid rgba(24, 144, 255, 0.3)'
                }}
              >
                {eventLevelOptions.map((opt) => (
                  <Option key={opt.value} value={opt.value}>
                    {opt.label}
                  </Option>
                ))}
              </Select>
            </Form.Item>
          </Space>

          <Form.Item
            name="timeRange"
            label="活动时间"
            rules={[{ required: true, message: '请选择活动时间' }]}
          >
            <RangePicker
              showTime
              style={{
                width: '100%',
                background: 'rgba(26, 31, 53, 0.8)',
                border: '1px solid rgba(24, 144, 255, 0.3)',
                color: '#e6f1ff'
              }}
            />
          </Form.Item>

          <Form.Item name="organizer" label="主办单位">
            <Input
              placeholder="请输入主办单位"
              style={{
                background: 'rgba(26, 31, 53, 0.8)',
                border: '1px solid rgba(24, 144, 255, 0.3)',
                color: '#e6f1ff'
              }}
            />
          </Form.Item>

          <Form.Item label="活动区域">
            <Space direction="vertical" style={{ width: '100%' }}>
              <Button
                icon={<EnvironmentOutlined />}
                onClick={() => setMapDrawerOpen(true)}
                style={{
                  background: 'rgba(24, 144, 255, 0.1)',
                  border: '1px solid rgba(24, 144, 255, 0.5)',
                  color: '#1890ff'
                }}
              >
                在地图上圈选活动区域
              </Button>
              {polygonPoints.length > 0 && (
                <Space size={[8, 8]} wrap>
                  <Tag color="blue">
                    已选择 {polygonPoints.length} 个顶点
                  </Tag>
                  {polygonPoints.slice(0, 3).map((point, index) => (
                    <Tag key={index} color="cyan">
                      {point.lng.toFixed(4)}, {point.lat.toFixed(4)}
                    </Tag>
                  ))}
                  {polygonPoints.length > 3 && (
                      <Tag color="default">...等{polygonPoints.length - 3}个更多</Tag>
                    )}
                </Space>
              )}
            </Space>
          </Form.Item>

          <Form.Item name="description" label="活动描述">
            <TextArea
              rows={4}
              placeholder="请输入活动描述"
              style={{
                background: 'rgba(26, 31, 53, 0.8)',
                border: '1px solid rgba(24, 144, 255, 0.3)',
                color: '#e6f1ff',
                resize: 'none'
              }}
            />
          </Form.Item>
        </Form>
      </Modal>

      <EventMapDrawer
        open={mapDrawerOpen}
        onClose={() => setMapDrawerOpen(false)}
        onConfirm={handleMapDrawerConfirm}
        initialPoints={polygonPoints}
      />
    </>
  );
};

export default EventForm;
