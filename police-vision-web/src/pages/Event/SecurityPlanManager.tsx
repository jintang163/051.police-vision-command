import React, { useState, useEffect, useRef } from 'react';
import {
  Card,
  Row,
  Col,
  Button,
  Space,
  Tag,
  Modal,
  Form,
  Input,
  Select,
  InputNumber,
  List,
  Drawer,
  Tree,
  Divider,
  message,
  Popconfirm,
  Statistic,
  Empty
} from 'antd';
import {
  PlusOutlined,
  EyeOutlined,
  DeleteOutlined,
  EditOutlined,
  PlayCircleOutlined,
  SendOutlined,
  FolderOutlined,
  TeamOutlined,
  EnvironmentOutlined,
  FileTextOutlined,
  AppstoreOutlined,
  MinusCircleOutlined
} from '@ant-design/icons';
import {
  SecurityPlan,
  TaskGroup,
  Post,
  SecurityPlanCreateDTO
} from '@/types';
import {
  getSecurityPlanList,
  getSecurityPlanDetail,
  createSecurityPlan,
  updateSecurityPlan,
  deleteSecurityPlan,
  publishSecurityPlan,
  executeSecurityPlan,
  archiveSecurityPlan
} from '@/services/eventApi';
import dayjs from 'dayjs';

declare global {
  interface Window {
    TMap: any;
  }
}

interface SecurityPlanManagerProps {
  eventId: string;
}

const { TextArea } = Input;
const { Option } = Select;
const { TreeNode } = Tree;

const SecurityPlanManager: React.FC<SecurityPlanManagerProps> = ({ eventId }) => {
  const [plans, setPlans] = useState<SecurityPlan[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [modalVisible, setModalVisible] = useState<boolean>(false);
  const [drawerVisible, setDrawerVisible] = useState<boolean>(false);
  const [editingPlan, setEditingPlan] = useState<SecurityPlan | null>(null);
  const [detailPlan, setDetailPlan] = useState<SecurityPlan | null>(null);
  const [form] = Form.useForm();
  const [taskGroups, setTaskGroups] = useState<TaskGroup[]>([]);
  const [selectedPostIndex, setSelectedPostIndex] = useState<number | null>(null);
  const [selectedGroupIndex, setSelectedGroupIndex] = useState<number | null>(null);

  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const markersRef = useRef<any>(null);
  const drawerMapRef = useRef<HTMLDivElement>(null);
  const drawerMapInstanceRef = useRef<any>(null);
  const drawerMarkersRef = useRef<any>(null);

  useEffect(() => {
    fetchPlans();
  }, [eventId]);

  useEffect(() => {
    if (modalVisible && mapContainerRef.current) {
      setTimeout(() => {
        initMap();
      }, 100);
    }
  }, [modalVisible]);

  useEffect(() => {
    if (drawerVisible && drawerMapRef.current) {
      setTimeout(() => {
        initDrawerMap();
      }, 100);
    }
  }, [drawerVisible, detailPlan]);

  const fetchPlans = async () => {
    if (!eventId) return;
    setLoading(true);
    try {
      const res = await getSecurityPlanList(eventId);
      setPlans(res.data || []);
    } catch (error: any) {
      message.error(error.message || '获取方案列表失败');
    } finally {
      setLoading(false);
    }
  };

  const initMap = () => {
    if (!window.TMap || !mapContainerRef.current) return;
    if (mapRef.current) {
      mapRef.current.destroy();
    }

    const center = new window.TMap.LatLng(39.9042, 116.4074);

    mapRef.current = new window.TMap.Map(mapContainerRef.current, {
      center,
      zoom: 14,
      pitch: 0,
      rotation: 0,
      viewMode: '2D',
      mapStyleId: 'style1',
      baseMap: {
        type: 'vector',
        features: ['base', 'road', 'point']
      }
    });

    markersRef.current = new window.TMap.MultiMarker({
      map: mapRef.current
    });

    mapRef.current.on('click', (e: any) => {
      if (selectedGroupIndex !== null && selectedPostIndex !== null) {
        const newTaskGroups = [...taskGroups];
        newTaskGroups[selectedGroupIndex].posts[selectedPostIndex].position = {
          lat: e.latLng.getLat().toFixed(6),
          lng: e.latLng.getLng().toFixed(6)
        };
        setTaskGroups(newTaskGroups);
        updateMapMarkers(newTaskGroups);
        message.success('岗位位置已设置');
      }
    });

    updateMapMarkers(taskGroups);
  };

  const initDrawerMap = () => {
    if (!window.TMap || !drawerMapRef.current || !detailPlan?.taskGroups) return;
    if (drawerMapInstanceRef.current) {
      drawerMapInstanceRef.current.destroy();
    }

    const center = new window.TMap.LatLng(39.9042, 116.4074);

    drawerMapInstanceRef.current = new window.TMap.Map(drawerMapRef.current, {
      center,
      zoom: 14,
      pitch: 0,
      rotation: 0,
      viewMode: '2D',
      mapStyleId: 'style1',
      baseMap: {
        type: 'vector',
        features: ['base', 'road', 'point']
      }
    });

    drawerMarkersRef.current = new window.TMap.MultiMarker({
      map: drawerMapInstanceRef.current
    });

    const markers = detailPlan.taskGroups.flatMap((group, groupIndex) =>
      group.posts
        .filter(post => post.position)
        .map((post, postIndex) => ({
          id: `post_${groupIndex}_${postIndex}`,
          position: new window.TMap.LatLng(post.position!.lat, post.position!.lng),
          properties: post,
          styles: 'post_marker'
        }))
    );

    if (markers.length > 0 && drawerMarkersRef.current) {
      drawerMarkersRef.current.setStyles({
        post_marker: new window.TMap.MarkerStyle({
          width: 28,
          height: 28,
          anchor: { x: 14, y: 28 },
          src: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyOCIgaGVpZ2h0PSIyOCIgdmlld0JveD0iMCAwIDI4IDI4Ij48cGF0aCBkPSJNMTQgMkM4LjQ3NyAyIDQgNi40NzcgNCAxMmMwIDggMTAgMTQgMTAgMTRzMTAtNiAxMC0xNGMwLTUuNTIzLTQuNDc3LTEwLTEwLTEwem0wIDE0YTQgNCAwIDEgMSAwLTggNCA0IDAgMCAxIDAgOHoiIGZpbGw9IiMxODkwZmYiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIyIi8+PC9zdmc+'
        })
      });
      drawerMarkersRef.current.setGeometries(markers);
    }
  };

  const updateMapMarkers = (groups: TaskGroup[]) => {
    if (!markersRef.current || !window.TMap) return;

    const markers = groups.flatMap((group, groupIndex) =>
      group.posts
        .filter(post => post.position)
        .map((post, postIndex) => ({
          id: `post_${groupIndex}_${postIndex}`,
          position: new window.TMap.LatLng(post.position!.lat, post.position!.lng),
          properties: post,
          styles: selectedGroupIndex === groupIndex && selectedPostIndex === postIndex
            ? 'post_selected'
            : 'post_normal'
        }))
    );

    markersRef.current.setStyles({
      post_normal: new window.TMap.MarkerStyle({
        width: 28,
        height: 28,
        anchor: { x: 14, y: 28 },
        src: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyOCIgaGVpZ2h0PSIyOCIgdmlld0JveD0iMCAwIDI4IDI4Ij48cGF0aCBkPSJNMTQgMkM4LjQ3NyAyIDQgNi40NzcgNCAxMmMwIDggMTAgMTQgMTAgMTRzMTAtNiAxMC0xNGMwLTUuNTIzLTQuNDc3LTEwLTEwLTEwem0wIDE0YTQgNCAwIDEgMSAwLTggNCA0IDAgMCAxIDAgOHoiIGZpbGw9IiMxODkwZmYiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIyIi8+PC9zdmc+'
      }),
      post_selected: new window.TMap.MarkerStyle({
        width: 36,
        height: 36,
        anchor: { x: 18, y: 36 },
        src: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzNiIgaGVpZ2h0PSIzNiIgdmlld0JveD0iMCAwIDM2IDM2Ij48cGF0aCBkPSJNMTggMkM5LjcxNiAyIDIgOS43MTYgMiAxOGMwIDEyIDE2IDE2IDE2IDE2czE2LTQgMTYtMTZDMzQgOS43MTYgMjYuMjg0IDIgMTggMnptMCAyMmE2IDYgMCAxIDEgMC0xMiA2IDYgMCAwIDEgMCAxMnoiIGZpbGw9IiNmYWFkMTQiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIyIi8+PC9zdmc+'
      })
    });

    markersRef.current.setGeometries(markers);
  };

  const getStatusColor = (status: number) => {
    switch (status) {
      case 0: return 'default';
      case 1: return 'blue';
      case 2: return 'green';
      case 3: return 'purple';
      default: return 'default';
    }
  };

  const getStatusText = (status: number) => {
    switch (status) {
      case 0: return '草稿';
      case 1: return '已发布';
      case 2: return '执行中';
      case 3: return '已归档';
      default: return '未知';
    }
  };

  const getPlanTypeText = (type: string) => {
    switch (type) {
      case 'route': return '路线安保';
      case 'point': return '点位安保';
      case 'mixed': return '混合安保';
      default: return '未知';
    }
  };

  const handleAdd = () => {
    setEditingPlan(null);
    setTaskGroups([]);
    setSelectedGroupIndex(null);
    setSelectedPostIndex(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = async (plan: SecurityPlan) => {
    try {
      const res = await getSecurityPlanDetail(plan.id);
      setEditingPlan(plan);
      setTaskGroups(res.data.taskGroups || []);
      setSelectedGroupIndex(null);
      setSelectedPostIndex(null);
      form.setFieldsValue({
        planName: res.data.planName,
        planType: res.data.planType,
        description: res.data.description
      });
      setModalVisible(true);
    } catch (error: any) {
      message.error(error.message || '获取方案详情失败');
    }
  };

  const handleDelete = async (planId: string) => {
    try {
      await deleteSecurityPlan(planId);
      message.success('删除成功');
      fetchPlans();
    } catch (error: any) {
      message.error(error.message || '删除失败');
    }
  };

  const handlePublish = async (planId: string) => {
    try {
      await publishSecurityPlan(planId);
      message.success('发布成功');
      fetchPlans();
    } catch (error: any) {
      message.error(error.message || '发布失败');
    }
  };

  const handleExecute = async (planId: string) => {
    try {
      await executeSecurityPlan(planId);
      message.success('开始执行');
      fetchPlans();
    } catch (error: any) {
      message.error(error.message || '执行失败');
    }
  };

  const handleArchive = async (planId: string) => {
    try {
      await archiveSecurityPlan(planId);
      message.success('已归档');
      fetchPlans();
    } catch (error: any) {
      message.error(error.message || '归档失败');
    }
  };

  const handleViewDetail = async (plan: SecurityPlan) => {
    try {
      const res = await getSecurityPlanDetail(plan.id);
      setDetailPlan(res.data);
      setDrawerVisible(true);
    } catch (error: any) {
      message.error(error.message || '获取方案详情失败');
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const dto: SecurityPlanCreateDTO = {
        eventId,
        planName: values.planName,
        planType: values.planType,
        taskGroups,
        description: values.description
      };

      if (editingPlan) {
        await updateSecurityPlan(editingPlan.id, dto);
        message.success('更新成功');
      } else {
        await createSecurityPlan(dto);
        message.success('创建成功');
      }

      setModalVisible(false);
      fetchPlans();
    } catch (error: any) {
      if (error.errorFields) {
        return;
      }
      message.error(error.message || '保存失败');
    }
  };

  const addTaskGroup = () => {
    const newGroup: TaskGroup = {
      groupName: `任务组${taskGroups.length + 1}`,
      groupLeader: '',
      description: '',
      posts: []
    };
    setTaskGroups([...taskGroups, newGroup]);
  };

  const removeTaskGroup = (index: number) => {
    const newGroups = taskGroups.filter((_, i) => i !== index);
    setTaskGroups(newGroups);
    if (selectedGroupIndex === index) {
      setSelectedGroupIndex(null);
      setSelectedPostIndex(null);
    }
    updateMapMarkers(newGroups);
  };

  const addPost = (groupIndex: number) => {
    const newGroups = [...taskGroups];
    const postCount = newGroups[groupIndex].posts.length;
    newGroups[groupIndex].posts.push({
      postName: `岗位${postCount + 1}`,
      postNo: `P${String(groupIndex + 1).padStart(2, '0')}${String(postCount + 1).padStart(2, '0')}`,
      dutyDescription: '',
      policeOfficers: []
    });
    setTaskGroups(newGroups);
  };

  const removePost = (groupIndex: number, postIndex: number) => {
    const newGroups = [...taskGroups];
    newGroups[groupIndex].posts.splice(postIndex, 1);
    setTaskGroups(newGroups);
    if (selectedGroupIndex === groupIndex && selectedPostIndex === postIndex) {
      setSelectedPostIndex(null);
    }
    updateMapMarkers(newGroups);
  };

  const updateTaskGroup = (index: number, field: keyof TaskGroup, value: string) => {
    const newGroups = [...taskGroups];
    (newGroups[index] as any)[field] = value;
    setTaskGroups(newGroups);
  };

  const updatePost = (groupIndex: number, postIndex: number, field: keyof Post, value: string) => {
    const newGroups = [...taskGroups];
    (newGroups[groupIndex].posts[postIndex] as any)[field] = value;
    setTaskGroups(newGroups);
  };

  const selectPost = (groupIndex: number, postIndex: number) => {
    setSelectedGroupIndex(groupIndex);
    setSelectedPostIndex(postIndex);
    updateMapMarkers(taskGroups);
  };

  const renderTaskGroupsForm = () => (
    <div style={{ maxHeight: 300, overflow: 'auto' }}>
      <List
        dataSource={taskGroups}
        locale={{ emptyText: '暂无任务组，请点击添加' }}
        renderItem={(group, groupIndex) => (
          <List.Item
            key={groupIndex}
            style={{
              padding: 12,
              marginBottom: 8,
              background: 'rgba(26, 31, 53, 0.5)',
              borderRadius: 8,
              border: '1px solid #1f2940',
              display: 'block'
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
              <Space>
                <TeamOutlined style={{ color: '#1890ff' }} />
                <span style={{ color: '#e6f1ff', fontWeight: 500 }}>
                  任务组 {groupIndex + 1}
                </span>
              </Space>
              <Button
                type="text"
                danger
                size="small"
                icon={<DeleteOutlined />}
                onClick={() => removeTaskGroup(groupIndex)}
              >
                删除组
              </Button>
            </div>
            <Row gutter={[8, 8]} style={{ marginBottom: 8 }}>
              <Col span={12}>
                <Input
                  size="small"
                  placeholder="组名"
                  value={group.groupName}
                  onChange={(e) => updateTaskGroup(groupIndex, 'groupName', e.target.value)}
                  style={{ background: '#141829', borderColor: '#1f2940' }}
                />
              </Col>
              <Col span={12}>
                <Input
                  size="small"
                  placeholder="组长"
                  value={group.groupLeader}
                  onChange={(e) => updateTaskGroup(groupIndex, 'groupLeader', e.target.value)}
                  style={{ background: '#141829', borderColor: '#1f2940' }}
                />
              </Col>
            </Row>
            <Input
              size="small"
              placeholder="描述"
              value={group.description}
              onChange={(e) => updateTaskGroup(groupIndex, 'description', e.target.value)}
              style={{ background: '#141829', borderColor: '#1f2940', marginBottom: 8 }}
            />
            <Divider style={{ margin: '8px 0' }} />
            <div style={{ marginBottom: 8 }}>
              <Space>
                <span style={{ color: '#8c9cb8', fontSize: 12 }}>岗位列表</span>
                <Button
                  type="link"
                  size="small"
                  icon={<PlusOutlined />}
                  onClick={() => addPost(groupIndex)}
                >
                  添加岗位
                </Button>
              </Space>
            </div>
            <List
              size="small"
              dataSource={group.posts}
              locale={{ emptyText: '暂无岗位' }}
              renderItem={(post, postIndex) => (
                <List.Item
                  key={postIndex}
                  style={{
                    padding: '8px',
                    marginBottom: 4,
                    background: selectedGroupIndex === groupIndex && selectedPostIndex === postIndex
                      ? 'rgba(24, 144, 255, 0.2)'
                      : 'rgba(20, 24, 41, 0.5)',
                    borderRadius: 4,
                    border: `1px solid ${selectedGroupIndex === groupIndex && selectedPostIndex === postIndex
                      ? '#1890ff'
                      : '#1f2940'}`,
                    cursor: 'pointer'
                  }}
                  onClick={() => selectPost(groupIndex, postIndex)}
                >
                  <div style={{ width: '100%' }}>
                    <Row gutter={[8, 4]}>
                      <Col span={8}>
                        <Input
                          size="small"
                          placeholder="岗位名称"
                          value={post.postName}
                          onChange={(e) => updatePost(groupIndex, postIndex, 'postName', e.target.value)}
                          onClick={(e) => e.stopPropagation()}
                          style={{ background: '#141829', borderColor: '#1f2940' }}
                        />
                      </Col>
                      <Col span={8}>
                        <Input
                          size="small"
                          placeholder="岗位编号"
                          value={post.postNo}
                          onChange={(e) => updatePost(groupIndex, postIndex, 'postNo', e.target.value)}
                          onClick={(e) => e.stopPropagation()}
                          style={{ background: '#141829', borderColor: '#1f2940' }}
                        />
                      </Col>
                      <Col span={6}>
                        {post.position ? (
                          <Tag color="green" style={{ margin: 0 }}>
                            已设点位
                          </Tag>
                        ) : (
                          <Tag color="default" style={{ margin: 0 }}>
                            未设点位
                          </Tag>
                        )}
                      </Col>
                      <Col span={2} style={{ textAlign: 'right' }}>
                        <Button
                          type="text"
                          danger
                          size="small"
                          icon={<MinusCircleOutlined />}
                          onClick={(e) => {
                            e.stopPropagation();
                            removePost(groupIndex, postIndex);
                          }}
                        />
                      </Col>
                    </Row>
                  </div>
                </List.Item>
              )}
            />
          </List.Item>
        )}
      />
    </div>
  );

  const renderPlanCard = (plan: SecurityPlan) => (
    <Col span={8} key={plan.id}>
      <Card
        className="tech-card"
        style={{ height: '100%' }}
        styles={{
          body: { padding: 16 }
        }}
        bordered={false}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
          <div>
            <div style={{ fontSize: 16, fontWeight: 600, color: '#e6f1ff', marginBottom: 8 }}>
              <FileTextOutlined style={{ color: '#1890ff', marginRight: 6 }} />
              {plan.planName}
            </div>
            <Space size={[8, 4]} wrap>
              <Tag color="blue">{getPlanTypeText(plan.planType)}</Tag>
              <Tag color={getStatusColor(plan.status)}>{getStatusText(plan.status)}</Tag>
            </Space>
          </div>
        </div>

        <Row gutter={[8, 8]} style={{ marginBottom: 12 }}>
          <Col span={12}>
            <div style={{
              padding: 8,
              background: 'rgba(26, 31, 53, 0.5)',
              borderRadius: 6,
              textAlign: 'center'
            }}>
              <div style={{ fontSize: 11, color: '#8c9cb8', marginBottom: 4 }}>任务组数</div>
              <div style={{
                fontSize: 20,
                fontWeight: 600,
                background: 'linear-gradient(180deg, #1890ff, #00d4ff)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent'
              }}>
                {plan.taskGroupCount}
              </div>
            </div>
          </Col>
          <Col span={12}>
            <div style={{
              padding: 8,
              background: 'rgba(26, 31, 53, 0.5)',
              borderRadius: 6,
              textAlign: 'center'
            }}>
              <div style={{ fontSize: 11, color: '#8c9cb8', marginBottom: 4 }}>岗位数</div>
              <div style={{
                fontSize: 20,
                fontWeight: 600,
                background: 'linear-gradient(180deg, #52c41a, #13c2c2)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent'
              }}>
                {plan.postCount}
              </div>
            </div>
          </Col>
        </Row>

        <div style={{ fontSize: 11, color: '#8c9cb8', marginBottom: 12 }}>
          更新时间：{dayjs(plan.updateTime).format('YYYY-MM-DD HH:mm')}
        </div>

        <Space wrap size={[4, 4]}>
          <Button
            size="small"
            type="link"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(plan)}
          >
            详情
          </Button>
          {plan.status === 0 && (
            <Button
              size="small"
              type="link"
              icon={<EditOutlined />}
              onClick={() => handleEdit(plan)}
            >
              编辑
            </Button>
          )}
          {plan.status === 0 && (
            <Button
              size="small"
              type="link"
              icon={<SendOutlined />}
              onClick={() => handlePublish(plan.id)}
            >
              发布
            </Button>
          )}
          {plan.status === 1 && (
            <Button
              size="small"
              type="link"
              icon={<PlayCircleOutlined />}
              onClick={() => handleExecute(plan.id)}
            >
              执行
            </Button>
          )}
          {(plan.status === 1 || plan.status === 2) && (
            <Button
              size="small"
              type="link"
              icon={<FolderOutlined />}
              onClick={() => handleArchive(plan.id)}
            >
              归档
            </Button>
          )}
          {plan.status === 0 && (
            <Popconfirm
              title="确认删除该方案？"
              onConfirm={() => handleDelete(plan.id)}
              okText="确认"
              cancelText="取消"
            >
              <Button
                size="small"
                type="link"
                danger
                icon={<DeleteOutlined />}
              >
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      </Card>
    </Col>
  );

  const renderTreeData = () => {
    if (!detailPlan?.taskGroups) return [];
    return detailPlan.taskGroups.map((group, groupIndex) => ({
      title: (
        <Space>
          <TeamOutlined style={{ color: '#1890ff' }} />
          <span>{group.groupName}</span>
          <Tag color="blue" style={{ marginLeft: 8 }}>
            {group.posts.length} 岗位
          </Tag>
        </Space>
      ),
      key: `group_${groupIndex}`,
      children: group.posts.map((post, postIndex) => ({
        title: (
          <Space>
            <EnvironmentOutlined style={{ color: '#52c41a' }} />
            <span>{post.postName}</span>
            <span style={{ color: '#8c9cb8', fontSize: 11 }}>({post.postNo})</span>
          </Space>
        ),
        key: `post_${groupIndex}_${postIndex}`
      }))
    }));
  };

  return (
    <div className="tech-card" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div className="corner-decoration corner-tl" />
      <div className="corner-decoration corner-tr" />
      <div className="corner-decoration corner-bl" />
      <div className="corner-decoration corner-br" />

      <div className="panel-header">
        <span className="panel-title">安保方案管理</span>
        <Button
          type="primary"
          size="small"
          icon={<PlusOutlined />}
          onClick={handleAdd}
        >
          新增方案
        </Button>
      </div>

      <div style={{ flex: 1, overflow: 'auto', padding: '0 16px 16px' }}>
        {plans.length === 0 ? (
          <Empty
            description="暂无安保方案"
            style={{ marginTop: 60 }}
          />
        ) : (
          <Row gutter={[12, 12]}>
            {plans.map(plan => renderPlanCard(plan))}
          </Row>
        )}
      </div>

      <Modal
        title={editingPlan ? '编辑安保方案' : '新增安保方案'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={900}
        okText="保存"
        cancelText="取消"
        styles={{
          header: { background: '#1a1f35', color: '#e6f1ff', borderBottom: '1px solid #1f2940' },
          body: { background: '#141829', maxHeight: '70vh', overflow: 'auto' },
          content: { background: '#141829', border: '1px solid rgba(24, 144, 255, 0.3)' },
          footer: { background: '#1a1f35', borderTop: '1px solid #1f2940' }
        }}
      >
        <Form
          form={form}
          layout="vertical"
          style={{ color: '#e6f1ff' }}
        >
          <Row gutter={[12, 0]}>
            <Col span={12}>
              <Form.Item
                label="方案名称"
                name="planName"
                rules={[{ required: true, message: '请输入方案名称' }]}
              >
                <Input
                  placeholder="请输入方案名称"
                  style={{ background: '#141829', borderColor: '#1f2940', color: '#e6f1ff' }}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="方案类型"
                name="planType"
                rules={[{ required: true, message: '请选择方案类型' }]}
              >
                <Select
                  placeholder="请选择方案类型"
                  style={{ background: '#141829' }}
                >
                  <Option value="route">路线安保</Option>
                  <Option value="point">点位安保</Option>
                  <Option value="mixed">混合安保</Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Form.Item label="方案描述" name="description">
            <TextArea
              rows={2}
              placeholder="请输入方案描述"
              style={{ background: '#141829', borderColor: '#1f2940', color: '#e6f1ff', resize: 'none' }}
            />
          </Form.Item>

          <div style={{ marginBottom: 8 }}>
            <Space style={{ marginBottom: 8 }}>
              <span style={{ color: '#e6f1ff', fontWeight: 500 }}>任务组管理</span>
              <Button
                type="primary"
                size="small"
                icon={<PlusOutlined />}
                onClick={addTaskGroup}
              >
                添加任务组
              </Button>
              {selectedGroupIndex !== null && selectedPostIndex !== null && (
                <Tag color="orange">
                  已选中岗位，点击地图设置位置
                </Tag>
              )}
            </Space>
            {renderTaskGroupsForm()}
          </div>

          <div>
            <div style={{ color: '#e6f1ff', fontWeight: 500, marginBottom: 8 }}>
              岗位点位地图
              <span style={{ color: '#8c9cb8', fontSize: 11, marginLeft: 8 }}>
                （先在左侧选择岗位，再点击地图设置位置）
              </span>
            </div>
            <div
              ref={mapContainerRef}
              style={{
                width: '100%',
                height: 300,
                border: '1px solid #1f2940',
                borderRadius: 8
              }}
            />
          </div>
        </Form>
      </Modal>

      <Drawer
        title="方案详情"
        placement="right"
        width={520}
        open={drawerVisible}
        onClose={() => setDrawerVisible(false)}
        styles={{
          header: { background: '#1a1f35', color: '#e6f1ff', borderBottom: '1px solid #1f2940' },
          body: { background: '#141829', padding: 16 },
          content: { background: '#141829' }
        }}
      >
        {detailPlan && (
          <div style={{ color: '#e6f1ff' }}>
            <div style={{ marginBottom: 16 }}>
              <div style={{ fontSize: 18, fontWeight: 600, marginBottom: 8 }}>
                {detailPlan.planName}
              </div>
              <Space size={[8, 4]} wrap>
                <Tag color="blue">{getPlanTypeText(detailPlan.planType)}</Tag>
                <Tag color={getStatusColor(detailPlan.status)}>{getStatusText(detailPlan.status)}</Tag>
              </Space>
            </div>

            <Divider style={{ borderColor: '#1f2940', margin: '12px 0' }} />

            <div style={{ marginBottom: 16 }}>
              <div style={{ fontSize: 14, fontWeight: 500, marginBottom: 8 }}>
                基本信息
              </div>
              <Row gutter={[8, 8]}>
                <Col span={12}>
                  <Statistic
                    title={<span style={{ fontSize: 11, color: '#8c9cb8' }}>任务组数</span>}
                    value={detailPlan.taskGroupCount}
                    valueStyle={{
                      color: '#1890ff',
                      fontSize: 16,
                      fontWeight: 600
                    }}
                  />
                </Col>
                <Col span={12}>
                  <Statistic
                    title={<span style={{ fontSize: 11, color: '#8c9cb8' }}>岗位数</span>}
                    value={detailPlan.postCount}
                    valueStyle={{
                      color: '#52c41a',
                      fontSize: 16,
                      fontWeight: 600
                    }}
                  />
                </Col>
              </Row>
              <div style={{ marginTop: 8, fontSize: 12, color: '#8c9cb8' }}>
                <div>创建时间：{dayjs(detailPlan.createTime).format('YYYY-MM-DD HH:mm')}</div>
                <div>更新时间：{dayjs(detailPlan.updateTime).format('YYYY-MM-DD HH:mm')}</div>
              </div>
            </div>

            {detailPlan.description && (
              <>
                <Divider style={{ borderColor: '#1f2940', margin: '12px 0' }} />
                <div style={{ marginBottom: 16 }}>
                  <div style={{ fontSize: 14, fontWeight: 500, marginBottom: 8 }}>
                    方案描述
                  </div>
                  <div style={{
                    padding: 12,
                    background: 'rgba(26, 31, 53, 0.5)',
                    borderRadius: 6,
                    fontSize: 12,
                    color: '#8c9cb8'
                  }}>
                    {detailPlan.description}
                  </div>
                </div>
              </>
            )}

            <Divider style={{ borderColor: '#1f2940', margin: '12px 0' }} />

            <div style={{ marginBottom: 16 }}>
              <div style={{ fontSize: 14, fontWeight: 500, marginBottom: 8 }}>
                任务组结构
              </div>
              <div style={{
                padding: 12,
                background: 'rgba(26, 31, 53, 0.5)',
                borderRadius: 6,
                maxHeight: 200,
                overflow: 'auto'
              }}>
                <Tree
                  treeData={renderTreeData()}
                  defaultExpandAll
                  style={{ background: 'transparent', color: '#e6f1ff' }}
                />
              </div>
            </div>

            <Divider style={{ borderColor: '#1f2940', margin: '12px 0' }} />

            <div>
              <div style={{ fontSize: 14, fontWeight: 500, marginBottom: 8 }}>
                岗位分布地图
              </div>
              <div
                ref={drawerMapRef}
                style={{
                  width: '100%',
                  height: 250,
                  border: '1px solid #1f2940',
                  borderRadius: 8
                }}
              />
            </div>

            <Divider style={{ borderColor: '#1f2940', margin: '16px 0' }} />

            <Space wrap>
              {detailPlan.status === 0 && (
                <Button
                  type="primary"
                  icon={<EditOutlined />}
                  onClick={() => {
                    setDrawerVisible(false);
                    handleEdit(detailPlan);
                  }}
                >
                  编辑方案
                </Button>
              )}
              {detailPlan.status === 0 && (
                <Button
                  icon={<SendOutlined />}
                  onClick={() => {
                    handlePublish(detailPlan.id);
                    setDrawerVisible(false);
                  }}
                >
                  发布
                </Button>
              )}
              {detailPlan.status === 1 && (
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  onClick={() => {
                    handleExecute(detailPlan.id);
                    setDrawerVisible(false);
                  }}
                >
                  开始执行
                </Button>
              )}
              {(detailPlan.status === 1 || detailPlan.status === 2) && (
                <Button
                  icon={<FolderOutlined />}
                  onClick={() => {
                    handleArchive(detailPlan.id);
                    setDrawerVisible(false);
                  }}
                >
                  归档
                </Button>
              )}
            </Space>
          </div>
        )}
      </Drawer>
    </div>
  );
};

export default SecurityPlanManager;
