import React, { useState, useEffect } from 'react';
import {
  Table,
  Button,
  Space,
  Modal,
  Form,
  Input,
  message,
  Tag,
  Card,
  Row,
  Col,
  Statistic,
  Descriptions,
  Divider,
  List
} from 'antd';
import {
  PlusOutlined,
  EyeOutlined,
  DownloadOutlined,
  FileTextOutlined,
  TeamOutlined,
  CarOutlined,
  BellOutlined,
  SafetyOutlined,
  VideoCameraOutlined,
  EnvironmentOutlined,
  ReloadOutlined,
  FilePdfOutlined
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';

import { EventReport, ReportGenerateDTO } from '@/types';
import {
  getReportList,
  getReportDetail,
  generateReport,
  downloadReport
} from '@/services/eventApi';

const { TextArea } = Input;

interface ReportManagerProps {
  eventId: string;
}

const ReportManager: React.FC<ReportManagerProps> = ({ eventId }) => {
  const [loading, setLoading] = useState(false);
  const [reports, setReports] = useState<EventReport[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const [modalVisible, setModalVisible] = useState(false);
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedReport, setSelectedReport] = useState<EventReport | null>(null);

  const loadReports = async () => {
    setLoading(true);
    try {
      const res = await getReportList(eventId, { page, size: pageSize });
      setReports(res.data);
      setTotal(res.total);
    } catch (error) {
      console.error('Load reports error:', error);
      message.error('加载报告列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadReports();
  }, [eventId, page, pageSize]);

  const handleGenerateReport = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const res = await generateReport({
        eventId,
        reportName: values.reportName,
        summary: values.summary
      });
      message.success('报告生成成功');
      setModalVisible(false);
      form.resetFields();
      loadReports();
    } catch (error) {
      console.error('Generate report error:', error);
      message.error('生成报告失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleViewDetail = async (report: EventReport) => {
    try {
      const res = await getReportDetail(report.id);
      setSelectedReport(res.data);
      setDetailVisible(true);
    } catch (error) {
      console.error('Get report detail error:', error);
      message.error('获取报告详情失败');
    }
  };

  const handleDownloadReport = (reportId: string) => {
    const url = downloadReport(reportId);
    window.open(url, '_blank');
  };

  const columns: ColumnsType<EventReport> = [
    {
      title: '报告名称',
      dataIndex: 'reportName',
      key: 'reportName',
      render: (text) => (
        <Space>
          <FileTextOutlined style={{ color: '#1890ff' }} />
          <span style={{ color: '#e6f1ff' }}>{text}</span>
        </Space>
      )
    },
    {
      title: '生成时间',
      dataIndex: 'generateTime',
      key: 'generateTime',
      width: 180,
      render: (time) => dayjs(time).format('YYYY-MM-DD HH:mm:ss')
    },
    {
      title: '人流量',
      dataIndex: 'pedestrianCount',
      key: 'pedestrianCount',
      width: 100,
      align: 'center',
      render: (count) => (
        <Space>
          <TeamOutlined style={{ color: '#52c41a' }} />
          <span style={{ color: '#e6f1ff' }}>{count?.toLocaleString() || 0}</span>
        </Space>
      )
    },
    {
      title: '车流量',
      dataIndex: 'vehicleCount',
      key: 'vehicleCount',
      width: 100,
      align: 'center',
      render: (count) => (
        <Space>
          <CarOutlined style={{ color: '#1890ff' }} />
          <span style={{ color: '#e6f1ff' }}>{count?.toLocaleString() || 0}</span>
        </Space>
      )
    },
    {
      title: '预警数',
      dataIndex: 'alertCount',
      key: 'alertCount',
      width: 100,
      align: 'center',
      render: (count) => (
        <Space>
          <BellOutlined style={{ color: '#faad14' }} />
          <span style={{ color: '#e6f1ff' }}>{count || 0}</span>
        </Space>
      )
    },
    {
      title: '警力数',
      dataIndex: 'policeCount',
      key: 'policeCount',
      width: 100,
      align: 'center',
      render: (count) => (
        <Space>
          <SafetyOutlined style={{ color: '#722ed1' }} />
          <span style={{ color: '#e6f1ff' }}>{count || 0}</span>
        </Space>
      )
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record)}
          >
            查看详情
          </Button>
          <Button
            type="link"
            size="small"
            icon={<DownloadOutlined />}
            onClick={() => handleDownloadReport(record.id)}
          >
            下载PDF
          </Button>
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
        <span className="panel-title">报告管理</span>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadReports}>
            刷新
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setModalVisible(true)}
          >
            生成报告
          </Button>
        </Space>
      </div>

      <Table
        rowKey="id"
        loading={loading}
        dataSource={reports}
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
      />

      <Modal
        title={
          <Space>
            <PlusOutlined style={{ color: '#1890ff' }} />
            <span>生成报告</span>
          </Space>
        }
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false);
          form.resetFields();
        }}
        onOk={handleGenerateReport}
        confirmLoading={submitting}
        okText="生成"
        cancelText="取消"
        width={500}
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ reportName: `${dayjs().format('YYYY年MM月DD日')}安保报告` }}
        >
          <Form.Item
            name="reportName"
            label="报告名称"
            rules={[{ required: true, message: '请输入报告名称' }]}
          >
            <Input placeholder="请输入报告名称" />
          </Form.Item>

          <Form.Item
            name="summary"
            label="活动总结"
          >
            <TextArea
              rows={4}
              placeholder="请输入活动总结（可选）"
              maxLength={500}
              showCount
            />
          </Form.Item>

          <div style={{
            padding: 12,
            background: 'rgba(24, 144, 255, 0.05)',
            borderRadius: 4,
            border: '1px dashed rgba(24, 144, 255, 0.3)'
          }}>
            <div style={{ color: '#8c9cb8', fontSize: 12 }}>
              <FilePdfOutlined style={{ marginRight: 4 }} />
              报告将自动包含以下内容：
            </div>
            <List
              size="small"
              dataSource={['活动基本信息', '资源投入统计（警力、摄像头、岗位）', '流量统计（人流量、车流量）', '预警统计']}
              renderItem={(item) => (
                <List.Item style={{ border: 'none', padding: '4px 0' }}>
                  <span style={{ color: '#8c9cb8', fontSize: 12 }}>• {item}</span>
                </List.Item>
              )}
            />
          </div>
        </Form>
      </Modal>

      <Modal
        title={
          <Space>
            <FileTextOutlined style={{ color: '#1890ff' }} />
            <span>报告详情</span>
          </Space>
        }
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        width={800}
        footer={[
          <Button key="close" onClick={() => setDetailVisible(false)}>
            关闭
          </Button>,
          selectedReport && (
            <Button
              key="download"
              type="primary"
              icon={<DownloadOutlined />}
              onClick={() => handleDownloadReport(selectedReport.id)}
            >
              下载PDF
            </Button>
          )
        ]}
      >
        {selectedReport && (
          <div style={{ color: '#e6f1ff' }}>
            <Card
              style={{
                marginBottom: 16,
                background: 'rgba(24, 144, 255, 0.03)',
                border: '1px solid rgba(24, 144, 255, 0.2)'
              }}
            >
              <div style={{ textAlign: 'center', marginBottom: 16 }}>
                <h2 style={{ margin: 0, color: '#e6f1ff', fontSize: 22 }}>
                  {selectedReport.reportName}
                </h2>
                <p style={{ color: '#8c9cb8', marginTop: 8 }}>
                  生成时间：{dayjs(selectedReport.generateTime).format('YYYY年MM月DD日 HH:mm')}
                </p>
              </div>
            </Card>

            <Divider orientation="left" style={{ color: '#1890ff', borderColor: '#1f2940' }}>
              <Space>
                <EnvironmentOutlined />
                <span>活动基本信息</span>
              </Space>
            </Divider>

            <Card
              size="small"
              style={{
                marginBottom: 16,
                background: 'rgba(24, 144, 255, 0.03)',
                border: '1px solid #1f2940'
              }}
            >
              <Descriptions column={2} size="small">
                <Descriptions.Item label="活动名称">
                  {selectedReport.reportName}
                </Descriptions.Item>
                <Descriptions.Item label="生成时间">
                  {dayjs(selectedReport.generateTime).format('YYYY-MM-DD HH:mm:ss')}
                </Descriptions.Item>
              </Descriptions>
            </Card>

            <Divider orientation="left" style={{ color: '#722ed1', borderColor: '#1f2940' }}>
              <Space>
                <SafetyOutlined />
                <span>资源投入统计</span>
              </Space>
            </Divider>

            <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
              <Col span={6}>
                <Card
                  size="small"
                  style={{
                    background: 'rgba(114, 46, 209, 0.05)',
                    border: '1px solid rgba(114, 46, 209, 0.3)',
                    textAlign: 'center'
                  }}
                >
                  <Statistic
                    title="投入警力"
                    value={selectedReport.policeCount || 0}
                    prefix={<SafetyOutlined style={{ color: '#722ed1' }} />}
                    valueStyle={{ color: '#722ed1' }}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card
                  size="small"
                  style={{
                    background: 'rgba(24, 144, 255, 0.05)',
                    border: '1px solid rgba(24, 144, 255, 0.3)',
                    textAlign: 'center'
                  }}
                >
                  <Statistic
                    title="摄像头"
                    value={selectedReport.cameraCount || 0}
                    prefix={<VideoCameraOutlined style={{ color: '#1890ff' }} />}
                    valueStyle={{ color: '#1890ff' }}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card
                  size="small"
                  style={{
                    background: 'rgba(82, 196, 26, 0.05)',
                    border: '1px solid rgba(82, 196, 26, 0.3)',
                    textAlign: 'center'
                  }}
                >
                  <Statistic
                    title="岗位数"
                    value={selectedReport.postCount || 0}
                    prefix={<EnvironmentOutlined style={{ color: '#52c41a' }} />}
                    valueStyle={{ color: '#52c41a' }}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card
                  size="small"
                  style={{
                    background: 'rgba(250, 173, 20, 0.05)',
                    border: '1px solid rgba(250, 173, 20, 0.3)',
                    textAlign: 'center'
                  }}
                >
                  <Statistic
                    title="预警数"
                    value={selectedReport.alertCount || 0}
                    prefix={<BellOutlined style={{ color: '#faad14' }} />}
                    valueStyle={{ color: '#faad14' }}
                  />
                </Card>
              </Col>
            </Row>

            <Divider orientation="left" style={{ color: '#52c41a', borderColor: '#1f2940' }}>
              <Space>
                <TeamOutlined />
                <span>流量统计</span>
              </Space>
            </Divider>

            <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
              <Col span={12}>
                <Card
                  size="small"
                  style={{
                    background: 'rgba(82, 196, 26, 0.05)',
                    border: '1px solid rgba(82, 196, 26, 0.3)'
                  }}
                >
                  <Statistic
                    title="累计人流量"
                    value={selectedReport.pedestrianCount || 0}
                    prefix={<TeamOutlined style={{ color: '#52c41a' }} />}
                    valueStyle={{ color: '#52c41a', fontSize: 28 }}
                  />
                </Card>
              </Col>
              <Col span={12}>
                <Card
                  size="small"
                  style={{
                    background: 'rgba(24, 144, 255, 0.05)',
                    border: '1px solid rgba(24, 144, 255, 0.3)'
                  }}
                >
                  <Statistic
                    title="累计车流量"
                    value={selectedReport.vehicleCount || 0}
                    prefix={<CarOutlined style={{ color: '#1890ff' }} />}
                    valueStyle={{ color: '#1890ff', fontSize: 28 }}
                  />
                </Card>
              </Col>
            </Row>

            <Divider orientation="left" style={{ color: '#1890ff', borderColor: '#1f2940' }}>
              <Space>
                <FileTextOutlined />
                <span>活动总结</span>
              </Space>
            </Divider>

            <Card
              size="small"
              style={{
                background: 'rgba(24, 144, 255, 0.03)',
                border: '1px solid #1f2940'
              }}
            >
              <p style={{ color: '#e6f1ff', lineHeight: 1.8, margin: 0, whiteSpace: 'pre-wrap' }}>
                {selectedReport.summary || '暂无活动总结'}
              </p>
            </Card>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default ReportManager;
