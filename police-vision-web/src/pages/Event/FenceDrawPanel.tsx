import React, { useState, useEffect, useRef } from 'react';
import {
  Card,
  Row,
  Col,
  Button,
  Space,
  Tag,
  Empty,
  List,
  Modal,
  Form,
  Input,
  Select,
  InputNumber,
  message,
  Tooltip,
  Divider,
  Popconfirm,
  Switch,
  Dropdown,
  ColorPicker,
  Empty as EmptyIcon,
  Spin,
  Alert
} from 'antd';
import {
  EnvironmentOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  InfoCircleOutlined,
  SketchOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  BgColorsOutlined,
  AimOutlined,
  EyeOutlined,
  EyeInvisibleOutlined,
  CopyOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import type { Color } from 'antd/es/color-picker';
import {
  EmergencyFence,
  EmergencyFenceCreateDTO
} from '@/types';
import {
  createEmergencyFence,
  updateEmergencyFence,
  deleteEmergencyFence,
  getEmergencyFenceList,
  batchDeleteEmergencyFences
} from '@/services/eventApi';

interface FenceDrawPanelProps {
  eventId: string;
}

const { Option } = Select;

const FENCE_TYPE_OPTIONS = [
  { value: 'blockade', label: '封控区', desc: '核心区域，禁止任何无关人员出入', color: '#ff4d4f', fill: 'rgba(255,77,79,0.2)' },
  { value: 'control', label: '管控区', desc: '周边管理区域，凭证件出入', color: '#faad14', fill: 'rgba(250,173,20,0.2)' },
  { value: 'prevention', label: '防范区', desc: '外围警戒区域，人员流动管控', color: '#52c41a', fill: 'rgba(82,196,26,0.2)' },
  { value: 'assembly', label: '集结点', desc: '警力/物资集合地点', color: '#1890ff', fill: 'rgba(24,144,255,0.2)' },
  { value: 'checkpoint', label: '检查点', desc: '人员车辆安检卡点', color: '#722ed1', fill: 'rgba(114,46,209,0.2)' }
];

const FenceDrawPanel: React.FC<FenceDrawPanelProps> = ({ eventId }) => {
  const [fences, setFences] = useState<EmergencyFence[]>([]);
  const [loading, setLoading] = useState(false);
  const [drawModalVisible, setDrawModalVisible] = useState(false);
  const [isDrawing, setIsDrawing] = useState(false);
  const [drawForm] = Form.useForm();
  const [editingFence, setEditingFence] = useState<EmergencyFence | null>(null);
  const [selectedFenceId, setSelectedFenceId] = useState<string | null>(null);

  useEffect(() => {
    if (eventId) {
      fetchFences();
    }
  }, [eventId]);

  const fetchFences = async () => {
    if (!eventId) return;
    setLoading(true);
    try {
      const res = await getEmergencyFenceList(eventId);
      setFences(res.data || []);
    } catch (error: any) {
      console.error('获取封控区列表失败:', error.message);
    } finally {
      setLoading(false);
    }
  };

  const getFenceTypeMeta = (typeCode: string) => {
    return FENCE_TYPE_OPTIONS.find((t) => t.value === typeCode) || FENCE_TYPE_OPTIONS[0];
  };

  const mockPolygonPoints = (typeCode: string, index: number): number[][] => {
    const baseLng = 116.407 + (Math.random() - 0.5) * 0.01;
    const baseLat = 39.904 + (Math.random() - 0.5) * 0.01;
    if (typeCode === 'assembly' || typeCode === 'checkpoint') {
      const r = 0.0008;
      return [
        [baseLng - r, baseLat],
        [baseLng, baseLat + r],
        [baseLng + r, baseLat],
        [baseLng, baseLat - r],
        [baseLng - r, baseLat]
      ];
    }
    const r = 0.0025;
    return [
      [baseLng - r * 1.4, baseLat - r * 0.8],
      [baseLng, baseLat + r],
      [baseLng + r * 1.2, baseLat + r * 0.4],
      [baseLng + r, baseLat - r],
      [baseLng - r * 0.8, baseLat - r * 0.6],
      [baseLng - r * 1.4, baseLat - r * 0.8]
    ];
  };

  const handleSubmitFence = async () => {
    try {
      const values = await drawForm.validateFields();
      const typeMeta = getFenceTypeMeta(values.fenceType);
      const selectedColor = values.fillColor || typeMeta.fill;
      const fillColorRgba = typeof selectedColor === 'string'
        ? selectedColor
        : (selectedColor as Color)?.toRgbString?.() || typeMeta.fill;

      const dto: EmergencyFenceCreateDTO = {
        eventId,
        fenceName: values.fenceName,
        fenceType: values.fenceType,
        fenceCode: `${values.fenceType}_${dayjs().format('YYYYMMDDHHmmss')}`,
        fenceGeometry: {
          type: 'Polygon',
          coordinates: [mockPolygonPoints(values.fenceType, fences.length)]
        },
        centerLng: 116.407,
        centerLat: 39.904,
        radiusMeters: values.radiusMeters,
        fillColor: fillColorRgba,
        strokeColor: typeMeta.color,
        strokeWeight: 2,
        opacity: 0.7,
        sortOrder: fences.length + 1,
        remark: values.remark
      };

      if (editingFence) {
        await updateEmergencyFence(editingFence.id, dto);
        message.success('封控区更新成功');
      } else {
        await createEmergencyFence(dto);
        message.success('封控区创建成功，已广播同步至地图');
      }
      setDrawModalVisible(false);
      setEditingFence(null);
      drawForm.resetFields();
      fetchFences();
    } catch (error: any) {
      if (error.errorFields) return;
      message.error(error.message || '保存失败');
    }
  };

  const handleDeleteFence = async (fenceId: string) => {
    try {
      await deleteEmergencyFence(fenceId);
      message.success('删除成功');
      fetchFences();
    } catch (error: any) {
      message.error(error.message || '删除失败');
    }
  };

  const handleBatchDelete = async () => {
    try {
      const res = await batchDeleteEmergencyFences(eventId);
      message.success(`已批量删除 ${res.data} 个封控区`);
      fetchFences();
    } catch (error: any) {
      message.error(error.message || '批量删除失败');
    }
  };

  const handleStartDraw = (fence?: EmergencyFence) => {
    if (fence) {
      setEditingFence(fence);
      drawForm.setFieldsValue({
        fenceName: fence.fenceName,
        fenceType: fence.fenceType,
        radiusMeters: fence.radiusMeters || 500,
        fillColor: fence.fillColor,
        remark: fence.remark
      });
    } else {
      setEditingFence(null);
      drawForm.resetFields();
      drawForm.setFieldsValue({
        fenceType: 'blockade',
        radiusMeters: 500,
        fillColor: 'rgba(255,77,79,0.2)'
      });
    }
    setDrawModalVisible(true);
  };

  const toggleFenceVisible = (fenceId: string) => {
    setSelectedFenceId(selectedFenceId === fenceId ? null : fenceId);
    message.info(selectedFenceId === fenceId ? '已在地图中隐藏该区域' : '已在地图中定位并高亮该区域');
  };

  const totalArea = fences.reduce((sum, _f) => sum + 1, 0);
  const activeArea = fences.filter((f) => f.status === 1).length;

  return (
    <div style={{ padding: '0 16px 16px' }}>
      <Alert
        type="info"
        showIcon
        icon={<SketchOutlined />}
        message={
          <Space>
            <span style={{ fontSize: 12 }}>图上作业说明：</span>
            <span style={{ fontSize: 11, color: '#8c9cb8' }}>
              创建封控区后将广播至指挥大屏和参战单位App，指挥员可基于地图直接绘制多边形区域。
            </span>
          </Space>
        }
        style={{
          marginBottom: 12,
          background: 'rgba(24, 144, 255, 0.08)',
          border: '1px solid rgba(24, 144, 255, 0.2)',
          padding: '8px 12px'
        }}
      />

      <Card
        size="small"
        style={{ marginBottom: 12 }}
        bordered={false}
        styles={{ body: { padding: 12, background: 'rgba(26, 31, 53, 0.5)' } }}
        title={
          <Space>
            <EnvironmentOutlined style={{ color: '#faad14' }} />
            <span style={{ color: '#e6f1ff', fontSize: 13 }}>已绘制封控区总览</span>
          </Space>
        }
        extra={
          <Space wrap>
            {FENCE_TYPE_OPTIONS.map((t) => (
              <Tag key={t.value} color={t.color} style={{ margin: 0, fontSize: 11 }}>
                {t.label} {fences.filter((f) => f.fenceType === t.value).length}
              </Tag>
            ))}
          </Space>
        }
      >
        <Row gutter={[12, 12]}>
          {FENCE_TYPE_OPTIONS.map((t) => (
            <Col span={8} key={t.value}>
              <div
                style={{
                  padding: 10,
                  borderRadius: 6,
                  background: `linear-gradient(135deg, ${t.color}15, ${t.color}05)`,
                  border: `1px solid ${t.color}33`
                }}
              >
                <Space size={8} style={{ marginBottom: 6 }}>
                  <div
                    style={{
                      width: 18,
                      height: 18,
                      borderRadius: 3,
                      background: t.fill,
                      border: `2px solid ${t.color}`
                    }}
                  />
                  <span style={{ color: '#e6f1ff', fontSize: 12, fontWeight: 500 }}>{t.label}</span>
                </Space>
                <div style={{ fontSize: 10, color: '#8c9cb8', lineHeight: 1.4 }}>{t.desc}</div>
              </div>
            </Col>
          ))}
        </Row>
      </Card>

      <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
        <Space>
          <Dropdown
            menu={{
              items: FENCE_TYPE_OPTIONS.map((t) => ({
                key: t.value,
                icon: (
                  <span
                    style={{
                      display: 'inline-block',
                      width: 10,
                      height: 10,
                      borderRadius: 2,
                      background: t.fill,
                      border: `1px solid ${t.color}`,
                      marginRight: 4
                    }}
                  />
                ),
                label: `绘制${t.label}`,
                onClick: () => {
                  drawForm.setFieldsValue({
                    fenceType: t.value,
                    fillColor: t.fill
                  });
                  handleStartDraw();
                }
              }))
            }}
          >
            <Button type="primary" size="small" icon={<PlusOutlined />} style={{ background: '#1890ff', borderColor: '#1890ff' }}>
              绘制区域
            </Button>
          </Dropdown>
          <Button size="small" icon={<ReloadOutlined />} onClick={fetchFences} loading={loading}>
            刷新
          </Button>
          {fences.length > 0 && (
            <Popconfirm
              title={`确认删除当前事件下全部 ${fences.length} 个封控区？`}
              onConfirm={handleBatchDelete}
              okText="确认删除"
              cancelText="取消"
              okButtonProps={{ danger: true }}
            >
              <Button danger size="small" icon={<DeleteOutlined />}>
                清空全部
              </Button>
            </Popconfirm>
          )}
        </Space>
        <Space size={16} style={{ fontSize: 12, color: '#8c9cb8' }}>
          <span>合计 {totalArea} 个区域</span>
          <span style={{ color: '#52c41a' }}>生效中 {activeArea}</span>
          <span style={{ color: '#8c8c8c' }}>已停用 {fences.length - activeArea}</span>
        </Space>
      </div>

      <Spin spinning={loading}>
        {fences.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              <div>
                <div style={{ color: '#8c9cb8', marginBottom: 12 }}>
                  <SketchOutlined style={{ marginRight: 6 }} />
                  尚未绘制任何管控区域
                </div>
                <Button type="primary" icon={<PlusOutlined />} onClick={() => handleStartDraw()}>
                  开始绘制封控区
                </Button>
              </div>
            }
            style={{ padding: 30 }}
          />
        ) : (
          <List
            size="small"
            dataSource={fences}
            renderItem={(fence) => {
              const typeMeta = getFenceTypeMeta(fence.fenceType);
              const isSelected = selectedFenceId === fence.id;
              return (
                <List.Item
                  key={fence.id}
                  style={{
                    padding: 10,
                    marginBottom: 8,
                    background: isSelected ? 'rgba(24, 144, 255, 0.1)' : 'rgba(26, 31, 53, 0.5)',
                    borderRadius: 6,
                    border: isSelected
                      ? '1px solid #1890ff'
                      : `1px solid ${fence.status === 1 ? typeMeta.color + '55' : '#1f2940'}`
                  }}
                  actions={[
                    <Tooltip key="view" title={isSelected ? '取消高亮' : '定位到地图'}>
                      <Button
                        type="link"
                        size="small"
                        icon={isSelected ? <EyeInvisibleOutlined /> : <EyeOutlined />}
                        onClick={() => toggleFenceVisible(fence.id)}
                      >
                        {isSelected ? '取消' : '定位'}
                      </Button>
                    </Tooltip>,
                    <Tooltip key="edit" title="编辑">
                      <Button
                        type="link"
                        size="small"
                        icon={<EditOutlined />}
                        onClick={() => handleStartDraw(fence)}
                      >
                        编辑
                      </Button>
                    </Tooltip>,
                    <Tooltip key="copy" title="复制一个相同区域">
                      <Button
                        type="link"
                        size="small"
                        icon={<CopyOutlined />}
                        onClick={() => {
                          drawForm.setFieldsValue({
                            fenceName: `${fence.fenceName}-副本`,
                            fenceType: fence.fenceType,
                            radiusMeters: fence.radiusMeters,
                            fillColor: fence.fillColor,
                            remark: fence.remark
                          });
                          handleStartDraw();
                        }}
                      >
                        复制
                      </Button>
                    </Tooltip>,
                    <Popconfirm
                      key="delete"
                      title={`确认删除【${fence.fenceName}】？`}
                      onConfirm={() => handleDeleteFence(fence.id)}
                      okText="删除"
                      cancelText="取消"
                      okButtonProps={{ danger: true }}
                    >
                      <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                        删除
                      </Button>
                    </Popconfirm>
                  ]}
                >
                  <List.Item.Meta
                    avatar={
                      <div
                        style={{
                          width: 48,
                          height: 48,
                          borderRadius: 6,
                          background: fence.fillColor || typeMeta.fill,
                          border: `2px solid ${typeMeta.color}`,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          position: 'relative',
                          flexShrink: 0
                        }}
                      >
                        <SketchOutlined style={{ color: typeMeta.color, fontSize: 22 }} />
                        {fence.status === 1 && (
                          <div
                            style={{
                              position: 'absolute',
                              top: -4,
                              right: -4,
                              width: 12,
                              height: 12,
                              borderRadius: '50%',
                              background: '#52c41a',
                              border: '2px solid rgba(26, 31, 53, 1)'
                            }}
                          />
                        )}
                      </div>
                    }
                    title={
                      <Space size={[8, 4]} wrap>
                        <span style={{ color: '#e6f1ff', fontSize: 13, fontWeight: 500 }}>
                          {fence.fenceName}
                        </span>
                        <Tag color={typeMeta.color} style={{ margin: 0 }}>
                          {typeMeta.label}
                        </Tag>
                        {fence.status === 1 ? (
                          <Tag color="success" style={{ margin: 0, fontSize: 11 }}>
                            <CheckCircleOutlined /> 生效中
                          </Tag>
                        ) : (
                          <Tag color="default" style={{ margin: 0, fontSize: 11 }}>
                            <CloseCircleOutlined /> 已停用
                          </Tag>
                        )}
                        {isSelected && (
                          <Tag color="blue" style={{ margin: 0, fontSize: 11 }}>
                            <AimOutlined /> 地图中
                          </Tag>
                        )}
                      </Space>
                    }
                    description={
                      <div style={{ fontSize: 11, color: '#8c9cb8', marginTop: 4 }}>
                        <Space size={[12, 4]} wrap>
                          {fence.fenceCode && <span>编码：{fence.fenceCode}</span>}
                          {fence.radiusMeters && <span>范围：{fence.radiusMeters}米</span>}
                          {fence.centerLat && fence.centerLng && (
                            <span>
                              中心：{fence.centerLng.toFixed(4)}, {fence.centerLat.toFixed(4)}
                            </span>
                          )}
                          {fence.createTime && <span>创建于 {dayjs(fence.createTime).format('MM-DD HH:mm')}</span>}
                        </Space>
                        {fence.remark && (
                          <div style={{ marginTop: 4, color: '#a6adc8' }}>
                            <InfoCircleOutlined style={{ marginRight: 4 }} />
                            {fence.remark}
                          </div>
                        )}
                      </div>
                    }
                  />
                </List.Item>
              );
            }}
          />
        )}
      </Spin>

      <Modal
        title={
          <Space>
            <SketchOutlined style={{ color: editingFence ? '#faad14' : '#1890ff' }} />
            <span>{editingFence ? '编辑封控区' : '绘制封控区'}</span>
          </Space>
        }
        open={drawModalVisible}
        onOk={handleSubmitFence}
        onCancel={() => {
          setDrawModalVisible(false);
          setEditingFence(null);
          drawForm.resetFields();
        }}
        okText={editingFence ? '保存修改' : '确认创建'}
        cancelText="取消"
        width={580}
        styles={{
          header: { background: '#1a1f35', color: '#e6f1ff', borderBottom: '1px solid #1f2940' },
          body: { background: '#141829' },
          content: { background: '#141829', border: '1px solid rgba(24, 144, 255, 0.3)' },
          footer: { background: '#1a1f35', borderTop: '1px solid #1f2940' }
        }}
      >
        <Form form={drawForm} layout="vertical" style={{ color: '#e6f1ff' }}>
          <Row gutter={12}>
            <Col span={16}>
              <Form.Item label="区域名称" name="fenceName" rules={[{ required: true, message: '请输入区域名称' }]}>
                <Input placeholder="如：核心封控区A、东侧检查点1" maxLength={50} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="区域类型" name="fenceType" rules={[{ required: true, message: '请选择区域类型' }]}>
                <Select placeholder="选择类型">
                  {FENCE_TYPE_OPTIONS.map((t) => (
                    <Option key={t.value} value={t.value}>
                      <Space size={8}>
                        <span
                          style={{
                            display: 'inline-block',
                            width: 12,
                            height: 12,
                            borderRadius: 2,
                            background: t.fill,
                            border: `1px solid ${t.color}`,
                            verticalAlign: 'middle'
                          }}
                        />
                        {t.label}
                      </Space>
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Card
            size="small"
            bordered={false}
            style={{ marginBottom: 12 }}
            styles={{ body: { padding: 12, background: 'rgba(26, 31, 53, 0.6)' } }}
            title={
              <Space size={6}>
                <SettingOutlined style={{ color: '#8c9cb8' }} />
                <span style={{ fontSize: 12, color: '#8c9cb8' }}>样式与参数</span>
              </Space>
            }
          >
            <Row gutter={12}>
              <Col span={8}>
                <Form.Item label="半径(米)" name="radiusMeters" style={{ marginBottom: 0 }}>
                  <InputNumber min={10} max={5000} step={50} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item label="填充颜色" name="fillColor" style={{ marginBottom: 0 }}>
                  <ColorPicker
                    format="rgb"
                    showText
                    style={{ width: '100%' }}
                    onChange={(val) => drawForm.setFieldValue('fillColor', val.toRgbString())}
                  />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item label="绘制模式" style={{ marginBottom: 0 }}>
                  <Button
                    type="primary"
                    block
                    icon={<AimOutlined />}
                    size="small"
                    style={{ background: isDrawing ? '#52c41a' : '#1890ff', borderColor: isDrawing ? '#52c41a' : '#1890ff' }}
                    onClick={() => {
                      setIsDrawing(!isDrawing);
                      message.info(isDrawing ? '已退出绘制模式' : '请在指挥大屏地图上点击绘制多边形顶点（演示模式将自动生成示例区域）');
                    }}
                  >
                    {isDrawing ? '绘制中(示例)' : '开启取点绘制'}
                  </Button>
                </Form.Item>
              </Col>
            </Row>
          </Card>

          <Form.Item label="备注说明" name="remark">
            <Input.TextArea rows={2} placeholder="补充说明区域用途、管理要求、联络人等信息..." maxLength={200} showCount />
          </Form.Item>

          <Alert
            type="warning"
            showIcon
            icon={<SafetyCertificateOutlined />}
            message="安全提示"
            description={
              <span style={{ fontSize: 11 }}>
                区域创建后将立即生效并通过 RocketMQ 广播至指挥大屏、参战单位App和GIS平台，所有变更均有日志追踪。
              </span>
            }
            style={{ background: 'rgba(250, 173, 20, 0.08)', border: '1px solid rgba(250, 173, 20, 0.2)' }}
          />
        </Form>
      </Modal>
    </div>
  );
};

export default FenceDrawPanel;
