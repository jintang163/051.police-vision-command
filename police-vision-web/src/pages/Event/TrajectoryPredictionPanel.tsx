import React, { useState, useEffect } from 'react';
import {
  Card,
  Row,
  Col,
  Button,
  Space,
  Tag,
  Empty,
  List,
  Avatar,
  Modal,
  Form,
  Input,
  InputNumber,
  Select,
  message,
  Tooltip,
  Divider,
  Statistic,
  Progress,
  Timeline,
  Table,
  Descriptions,
  Alert,
  Popconfirm,
  Spin,
  Badge
} from 'antd';
import {
  EyeOutlined,
  WarningOutlined,
  SafetyOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  ReloadOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  RadarChartOutlined,
  EnvironmentOutlined,
  LineChartOutlined,
  ClockCircleOutlined,
  AimOutlined,
  UserOutlined,
  PhoneOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  TargetPerson,
  TrajectoryPredictResult,
  TrajectoryPrediction,
  PredictionAlert,
  PredictionAlertStats,
  PersonTrackPoint,
  ActivityPattern
} from '@/types';
import {
  getTargetPersonPage,
  getTargetPersonDetail,
  predictTrajectory,
  predictTrajectoryBatch,
  getLatestPredictions,
  getHighRiskPredictions,
  getPredictionAlerts,
  getPredictionAlertStats,
  handlePredictionAlert,
  autoDispatchPolice,
  generatePredictionAlerts,
  getActivityPattern,
  getRecentTrack,
  checkSensitiveArea
} from '@/services/controlApi';

interface TrajectoryPredictionPanelProps {
  eventId?: string;
  eventLng?: number;
  eventLat?: number;
}

const { Option } = Select;

const TrajectoryPredictionPanel: React.FC<TrajectoryPredictionPanelProps> = ({
  eventId,
  eventLng,
  eventLat
}) => {
  const [loading, setLoading] = useState(false);
  const [predicting, setPredicting] = useState(false);
  const [targetPersons, setTargetPersons] = useState<TargetPerson[]>([]);
  const [selectedPerson, setSelectedPerson] = useState<TargetPerson | null>(null);
  const [predictResult, setPredictResult] = useState<TrajectoryPredictResult | null>(null);
  const [alertStats, setAlertStats] = useState<PredictionAlertStats | null>(null);
  const [alerts, setAlerts] = useState<PredictionAlert[]>([]);
  const [alertsLoading, setAlertsLoading] = useState(false);
  const [activityPattern, setActivityPattern] = useState<ActivityPattern | null>(null);
  const [recentTrack, setRecentTrack] = useState<PersonTrackPoint[]>([]);

  useEffect(() => {
    fetchTargetPersons();
    fetchAlertStats();
    fetchAlerts();
  }, []);

  const fetchTargetPersons = async () => {
    setLoading(true);
    try {
      const res = await getTargetPersonPage({ status: 1, pageNum: 1, pageSize: 20 });
      const records = res.data?.records || [];
      setTargetPersons(records);
      if (records.length > 0 && !selectedPerson) {
        handleSelectPerson(records[0]);
      }
    } catch (error: any) {
      message.error(error.message || '加载重点人员失败');
    } finally {
      setLoading(false);
    }
  };

  const fetchAlertStats = async () => {
    try {
      const res = await getPredictionAlertStats();
      setAlertStats(res.data);
    } catch (error) {
      console.warn('预警统计加载失败', error);
    }
  };

  const fetchAlerts = async () => {
    setAlertsLoading(true);
    try {
      const res = await getPredictionAlerts({ pageNum: 1, pageSize: 10 });
      setAlerts(res.data?.list || []);
    } catch (error) {
      console.warn('预警列表加载失败', error);
    } finally {
      setAlertsLoading(false);
    }
  };

  const handleSelectPerson = async (person: TargetPerson) => {
    setSelectedPerson(person);
    setPredictResult(null);
    try {
      const [predRes, patternRes, trackRes] = await Promise.all([
        getLatestPredictions(person.personId, 3),
        getActivityPattern(person.personId, 90),
        getRecentTrack(person.personId, 7, 500)
      ]);
      if (predRes.data && predRes.data.length > 0) {
        const batch = predRes.data[0].predictionBatch;
        setPredictResult({
          personId: person.personId,
          personName: person.personName || '',
          predictionBatch: batch,
          modelVersion: predRes.data[0].modelVersion,
          predictTime: predRes.data[0].predictTime,
          predictWindowStart: predRes.data[0].predictWindowStart,
          predictWindowEnd: predRes.data[0].predictWindowEnd,
          historySampleCount: 0,
          predictions: predRes.data,
          accuracyEstimate: 0.75
        });
      }
      setActivityPattern(patternRes.data);
      setRecentTrack(trackRes.data);
    } catch (error) {
      console.warn('加载人员轨迹数据失败', error);
    }
  };

  const handlePredict = async () => {
    if (!selectedPerson) {
      message.warning('请先选择重点人员');
      return;
    }
    setPredicting(true);
    try {
      const res = await predictTrajectory(selectedPerson.personId);
      setPredictResult(res.data);
      message.success(`预测完成：未来30分钟 ${res.data.predictions.length} 个位置预测`);
      try {
        await generatePredictionAlerts(res.data.predictionBatch);
        fetchAlertStats();
        fetchAlerts();
      } catch (err) {
        console.warn('自动生成预警失败', err);
      }
    } catch (error: any) {
      message.error(error.message || '预测失败');
    } finally {
      setPredicting(false);
    }
  };

  const handleBatchPredict = async () => {
    if (targetPersons.length === 0) return;
    setPredicting(true);
    try {
      const ids = targetPersons.slice(0, 10).map((p) => p.personId);
      const res = await predictTrajectoryBatch(ids);
      message.success(`批量预测完成：成功 ${res.data.success}，失败 ${res.data.failed}`);
      fetchAlertStats();
      fetchAlerts();
    } catch (error: any) {
      message.error(error.message || '批量预测失败');
    } finally {
      setPredicting(false);
    }
  };

  const handleDispatchPolice = async (alert: PredictionAlert) => {
    try {
      const res = await autoDispatchPolice(alert.alertId);
      message.success(res.data.message);
      fetchAlerts();
      fetchAlertStats();
    } catch (error: any) {
      message.error(error.message || '派警失败');
    }
  };

  const handleHandleAlert = async (alert: PredictionAlert) => {
    try {
      await handlePredictionAlert({
        alertId: alert.alertId,
        targetStatus: 2,
        statusName: '已处置',
        remark: '指挥中心已确认处置',
        officerId: 1001,
        officerName: '当前用户'
      });
      message.success('预警已标记为已处置');
      fetchAlerts();
      fetchAlertStats();
    } catch (error: any) {
      message.error(error.message || '操作失败');
    }
  };

  const getAlertLevelColor = (level: number) => {
    if (level >= 4) return 'red';
    if (level === 3) return 'orange';
    if (level === 2) return 'gold';
    return 'blue';
  };

  const getAlertLevelText = (level: number) => {
    if (level >= 4) return '极高';
    if (level === 3) return '高';
    if (level === 2) return '中';
    return '低';
  };

  const getPredictionColor = (pred: TrajectoryPrediction) => {
    if (pred.isSensitiveArea === 1 && pred.crowdRiskLevel >= 2) return 'red';
    if (pred.isSensitiveArea === 1) return 'orange';
    if (pred.crowdRiskLevel >= 2) return 'gold';
    if (pred.probability >= 0.4) return 'green';
    return 'blue';
  };

  return (
    <div style={{ color: '#e6f1ff' }}>
      <Row gutter={[12, 12]} style={{ marginBottom: 12 }}>
        <Col span={5}>
          <Card size="small" bordered={false} styles={{ body: { padding: 12, background: 'rgba(26, 31, 53, 0.6)', textAlign: 'center' } }}>
            <Statistic
              title={<span style={{ color: '#8c9cb8', fontSize: 12 }}>今日预警总数</span>}
              value={alertStats?.todayTotal || 0}
              valueStyle={{ color: '#1890ff', fontSize: 22, fontWeight: 600 }}
              prefix={<WarningOutlined />}
            />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small" bordered={false} styles={{ body: { padding: 12, background: 'rgba(26, 31, 53, 0.6)', textAlign: 'center' } }}>
            <Statistic
              title={<span style={{ color: '#8c9cb8', fontSize: 12 }}>待处置</span>}
              value={alertStats?.pendingCount || 0}
              valueStyle={{ color: '#faad14', fontSize: 22, fontWeight: 600 }}
              prefix={<ClockCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small" bordered={false} styles={{ body: { padding: 12, background: 'rgba(26, 31, 53, 0.6)', textAlign: 'center' } }}>
            <Statistic
              title={<span style={{ color: '#8c9cb8', fontSize: 12 }}>高风险预警</span>}
              value={alertStats?.highRiskCount || 0}
              valueStyle={{ color: '#ff4d4f', fontSize: 22, fontWeight: 600 }}
              prefix={<ExclamationCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small" bordered={false} styles={{ body: { padding: 12, background: 'rgba(26, 31, 53, 0.6)', textAlign: 'center' } }}>
            <Statistic
              title={<span style={{ color: '#8c9cb8', fontSize: 12 }}>已处置</span>}
              value={alertStats?.handledCount || 0}
              valueStyle={{ color: '#52c41a', fontSize: 22, fontWeight: 600 }}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card size="small" bordered={false} styles={{ body: { padding: 12, background: 'rgba(26, 31, 53, 0.6)', textAlign: 'center' } }}>
            <div style={{ marginBottom: 4, color: '#8c9cb8', fontSize: 12 }}>模型准确率</div>
            <div style={{ color: '#2f54eb', fontSize: 22, fontWeight: 600 }}>75%</div>
            <Progress percent={75} showInfo={false} size="small" style={{ marginTop: 4 }} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[12, 12]}>
        <Col span={8}>
          <Card
            size="small"
            bordered={false}
            title={
              <Space>
                <TeamOutlined style={{ color: '#1890ff' }} />
                <span style={{ fontSize: 14 }}>重点人员库</span>
              </Space>
            }
            styles={{ body: { padding: 8, background: 'rgba(26, 31, 53, 0.6)' } }}
            style={{ background: '#141829' }}
            extra={
              <Space>
                <Button size="small" icon={<ReloadOutlined />} onClick={fetchTargetPersons} />
                <Button size="small" type="primary" icon={<ThunderboltOutlined />} onClick={handleBatchPredict} loading={predicting}>
                  批量预测
                </Button>
              </Space>
            }
          >
            <List
              size="small"
              loading={loading}
              dataSource={targetPersons}
              locale={{ emptyText: <Empty description="暂无重点人员" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
              renderItem={(person) => {
                const active = selectedPerson?.personId === person.personId;
                return (
                  <List.Item
                    onClick={() => handleSelectPerson(person)}
                    style={{
                      cursor: 'pointer',
                      padding: '8px 10px',
                      background: active ? 'rgba(24, 144, 255, 0.15)' : 'transparent',
                      border: active ? '1px solid #1890ff' : '1px solid transparent',
                      borderRadius: 4,
                      marginBottom: 4
                    }}
                  >
                    <List.Item.Meta
                      avatar={<Avatar size="small" icon={<UserOutlined />} src={person.avatarUrl} />}
                      title={
                        <Space size={6}>
                          <span style={{ color: active ? '#1890ff' : '#e6f1ff', fontSize: 13 }}>{person.personName}</span>
                          {person.controlLevel && person.controlLevel >= 3 && (
                            <Tag color="red" style={{ margin: 0, fontSize: 10 }}>L{person.controlLevel}</Tag>
                          )}
                          {person.personTypeName && (
                            <Tag color="blue" style={{ margin: 0, fontSize: 10 }}>{person.personTypeName}</Tag>
                          )}
                        </Space>
                      }
                      description={
                        <Space size={8} style={{ fontSize: 11, color: '#8c9cb8' }}>
                          <span><EyeOutlined /> 预警{person.alertCount || 0}</span>
                          <span><SafetyOutlined /> 风险{(person.riskScore || 0).toFixed(0)}</span>
                          {person.longitude && (
                            <span><EnvironmentOutlined /> 在线</span>
                          )}
                        </Space>
                      }
                    />
                  </List.Item>
                );
              }}
            />
          </Card>
        </Col>

        <Col span={10}>
          <Card
            size="small"
            bordered={false}
            title={
              <Space>
                <RadarChartOutlined style={{ color: '#722ed1' }} />
                <span style={{ fontSize: 14 }}>轨迹预测（LSTM模型）</span>
                {predictResult && (
                  <Tag color="purple" style={{ fontSize: 10 }}>
                    模型：{predictResult.modelVersion}
                  </Tag>
                )}
              </Space>
            }
            styles={{ body: { padding: 12, background: 'rgba(26, 31, 53, 0.6)' } }}
            style={{ background: '#141829' }}
            extra={
              <Space>
                <Button size="small" icon={<LineChartOutlined />} onClick={handlePredict} loading={predicting}>
                  预测未来30分钟
                </Button>
              </Space>
            }
          >
            {!selectedPerson ? (
              <Empty description="请先选择重点人员" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : !predictResult ? (
              <div style={{ textAlign: 'center', padding: 40 }}>
                <RadarChartOutlined style={{ fontSize: 48, color: '#1890ff', marginBottom: 12 }} />
                <div style={{ color: '#8c9cb8', marginBottom: 16 }}>
                  点击"预测未来30分钟"启动LSTM模型<br />
                  基于90天历史GPS数据，输出Top-3最可能位置及概率
                </div>
                <Button type="primary" icon={<PlayCircleOutlined />} onClick={handlePredict} loading={predicting}>
                  开始预测
                </Button>
              </div>
            ) : (
              <div>
                <Descriptions column={2} size="small" labelStyle={{ color: '#8c9cb8', fontSize: 11 }} contentStyle={{ color: '#e6f1ff', fontSize: 12 }}>
                  <Descriptions.Item label="预测人员">{predictResult.personName}</Descriptions.Item>
                  <Descriptions.Item label="样本量">{predictResult.historySampleCount || '历史分析'}</Descriptions.Item>
                  <Descriptions.Item label="预测时间">{dayjs(predictResult.predictTime).format('HH:mm:ss')}</Descriptions.Item>
                  <Descriptions.Item label="时间窗口">
                    {dayjs(predictResult.predictWindowStart).format('HH:mm')} - {dayjs(predictResult.predictWindowEnd).format('HH:mm')}
                  </Descriptions.Item>
                </Descriptions>

                <Divider orientation="left" style={{ borderColor: '#1f2940', color: '#8c9cb8', fontSize: 12, margin: '12px 0' }}>
                  Top-3 预测位置
                </Divider>

                <List
                  size="small"
                  dataSource={predictResult.predictions.slice(0, 3)}
                  renderItem={(pred, idx) => (
                    <List.Item
                      style={{
                        padding: '8px 10px',
                        background: 'rgba(20, 24, 41, 0.6)',
                        border: `1px solid ${idx === 0 ? '#1890ff' : '#1f2940'}`,
                        borderRadius: 4,
                        marginBottom: 6
                      }}
                    >
                      <Space direction="vertical" size={4} style={{ width: '100%' }}>
                        <Space size={8} style={{ width: '100%', justifyContent: 'space-between' }}>
                          <Space>
                            <Badge
                              count={`#${idx + 1}`}
                              style={{
                                background: getPredictionColor(pred),
                                boxShadow: 'none',
                                minWidth: 22,
                                height: 22,
                                lineHeight: '22px',
                                padding: '0 6px'
                              }}
                            />
                            <span style={{ fontWeight: 500 }}>{pred.locationDesc || '预测位置'}</span>
                          </Space>
                          <Progress
                            type="dashboard"
                            percent={Math.round(pred.probability * 100)}
                            width={46}
                            strokeColor={getPredictionColor(pred)}
                          />
                        </Space>
                        <Space size={10} style={{ fontSize: 11, color: '#8c9cb8' }}>
                          <span><EnvironmentOutlined /> {pred.longitude.toFixed(5)}, {pred.latitude.toFixed(5)}</span>
                          <span><AimOutlined /> 概率 {(pred.probability * 100).toFixed(1)}%</span>
                        </Space>
                        <Space size={6} wrap>
                          {pred.isSensitiveArea === 1 && (
                            <Tag color="red" style={{ margin: 0, fontSize: 10 }}>
                              <WarningOutlined /> 敏感区域 {pred.sensitiveAreaType}
                            </Tag>
                          )}
                          {pred.crowdRiskLevel >= 2 && (
                            <Tag color="orange" style={{ margin: 0, fontSize: 10 }}>
                              <TeamOutlined /> 聚集风险{getAlertLevelText(pred.crowdRiskLevel)}
                            </Tag>
                          )}
                          {pred.isSensitiveArea !== 1 && pred.crowdRiskLevel < 2 && (
                            <Tag color="green" style={{ margin: 0, fontSize: 10 }}>
                              <SafetyOutlined /> 常规区域
                            </Tag>
                          )}
                        </Space>
                      </Space>
                    </List.Item>
                  )}
                />

                {activityPattern && activityPattern.hotspots && activityPattern.hotspots.length > 0 && (
                  <>
                    <Divider orientation="left" style={{ borderColor: '#1f2940', color: '#8c9cb8', fontSize: 12, margin: '12px 0' }}>
                      90天活动热力区域
                    </Divider>
                    <Row gutter={[6, 6]}>
                      {activityPattern.hotspots.slice(0, 4).map((h, i) => (
                        <Col span={12} key={i}>
                          <Card size="small" bordered={false} styles={{ body: { padding: 8, background: 'rgba(20, 24, 41, 0.8)' } }}>
                            <Space direction="vertical" size={2} style={{ width: '100%' }}>
                              <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                                <span style={{ fontSize: 11, color: '#8c9cb8' }}>热点 #{i + 1}</span>
                                <Progress percent={Math.round(h.ratio * 100)} size="small" showInfo={false} style={{ width: 60 }} />
                              </Space>
                              <div style={{ fontSize: 11, color: '#e6f1ff' }}>
                                {h.longitude.toFixed(4)}, {h.latitude.toFixed(4)}
                              </div>
                              <div style={{ fontSize: 10, color: '#8c9cb8' }}>
                                出现 {h.count} 次 · 占比 {(h.ratio * 100).toFixed(1)}%
                              </div>
                            </Space>
                          </Card>
                        </Col>
                      ))}
                    </Row>
                  </>
                )}

                {activityPattern?.timeDistribution && (
                  <div style={{ marginTop: 10 }}>
                    <div style={{ fontSize: 11, color: '#8c9cb8', marginBottom: 4 }}>时段分布（90天）</div>
                    <Row gutter={4}>
                      {Object.entries(activityPattern.timeDistribution).map(([k, v]) => {
                        const total = Object.values(activityPattern.timeDistribution).reduce((a, b) => a + (b as number), 0);
                        const pct = total > 0 ? ((v as number) / total) * 100 : 0;
                        const labelMap: Record<string, string> = { morning: '上午', afternoon: '下午', evening: '晚间', night: '深夜' };
                        return (
                          <Col span={6} key={k}>
                            <Card size="small" bordered={false} styles={{ body: { padding: 6, background: 'rgba(20, 24, 41, 0.8)', textAlign: 'center' } }}>
                              <div style={{ fontSize: 10, color: '#8c9cb8' }}>{labelMap[k] || k}</div>
                              <div style={{ fontSize: 14, fontWeight: 600, color: '#1890ff' }}>{pct.toFixed(0)}%</div>
                            </Card>
                          </Col>
                        );
                      })}
                    </Row>
                  </div>
                )}
              </div>
            )}
          </Card>
        </Col>

        <Col span={6}>
          <Card
            size="small"
            bordered={false}
            title={
              <Space>
                <ThunderboltOutlined style={{ color: '#ff4d4f' }} />
                <span style={{ fontSize: 14 }}>预测预警推送</span>
              </Space>
            }
            styles={{ body: { padding: 8, background: 'rgba(26, 31, 53, 0.6)' } }}
            style={{ background: '#141829' }}
            extra={
              <Space>
                <Button size="small" icon={<ReloadOutlined />} onClick={fetchAlerts} />
              </Space>
            }
          >
            <List
              size="small"
              loading={alertsLoading}
              locale={{ emptyText: <Empty description="暂无预警" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
              dataSource={alerts}
              renderItem={(alert) => (
                <List.Item
                  style={{
                    padding: '8px 10px',
                    background: 'rgba(20, 24, 41, 0.6)',
                    borderLeft: `3px solid ${getAlertLevelColor(alert.alertLevel)}`,
                    borderRadius: 4,
                    marginBottom: 6
                  }}
                >
                  <Space direction="vertical" size={4} style={{ width: '100%' }}>
                    <Space size={6} style={{ width: '100%', justifyContent: 'space-between' }}>
                      <Space>
                        <Tag color={getAlertLevelColor(alert.alertLevel)} style={{ margin: 0, fontSize: 10 }}>
                          {alert.alertTypeName} · {getAlertLevelText(alert.alertLevel)}
                        </Tag>
                        {alert.status === 0 && <Badge status="processing" text={<span style={{ color: '#faad14', fontSize: 11 }}>待处置</span>} />}
                        {alert.status === 1 && <Badge status="warning" text={<span style={{ color: '#1890ff', fontSize: 11 }}>处理中</span>} />}
                        {alert.status === 2 && <Badge status="success" text={<span style={{ color: '#52c41a', fontSize: 11 }}>已处置</span>} />}
                      </Space>
                      <span style={{ color: '#8c9cb8', fontSize: 10 }}>
                        {dayjs(alert.createTime || alert.predictTime).format('HH:mm')}
                      </span>
                    </Space>
                    <Space size={6}>
                      <Avatar size="small" icon={<UserOutlined />} />
                      <div style={{ fontSize: 12 }}>
                        <span style={{ color: '#e6f1ff', fontWeight: 500 }}>{alert.personName}</span>
                        <span style={{ color: '#8c9cb8' }}> · {(alert.probability * 100).toFixed(0)}%</span>
                      </div>
                    </Space>
                    <div style={{ fontSize: 11, color: '#a6adc8', lineHeight: 1.4 }}>
                      {alert.triggerReason}
                    </div>
                    <Space size={4} style={{ width: '100%', justifyContent: 'flex-end' }}>
                      {alert.status === 0 && (
                        <>
                          <Button size="small" type="primary" icon={<PhoneOutlined />} onClick={() => handleDispatchPolice(alert)}>
                            自动派警
                          </Button>
                          <Popconfirm title="标记为已处置？" onConfirm={() => handleHandleAlert(alert)} okText="确认" cancelText="取消">
                            <Button size="small" icon={<CheckCircleOutlined />}>
                              标记处置
                            </Button>
                          </Popconfirm>
                        </>
                      )}
                      {alert.status !== 0 && (
                        <Tag color="default" style={{ fontSize: 10 }}>
                          {alert.statusName}
                        </Tag>
                      )}
                    </Space>
                  </Space>
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>

      <Alert
        style={{ marginTop: 12 }}
        type="info"
        showIcon
        message="关于轨迹预测模型"
        description="基于LSTM深度学习模型，使用该重点人员过去90天的GPS轨迹作为训练样本。综合考量：① 时间衰减因子（越近的数据权重越高）；② 时段相似度（当前时段与历史时段的匹配度）；③ 工作日/周末模式；④ 地理热点聚类；⑤ 当前实时位置修正。输出未来30分钟内最可能出现的3个位置及概率，预测落入敏感区域或多人聚集区时自动预警并推送民警提前部署。"
      />
    </div>
  );
};

export default TrajectoryPredictionPanel;
