import React, { useState, useEffect } from 'react';
import {
  Table,
  Button,
  Space,
  Tabs,
  Input,
  Select,
  Form,
  Modal,
  message,
  Tag,
  Card,
  Row,
  Col,
  Avatar,
  Popconfirm,
  Result,
  Descriptions,
  Upload,
  Divider,
  Statistic,
  Alert
} from 'antd';
import {
  PlusOutlined,
  SearchOutlined,
  QrcodeOutlined,
  DownloadOutlined,
  DeleteOutlined,
  EyeOutlined,
  SafetyCertificateOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  UserOutlined,
  UploadOutlined,
  FileTextOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';

import { Pass, PassVerifyResult, PassCreateDTO } from '@/types';
import {
  getPassList,
  getPassDetail,
  generatePass,
  batchGeneratePass,
  verifyPass,
  verifyPassByQrcode,
  revokePass,
  downloadPassQrcode
} from '@/services/eventApi';

const { TabPane } = Tabs;
const { Option } = Select;
const { TextArea } = Input;

interface PassManagerProps {
  eventId: string;
}

const PassManager: React.FC<PassManagerProps> = ({ eventId }) => {
  const [activeTab, setActiveTab] = useState('list');
  const [loading, setLoading] = useState(false);
  const [passList, setPassList] = useState<Pass[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const [searchName, setSearchName] = useState('');
  const [searchStatus, setSearchStatus] = useState<number | undefined>();

  const [modalVisible, setModalVisible] = useState(false);
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedPass, setSelectedPass] = useState<Pass | null>(null);

  const [verifyInput, setVerifyInput] = useState('');
  const [verifyResult, setVerifyResult] = useState<PassVerifyResult | null>(null);
  const [verifying, setVerifying] = useState(false);

  const [batchText, setBatchText] = useState('');
  const [batchResult, setBatchResult] = useState<Pass[]>([]);
  const [batchSubmitting, setBatchSubmitting] = useState(false);

  const loadPassList = async () => {
    setLoading(true);
    try {
      const params: any = { page, size: pageSize };
      if (searchName) params.holderName = searchName;
      if (searchStatus !== undefined) params.status = searchStatus;
      const res = await getPassList(eventId, params);
      setPassList(res.data);
      setTotal(res.total);
    } catch (error) {
      console.error('Load pass list error:', error);
      message.error('加载通行证列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (activeTab === 'list') {
      loadPassList();
    }
  }, [eventId, activeTab, page, pageSize, searchName, searchStatus]);

  const handleSearch = () => {
    setPage(1);
    loadPassList();
  };

  const handleCreatePass = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const res = await generatePass({
        eventId,
        holderName: values.holderName,
        holderIdcard: values.holderIdcard,
        holderPhone: values.holderPhone,
        passType: values.passType,
        expireDays: values.expireDays || 7
      });
      message.success('通行证生成成功');
      setModalVisible(false);
      form.resetFields();
      loadPassList();
    } catch (error) {
      console.error('Generate pass error:', error);
      message.error('生成通行证失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleViewDetail = async (pass: Pass) => {
    try {
      const res = await getPassDetail(pass.id);
      setSelectedPass(res.data);
      setDetailVisible(true);
    } catch (error) {
      console.error('Get pass detail error:', error);
      message.error('获取详情失败');
    }
  };

  const handleDownloadQrcode = (passId: string) => {
    const url = downloadPassQrcode(passId);
    window.open(url, '_blank');
  };

  const handleRevokePass = async (passId: string) => {
    try {
      await revokePass(passId);
      message.success('吊销成功');
      loadPassList();
    } catch (error) {
      console.error('Revoke pass error:', error);
      message.error('吊销失败');
    }
  };

  const handleVerify = async () => {
    if (!verifyInput.trim()) {
      message.warning('请输入通行证编号或JWT Token');
      return;
    }

    setVerifying(true);
    try {
      let res;
      if (verifyInput.trim().startsWith('eyJ')) {
        res = await verifyPassByQrcode(verifyInput.trim());
      } else {
        res = await verifyPass(verifyInput.trim());
      }
      setVerifyResult(res.data);
    } catch (error) {
      console.error('Verify pass error:', error);
      setVerifyResult({
        success: false,
        message: '核验失败，请检查输入',
        verifyTime: new Date().toISOString()
      });
    } finally {
      setVerifying(false);
    }
  };

  const parseBatchText = (text: string): PassCreateDTO[] => {
    const lines = text.trim().split('\n').filter(line => line.trim());
    return lines.map(line => {
      const parts = line.split(',').map(s => s.trim());
      return {
        eventId,
        holderName: parts[0] || '',
        holderIdcard: parts[1] || '',
        holderPhone: parts[2] || '',
        passType: parts[3] || '临时通行证'
      };
    }).filter(item => item.holderName && item.holderIdcard);
  };

  const handleBatchGenerate = async () => {
    const list = parseBatchText(batchText);
    if (list.length === 0) {
      message.warning('请输入有效的人员信息');
      return;
    }

    setBatchSubmitting(true);
    try {
      const res = await batchGeneratePass(eventId, list);
      setBatchResult(res.data);
      message.success(`成功生成 ${res.data.length} 张通行证`);
      loadPassList();
    } catch (error) {
      console.error('Batch generate pass error:', error);
      message.error('批量生成失败');
    } finally {
      setBatchSubmitting(false);
    }
  };

  const getStatusTag = (status: number, expireTime?: string) => {
    if (expireTime && dayjs(expireTime).isBefore(dayjs())) {
      return <Tag color="gray">已过期</Tag>;
    }
    switch (status) {
      case 1:
        return <Tag color="green">有效</Tag>;
      case 2:
        return <Tag color="red">已作废</Tag>;
      default:
        return <Tag>未知</Tag>;
    }
  };

  const columns: ColumnsType<Pass> = [
    {
      title: '通行证编号',
      dataIndex: 'passNo',
      key: 'passNo',
      width: 160,
      render: (text) => (
        <Space>
          <SafetyCertificateOutlined style={{ color: '#1890ff' }} />
          <span style={{ color: '#e6f1ff' }}>{text}</span>
        </Space>
      )
    },
    {
      title: '持证人',
      dataIndex: 'holderName',
      key: 'holderName',
      width: 100
    },
    {
      title: '身份证号',
      dataIndex: 'holderIdcard',
      key: 'holderIdcard',
      width: 180,
      render: (text) => (
        <span style={{ fontFamily: 'monospace' }}>{text}</span>
      )
    },
    {
      title: '手机号',
      dataIndex: 'holderPhone',
      key: 'holderPhone',
      width: 120
    },
    {
      title: '证件类型',
      dataIndex: 'passType',
      key: 'passType',
      width: 110
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status, record) => getStatusTag(status, record.expireTime)
    },
    {
      title: '签发时间',
      dataIndex: 'issueTime',
      key: 'issueTime',
      width: 170,
      render: (time) => dayjs(time).format('YYYY-MM-DD HH:mm')
    },
    {
      title: '过期时间',
      dataIndex: 'expireTime',
      key: 'expireTime',
      width: 170,
      render: (time) => dayjs(time).format('YYYY-MM-DD HH:mm')
    },
    {
      title: '核验次数',
      dataIndex: 'verifyCount',
      key: 'verifyCount',
      width: 90,
      align: 'center'
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
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
          <Button
            type="link"
            size="small"
            icon={<DownloadOutlined />}
            onClick={() => handleDownloadQrcode(record.id)}
          >
            二维码
          </Button>
          {record.status === 1 && (
            <Popconfirm
              title="确定吊销该通行证?"
              onConfirm={() => handleRevokePass(record.id)}
              okText="确定"
              cancelText="取消"
            >
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                吊销
              </Button>
            </Popconfirm>
          )}
        </Space>
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
        <span className="panel-title">通行证管理</span>
      </div>

      <Tabs activeKey={activeTab} onChange={setActiveTab} type="card">
        <TabPane
          tab={
            <span>
              <FileTextOutlined />
              通行证列表
            </span>
          }
          key="list"
        >
          <div style={{ marginBottom: 16 }}>
            <Space>
              <Input
                placeholder="持证人姓名"
                prefix={<SearchOutlined />}
                value={searchName}
                onChange={(e) => setSearchName(e.target.value)}
                style={{ width: 200 }}
                onPressEnter={handleSearch}
              />
              <Select
                placeholder="证件状态"
                value={searchStatus}
                onChange={(value) => setSearchStatus(value)}
                style={{ width: 150 }}
                allowClear
              >
                <Option value={1}>有效</Option>
                <Option value={2}>已作废</Option>
                <Option value={3}>已过期</Option>
              </Select>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
                搜索
              </Button>
              <Button icon={<ReloadOutlined />} onClick={loadPassList}>
                刷新
              </Button>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => setModalVisible(true)}
              >
                单个生成
              </Button>
            </Space>
          </div>

          <Table
            rowKey="id"
            loading={loading}
            dataSource={passList}
            columns={columns}
            scroll={{ x: 1300 }}
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
          />
        </TabPane>

        <TabPane
          tab={
            <span>
              <QrcodeOutlined />
              扫码核验
            </span>
          }
          key="verify"
        >
          <Row gutter={24}>
            <Col span={10}>
              <Card
                title={
                  <Space>
                    <QrcodeOutlined style={{ color: '#1890ff' }} />
                    <span>扫码核验</span>
                  </Space>
                }
                style={{
                  background: 'rgba(24, 144, 255, 0.05)',
                  border: '1px solid rgba(24, 144, 255, 0.3)'
                }}
              >
                <Space direction="vertical" size="large" style={{ width: '100%' }}>
                  <div>
                    <div style={{ color: '#8c9cb8', marginBottom: 8 }}>
                      输入通行证编号或粘贴JWT Token
                    </div>
                    <TextArea
                      rows={4}
                      placeholder="请输入通行证编号，或粘贴二维码中的JWT Token"
                      value={verifyInput}
                      onChange={(e) => setVerifyInput(e.target.value)}
                      style={{ fontFamily: 'monospace' }}
                    />
                  </div>
                  <Button
                    type="primary"
                    size="large"
                    icon={<QrcodeOutlined />}
                    onClick={handleVerify}
                    loading={verifying}
                    block
                  >
                    核验
                  </Button>

                  <Alert
                    type="info"
                    showIcon
                    message="使用说明"
                    description="支持两种核验方式：1. 输入通行证编号进行核验；2. 扫描二维码获取JWT Token后粘贴核验。"
                  />
                </Space>
              </Card>
            </Col>

            <Col span={14}>
              <Card
                title={
                  <Space>
                    <SafetyCertificateOutlined style={{ color: '#1890ff' }} />
                    <span>核验结果</span>
                  </Space>
                }
                style={{
                  background: 'rgba(114, 46, 209, 0.05)',
                  border: '1px solid rgba(114, 46, 209, 0.3)'
                }}
              >
                {verifyResult ? (
                  <div>
                    <Result
                      status={verifyResult.success ? 'success' : 'error'}
                      title={verifyResult.success ? '核验通过' : '核验失败'}
                      subTitle={verifyResult.message}
                      icon={verifyResult.success ? (
                        <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 48 }} />
                      ) : (
                        <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 48 }} />
                      )}
                    />

                    {verifyResult.success && verifyResult.pass && (
                      <div style={{ marginTop: 16 }}>
                        <Divider style={{ margin: '8px 0 16px 0' }}>证件信息</Divider>
                        <Descriptions column={1} size="small">
                          <Descriptions.Item label="通行证编号">
                            {verifyResult.pass.passNo}
                          </Descriptions.Item>
                          <Descriptions.Item label="持证人">
                            <Space>
                              <Avatar
                                size={32}
                                src={verifyResult.pass.photoUrl}
                                icon={<UserOutlined />}
                              />
                              {verifyResult.pass.holderName}
                            </Space>
                          </Descriptions.Item>
                          <Descriptions.Item label="身份证号">
                            {verifyResult.pass.holderIdcard}
                          </Descriptions.Item>
                          <Descriptions.Item label="证件类型">
                            {verifyResult.pass.passType}
                          </Descriptions.Item>
                          <Descriptions.Item label="有效期">
                            {dayjs(verifyResult.pass.issueTime).format('YYYY-MM-DD')}
                            {' ~ '}
                            {dayjs(verifyResult.pass.expireTime).format('YYYY-MM-DD')}
                          </Descriptions.Item>
                          <Descriptions.Item label="已核验次数">
                            <Tag color="blue">{verifyResult.pass.verifyCount} 次</Tag>
                          </Descriptions.Item>
                          <Descriptions.Item label="核验时间">
                            {dayjs(verifyResult.verifyTime).format('YYYY-MM-DD HH:mm:ss')}
                          </Descriptions.Item>
                        </Descriptions>
                      </div>
                    )}
                  </div>
                ) : (
                  <div style={{
                    padding: 60,
                    textAlign: 'center',
                    color: '#8c9cb8'
                  }}>
                    <QrcodeOutlined style={{ fontSize: 48, marginBottom: 16, opacity: 0.3 }} />
                    <p>请输入通行证编号或扫描二维码进行核验</p>
                  </div>
                )}
              </Card>
            </Col>
          </Row>
        </TabPane>

        <TabPane
          tab={
            <span>
              <UploadOutlined />
              批量生成
            </span>
          }
          key="batch"
        >
          <Row gutter={24}>
            <Col span={12}>
              <Card
                title={
                  <Space>
                    <UploadOutlined style={{ color: '#1890ff' }} />
                    <span>批量导入人员信息</span>
                  </Space>
                }
                style={{
                  background: 'rgba(24, 144, 255, 0.05)',
                  border: '1px solid rgba(24, 144, 255, 0.3)'
                }}
              >
                <Alert
                  type="info"
                  showIcon
                  message="格式说明"
                  description="每行一条记录，格式：姓名,身份证号,手机号,证件类型。证件类型可选，默认'临时通行证'。"
                  style={{ marginBottom: 16 }}
                />

                <div style={{ marginBottom: 12 }}>
                  <Space>
                    <Upload
                      accept=".csv,.txt"
                      showUploadList={false}
                      beforeUpload={(file) => {
                        const reader = new FileReader();
                        reader.onload = (e) => {
                          const text = e.target?.result as string;
                          setBatchText(text);
                        };
                        reader.readAsText(file);
                        return false;
                      }}
                    >
                      <Button icon={<UploadOutlined />}>上传文件</Button>
                    </Upload>
                    <span style={{ color: '#8c9cb8', fontSize: 12 }}>
                      支持CSV/TXT文件
                    </span>
                  </Space>
                </div>

                <TextArea
                  rows={10}
                  placeholder={'张三,110101199001011234,13800138000,工作证\n李四,110101199002022345,13800138001,临时通行证'}
                  value={batchText}
                  onChange={(e) => setBatchText(e.target.value)}
                  style={{ fontFamily: 'monospace', marginBottom: 16 }}
                />

                <div style={{ marginBottom: 16 }}>
                  <Space>
                    <Statistic
                      title="待生成数量"
                      value={parseBatchText(batchText).length}
                      valueStyle={{ color: '#1890ff', fontSize: 18 }}
                    />
                    {batchResult.length > 0 && (
                      <Statistic
                        title="已生成数量"
                        value={batchResult.length}
                        valueStyle={{ color: '#52c41a', fontSize: 18 }}
                      />
                    )}
                  </Space>
                </div>

                <Button
                  type="primary"
                  size="large"
                  icon={<PlusOutlined />}
                  onClick={handleBatchGenerate}
                  loading={batchSubmitting}
                  block
                  disabled={parseBatchText(batchText).length === 0}
                >
                  批量生成通行证
                </Button>
              </Card>
            </Col>

            <Col span={12}>
              <Card
                title={
                  <Space>
                    <SafetyCertificateOutlined style={{ color: '#1890ff' }} />
                    <span>生成结果</span>
                  </Space>
                }
                style={{
                  background: 'rgba(82, 196, 26, 0.05)',
                  border: '1px solid rgba(82, 196, 26, 0.3)'
                }}
              >
                {batchResult.length > 0 ? (
                  <List
                    dataSource={batchResult}
                    size="small"
                    renderItem={(item) => (
                      <List.Item
                        style={{
                          padding: '12px 0',
                          borderBottom: '1px solid #1f2940'
                        }}
                      >
                        <List.Item.Meta
                          avatar={
                            <Avatar
                              style={{ background: '#52c41a' }}
                              icon={<SafetyCertificateOutlined />}
                            />
                          }
                          title={
                            <Space>
                              <span style={{ color: '#e6f1ff' }}>{item.holderName}</span>
                              <Tag color="green">生成成功</Tag>
                            </Space>
                          }
                          description={
                            <Space direction="vertical" size={0}>
                              <span style={{ color: '#8c9cb8', fontSize: 12 }}>
                                {item.passNo}
                              </span>
                              <span style={{ color: '#8c9cb8', fontSize: 12 }}>
                                {item.holderIdcard}
                              </span>
                            </Space>
                          }
                        />
                        <Button
                          type="link"
                          size="small"
                          icon={<DownloadOutlined />}
                          onClick={() => handleDownloadQrcode(item.id)}
                        >
                          下载二维码
                        </Button>
                      </List.Item>
                    )}
                  />
                ) : (
                  <div style={{
                    padding: 60,
                    textAlign: 'center',
                    color: '#8c9cb8'
                  }}>
                    <SafetyCertificateOutlined style={{ fontSize: 48, marginBottom: 16, opacity: 0.3 }} />
                    <p>暂无生成记录</p>
                    <p style={{ fontSize: 12 }}>在左侧输入人员信息并点击批量生成</p>
                  </div>
                )}
              </Card>
            </Col>
          </Row>
        </TabPane>
      </Tabs>

      <Modal
        title={
          <Space>
            <PlusOutlined style={{ color: '#1890ff' }} />
            <span>生成通行证</span>
          </Space>
        }
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false);
          form.resetFields();
        }}
        onOk={handleCreatePass}
        confirmLoading={submitting}
        okText="生成"
        cancelText="取消"
        width={500}
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ passType: '临时通行证', expireDays: 7 }}
        >
          <Form.Item
            name="holderName"
            label="持证人姓名"
            rules={[{ required: true, message: '请输入持证人姓名' }]}
          >
            <Input placeholder="请输入持证人姓名" />
          </Form.Item>

          <Form.Item
            name="holderIdcard"
            label="身份证号"
            rules={[
              { required: true, message: '请输入身份证号' },
              { len: 18, message: '身份证号为18位' }
            ]}
          >
            <Input placeholder="请输入身份证号" maxLength={18} />
          </Form.Item>

          <Form.Item
            name="holderPhone"
            label="手机号"
            rules={[
              { required: true, message: '请输入手机号' },
              { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号' }
            ]}
          >
            <Input placeholder="请输入手机号" maxLength={11} />
          </Form.Item>

          <Form.Item
            name="passType"
            label="证件类型"
            rules={[{ required: true, message: '请选择证件类型' }]}
          >
            <Select placeholder="请选择证件类型">
              <Option value="工作证">工作证</Option>
              <Option value="临时通行证">临时通行证</Option>
              <Option value="车辆通行证">车辆通行证</Option>
              <Option value="采访证">采访证</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="expireDays"
            label="有效天数"
            rules={[{ required: true, message: '请输入有效天数' }]}
          >
            <InputNumber min={1} max={365} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={
          <Space>
            <SafetyCertificateOutlined style={{ color: '#1890ff' }} />
            <span>通行证详情</span>
          </Space>
        }
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        footer={[
          <Button key="close" onClick={() => setDetailVisible(false)}>
            关闭
          </Button>,
          selectedPass && selectedPass.status === 1 && (
            <Button
              key="download"
              icon={<DownloadOutlined />}
              onClick={() => handleDownloadQrcode(selectedPass.id)}
            >
              下载二维码
            </Button>
          )
        ]}
        width={600}
      >
        {selectedPass && (
          <div>
            <Row gutter={24}>
              <Col span={8} style={{ textAlign: 'center' }}>
                <div style={{
                  padding: 16,
                  background: 'rgba(24, 144, 255, 0.05)',
                  borderRadius: 8,
                  border: '1px solid rgba(24, 144, 255, 0.3)'
                }}>
                  <Avatar
                    size={100}
                    src={selectedPass.photoUrl}
                    icon={<UserOutlined style={{ fontSize: 48 }} />}
                    style={{ marginBottom: 12 }}
                  />
                  <div style={{ fontSize: 16, fontWeight: 500, color: '#e6f1ff' }}>
                    {selectedPass.holderName}
                  </div>
                  <div style={{ fontSize: 12, color: '#8c9cb8', marginTop: 4 }}>
                    {selectedPass.passType}
                  </div>
                  {getStatusTag(selectedPass.status, selectedPass.expireTime)}
                </div>
              </Col>

              <Col span={16}>
                <Descriptions column={1} size="small" bordered>
                  <Descriptions.Item label="通行证编号" span={2}>
                    <span style={{ fontFamily: 'monospace', color: '#1890ff' }}>
                      {selectedPass.passNo}
                    </span>
                  </Descriptions.Item>
                  <Descriptions.Item label="身份证号" span={2}>
                    {selectedPass.holderIdcard}
                  </Descriptions.Item>
                  <Descriptions.Item label="手机号" span={2}>
                    {selectedPass.holderPhone}
                  </Descriptions.Item>
                  <Descriptions.Item label="签发时间">
                    {dayjs(selectedPass.issueTime).format('YYYY-MM-DD HH:mm')}
                  </Descriptions.Item>
                  <Descriptions.Item label="过期时间">
                    {dayjs(selectedPass.expireTime).format('YYYY-MM-DD HH:mm')}
                  </Descriptions.Item>
                  <Descriptions.Item label="核验次数" span={2}>
                    <Tag color="blue">{selectedPass.verifyCount} 次</Tag>
                  </Descriptions.Item>
                </Descriptions>
              </Col>
            </Row>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default PassManager;
