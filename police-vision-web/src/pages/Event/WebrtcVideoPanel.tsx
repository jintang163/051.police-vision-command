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
  Avatar,
  Modal,
  Form,
  Input,
  InputNumber,
  Select,
  Switch,
  message,
  Tooltip,
  Divider,
  Statistic,
  Dropdown,
  Badge,
  Popconfirm,
  Spin
} from 'antd';
import {
  VideoCameraOutlined,
  AudioOutlined,
  AudioMutedOutlined,
  VideoMutedOutlined,
  UserOutlined,
  PhoneOutlined,
  DisconnectOutlined,
  ShareAltOutlined,
  SettingOutlined,
  PlusOutlined,
  CrownOutlined,
  HandOutlined,
  ReloadOutlined,
  InfoCircleOutlined,
  TeamOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  WebrtcRoomJoinResult,
  WebrtcParticipant,
  WebrtcActiveRoom,
  WebrtcRoomJoinDTO
} from '@/types';
import {
  joinWebrtcRoom,
  leaveWebrtcRoom,
  getWebrtcRoomInfo,
  getActiveWebrtcRooms,
  updateWebrtcParticipantStatus,
  sendWebrtcSignal
} from '@/services/eventApi';

interface WebrtcVideoPanelProps {
  eventId: string;
  eventName?: string;
  defaultRoomId?: string;
}

const { Option } = Select;

const WebrtcVideoPanel: React.FC<WebrtcVideoPanelProps> = ({
  eventId,
  eventName,
  defaultRoomId
}) => {
  const [activeRooms, setActiveRooms] = useState<WebrtcActiveRoom[]>([]);
  const [currentRoom, setCurrentRoom] = useState<WebrtcRoomJoinResult | null>(null);
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [joinLoading, setJoinLoading] = useState(false);
  const [roomsLoading, setRoomsLoading] = useState(false);
  const [roomInfoLoading, setRoomInfoLoading] = useState(false);
  const [createForm] = Form.useForm();
  const [localStream, setLocalStream] = useState<MediaStream | null>(null);
  const videoRefs = useRef<Record<string, HTMLVideoElement | null>>({});

  const currentUserId = String(Math.floor(Math.random() * 9000) + 1000);
  const currentUserName = `指挥中心-${currentUserId}`;

  useEffect(() => {
    fetchActiveRooms();
    return () => {
      if (localStream) {
        localStream.getTracks().forEach((t) => t.stop());
      }
    };
  }, []);

  useEffect(() => {
    if (defaultRoomId && !currentRoom) {
      handleJoinRoom(defaultRoomId);
    }
  }, [defaultRoomId]);

  const fetchActiveRooms = async () => {
    setRoomsLoading(true);
    try {
      const res = await getActiveWebrtcRooms();
      setActiveRooms(res.data || []);
    } catch (error: any) {
      console.error('获取活跃房间失败:', error.message);
    } finally {
      setRoomsLoading(false);
    }
  };

  const fetchRoomInfo = async (roomId: string) => {
    setRoomInfoLoading(true);
    try {
      const res = await getWebrtcRoomInfo(roomId);
      setCurrentRoom(res.data);
    } catch (error: any) {
      message.error('获取房间信息失败');
    } finally {
      setRoomInfoLoading(false);
    }
  };

  const startLocalVideo = async (enableVideo = true, enableAudio = true) => {
    try {
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        message.warning('当前浏览器不支持摄像头访问，已进入旁听模式');
        return null;
      }
      const stream = await navigator.mediaDevices.getUserMedia({
        video: enableVideo,
        audio: enableAudio
      });
      setLocalStream(stream);
      if (videoRefs.current['local']) {
        videoRefs.current['local'].srcObject = stream;
      }
      return stream;
    } catch (error: any) {
      message.warning(`摄像头/麦克风访问失败：${error.message || '权限被拒绝'}，将使用旁听模式`);
      return null;
    }
  };

  const handleJoinRoom = async (roomId: string) => {
    setJoinLoading(true);
    try {
      const dto: WebrtcRoomJoinDTO = {
        roomId,
        roomName: `${eventName || '突发事件'}视频会商`,
        eventId,
        userId: Number(currentUserId),
        userName: currentUserName,
        enableAudio: true,
        enableVideo: true
      };
      const res = await joinWebrtcRoom(dto);
      setCurrentRoom(res.data);
      await startLocalVideo(true, true);
      message.success(`已加入视频会议室：${res.data.roomName}`);
    } catch (error: any) {
      message.error(error.message || '加入会议失败');
    } finally {
      setJoinLoading(false);
    }
  };

  const handleCreateRoom = async () => {
    try {
      const values = await createForm.validateFields();
      const roomId = values.roomId || `ROOM_${dayjs().format('YYYYMMDDHHmmss')}_${Math.floor(Math.random() * 1000)}`;
      setJoinLoading(true);
      const dto: WebrtcRoomJoinDTO = {
        roomId,
        roomName: values.roomName,
        eventId,
        userId: Number(currentUserId),
        userName: currentUserName,
        enableAudio: true,
        enableVideo: true
      };
      const res = await joinWebrtcRoom(dto);
      setCurrentRoom(res.data);
      setCreateModalVisible(false);
      createForm.resetFields();
      await startLocalVideo(true, true);
      message.success('视频会商室已创建并加入');
      fetchActiveRooms();
    } catch (error: any) {
      if (error.errorFields) return;
      message.error(error.message || '创建会议室失败');
    } finally {
      setJoinLoading(false);
    }
  };

  const handleLeaveRoom = async () => {
    if (!currentRoom) return;
    try {
      await leaveWebrtcRoom(currentRoom.roomId, currentUserId, currentUserName);
      if (localStream) {
        localStream.getTracks().forEach((t) => t.stop());
        setLocalStream(null);
      }
      setCurrentRoom(null);
      message.success('已离开视频会议室');
      fetchActiveRooms();
    } catch (error: any) {
      message.error(error.message || '离开会议失败');
    }
  };

  const toggleMedia = async (type: 'audio' | 'video', enabled: boolean) => {
    if (!currentRoom) return;
    try {
      await updateWebrtcParticipantStatus({
        roomId: currentRoom.roomId,
        userId: currentUserId,
        enableAudio: type === 'audio' ? enabled : undefined,
        enableVideo: type === 'video' ? enabled : undefined
      });
      if (type === 'video' && localStream) {
        localStream.getVideoTracks().forEach((t) => { t.enabled = enabled; });
      }
      if (type === 'audio' && localStream) {
        localStream.getAudioTracks().forEach((t) => { t.enabled = enabled; });
      }
      fetchRoomInfo(currentRoom.roomId);
      message.success(`${type === 'audio' ? '麦克风' : '摄像头'}已${enabled ? '开启' : '关闭'}`);
    } catch (error: any) {
      message.error(error.message || '操作失败');
    }
  };

  const toggleHandRaise = async (raised: boolean) => {
    if (!currentRoom) return;
    try {
      await updateWebrtcParticipantStatus({
        roomId: currentRoom.roomId,
        userId: currentUserId,
        isHandRaised: raised
      });
      fetchRoomInfo(currentRoom.roomId);
      message.success(raised ? '已举手，等待主持人批准发言' : '已放下手');
    } catch (error: any) {
      message.error(error.message || '操作失败');
    }
  };

  const getParticipant = (userId: string): WebrtcParticipant | null => {
    if (!currentRoom?.participants) return null;
    const p = currentRoom.participants[userId];
    return p || null;
  };

  const renderVideoGrid = () => {
    if (!currentRoom) return null;
    const participantList = Object.entries(currentRoom.participants || {});
    const gridCols = Math.min(4, Math.max(1, Math.ceil(Math.sqrt(participantList.length + 1))));
    const colSpan = 24 / gridCols;

    const VideoTile = ({
      participant,
      isLocal = false,
      refKey
    }: { participant?: WebrtcParticipant | null; isLocal?: boolean; refKey: string }) => {
      const videoEnabled = isLocal ? !!localStream : (participant?.enableVideo ?? true);
      const audioEnabled = isLocal ? !!localStream : (participant?.enableAudio ?? true);
      const name = isLocal ? currentUserName : participant?.userName || '未知用户';
      const isHost = participant?.role === 'host';
      const handRaised = participant?.isHandRaised;

      return (
        <Col span={colSpan} style={{ padding: 4 }}>
          <div
            style={{
              position: 'relative',
              width: '100%',
              paddingTop: '56.25%',
              background: '#0a0d1a',
              borderRadius: 8,
              overflow: 'hidden',
              border: isLocal ? '2px solid #1890ff' : '2px solid #1f2940'
            }}
          >
            {videoEnabled ? (
              <video
                ref={(el) => { videoRefs.current[refKey] = el; }}
                autoPlay
                muted={isLocal}
                playsInline
                style={{
                  position: 'absolute', top: 0, left: 0,
                  width: '100%', height: '100%', objectFit: 'cover'
                }}
              />
            ) : (
              <div
                style={{
                  position: 'absolute', top: 0, left: 0,
                  width: '100%', height: '100%',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  background: 'linear-gradient(135deg, #1f2940, #2a3560)'
                }}
              >
                <Avatar
                  size={80}
                  style={{ background: 'rgba(24, 144, 255, 0.4)', fontSize: 32 }}
                  icon={<UserOutlined />}
                >
                  {name?.charAt(0)}
                </Avatar>
              </div>
            )}

            <div
              style={{
                position: 'absolute', top: 8, left: 8,
                background: 'rgba(0,0,0,0.7)', padding: '4px 10px',
                borderRadius: 4, fontSize: 12, color: '#fff',
                display: 'flex', alignItems: 'center', gap: 6
              }}
            >
              {isHost && <CrownOutlined style={{ color: '#faad14' }} />}
              {handRaised && <HandOutlined style={{ color: '#13c2c2' }} />}
              <span style={{ maxWidth: 120, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {name}{isLocal && ' (我)'}
              </span>
            </div>

            <div
              style={{
                position: 'absolute', top: 8, right: 8,
                display: 'flex', gap: 4
              }}
            >
              {!audioEnabled && (
                <Tag color="default" style={{ margin: 0, padding: '0 6px' }}>
                  <AudioMutedOutlined />
                </Tag>
              )}
              {!videoEnabled && (
                <Tag color="default" style={{ margin: 0, padding: '0 6px' }}>
                  <VideoMutedOutlined />
                </Tag>
              )}
            </div>
          </div>
        </Col>
      );
    };

    return (
      <Row style={{ margin: '0 -4px' }}>
        <VideoTile isLocal refKey="local" />
        {participantList.map(([uid, p]) =>
          String(uid) !== String(currentUserId) && (
            <VideoTile key={uid} participant={p} refKey={uid} />
          )
        )}
      </Row>
    );
  };

  const myParticipant = getParticipant(currentUserId);

  return (
    <div style={{ padding: '0 16px 16px' }}>
      {!currentRoom ? (
        <div>
          <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Space>
              <span style={{ color: '#e6f1ff', fontSize: 13 }}>
                <VideoCameraOutlined style={{ color: '#1890ff' }} /> 当前活跃会商室
              </span>
              <Tag color="blue" style={{ margin: 0 }}>{activeRooms.length}</Tag>
            </Space>
            <Space>
              <Button size="small" icon={<ReloadOutlined />} onClick={fetchActiveRooms} loading={roomsLoading}>
                刷新
              </Button>
              <Button
                type="primary"
                size="small"
                icon={<PlusOutlined />}
                onClick={() => setCreateModalVisible(true)}
                style={{ background: '#1890ff', borderColor: '#1890ff' }}
              >
                创建会商室
              </Button>
            </Space>
          </div>

          <Spin spinning={roomsLoading}>
            {activeRooms.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  <div>
                    <div style={{ color: '#8c9cb8', marginBottom: 12 }}>暂无视频会商室</div>
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalVisible(true)}>
                      创建首个会商室
                    </Button>
                  </div>
                }
                style={{ padding: 40 }}
              />
            ) : (
              <List
                grid={{ gutter: 12, xs: 1, sm: 2 }}
                dataSource={activeRooms}
                renderItem={(room) => (
                  <List.Item>
                    <Card
                      size="small"
                      bordered={false}
                      hoverable
                      styles={{ body: { padding: 14, background: 'rgba(26, 31, 53, 0.7)' } }}
                      title={
                        <Space size={8}>
                          <VideoCameraOutlined style={{ color: '#1890ff' }} />
                          <span style={{ color: '#e6f1ff', fontSize: 13, fontWeight: 500 }}>{room.roomName}</span>
                        </Space>
                      }
                      extra={
                        <Badge color="#52c41a" text={<span style={{ fontSize: 11, color: '#52c41a' }}>进行中</span>} />
                      }
                    >
                      <Row gutter={12}>
                        <Col span={12}>
                          <Statistic
                            title={<span style={{ fontSize: 11, color: '#8c9cb8' }}>参会人员</span>}
                            value={room.participantCount}
                            valueStyle={{ color: '#1890ff', fontSize: 20, fontWeight: 600 }}
                            prefix={<TeamOutlined />}
                          />
                        </Col>
                        <Col span={12}>
                          <div style={{ fontSize: 11, color: '#8c9cb8', marginBottom: 4 }}>时长</div>
                          <div style={{ color: '#faad14', fontSize: 16, fontWeight: 500 }}>
                            <InfoCircleOutlined /> 实时中
                          </div>
                        </Col>
                      </Row>
                      <Divider style={{ margin: '10px 0', borderColor: '#1f2940' }} />
                      <div style={{ fontSize: 11, color: '#8c9cb8', marginBottom: 8 }}>
                        房间ID：{room.roomId}
                      </div>
                      <Button
                        type="primary"
                        block
                        size="small"
                        icon={<PhoneOutlined />}
                        loading={joinLoading}
                        onClick={() => handleJoinRoom(room.roomId)}
                        style={{ background: '#52c41a', borderColor: '#52c41a' }}
                      >
                        立即加入
                      </Button>
                    </Card>
                  </List.Item>
                )}
              />
            )}
          </Spin>
        </div>
      ) : (
        <Spin spinning={roomInfoLoading}>
          <Card
            size="small"
            bordered={false}
            style={{ marginBottom: 12 }}
            styles={{ body: { padding: 12, background: 'rgba(26, 31, 53, 0.5)' } }}
            title={
              <Space>
                <Badge color="#52c41a" />
                <VideoCameraOutlined style={{ color: '#1890ff' }} />
                <span style={{ color: '#e6f1ff', fontSize: 13, fontWeight: 500 }}>{currentRoom.roomName}</span>
                <Tag color="geekblue" style={{ margin: 0 }}>ID: {currentRoom.roomId}</Tag>
              </Space>
            }
            extra={
              <Space>
                <Statistic
                  title={<span style={{ fontSize: 11, color: '#8c9cb8' }}>参会</span>}
                  value={Object.keys(currentRoom.participants || {}).length}
                  valueStyle={{ color: '#1890ff', fontSize: 14, fontWeight: 600 }}
                  prefix={<UserOutlined />}
                  style={{ margin: 0, padding: 0 }}
                />
                <Tooltip title="房间信息刷新">
                  <Button size="small" icon={<ReloadOutlined />} onClick={() => fetchRoomInfo(currentRoom.roomId)} />
                </Tooltip>
                <Popconfirm
                  title="确认离开当前视频会商室？"
                  onConfirm={handleLeaveRoom}
                  okText="确认离开"
                  cancelText="取消"
                >
                  <Button danger size="small" icon={<DisconnectOutlined />}>
                    离开
                  </Button>
                </Popconfirm>
              </Space>
            }
          >
            {renderVideoGrid()}
          </Card>

          <Card
            size="small"
            bordered={false}
            styles={{ body: { padding: 12, background: 'rgba(26, 31, 53, 0.5)' } }}
            title={
              <Space>
                <TeamOutlined style={{ color: '#1890ff' }} />
                <span style={{ color: '#e6f1ff', fontSize: 13 }}>会场控制</span>
              </Space>
            }
          >
            <Space size={12} wrap>
              <Tooltip title={myParticipant?.enableAudio ? '关闭麦克风' : '开启麦克风'}>
                <Button
                  type={myParticipant?.enableAudio ? 'primary' : 'default'}
                  danger={!myParticipant?.enableAudio}
                  icon={myParticipant?.enableAudio ? <AudioOutlined /> : <AudioMutedOutlined />}
                  onClick={() => toggleMedia('audio', !myParticipant?.enableAudio)}
                >
                  {myParticipant?.enableAudio ? '麦克风：开' : '麦克风：关'}
                </Button>
              </Tooltip>

              <Tooltip title={myParticipant?.enableVideo ? '关闭摄像头' : '开启摄像头'}>
                <Button
                  type={myParticipant?.enableVideo ? 'primary' : 'default'}
                  danger={!myParticipant?.enableVideo}
                  icon={myParticipant?.enableVideo ? <VideoCameraOutlined /> : <VideoMutedOutlined />}
                  onClick={() => toggleMedia('video', !myParticipant?.enableVideo)}
                >
                  {myParticipant?.enableVideo ? '摄像头：开' : '摄像头：关'}
                </Button>
              </Tooltip>

              <Tooltip title={myParticipant?.isHandRaised ? '放下手' : '举手发言'}>
                <Button
                  type={myParticipant?.isHandRaised ? 'primary' : 'default'}
                  icon={<HandOutlined />}
                  onClick={() => toggleHandRaise(!myParticipant?.isHandRaised)}
                  style={myParticipant?.isHandRaised ? { background: '#13c2c2', borderColor: '#13c2c2' } : {}}
                >
                  {myParticipant?.isHandRaised ? '已举手' : '举手'}
                </Button>
              </Tooltip>

              <Dropdown
                menu={{
                  items: [
                    {
                      key: 'screen',
                      icon: <ShareAltOutlined />,
                      label: '共享屏幕（模拟）',
                      onClick: () => message.info('屏幕共享功能需要用户授权，演示模式下模拟触发')
                    },
                    {
                      key: 'record',
                      icon: <SettingOutlined />,
                      label: '会议录制（模拟）',
                      onClick: () => message.success('已开始录制会议内容（模拟模式）')
                    }
                  ]
                }}
              >
                <Button icon={<SettingOutlined />}>更多</Button>
              </Dropdown>
            </Space>
          </Card>
        </Spin>
      )}

      <Modal
        title={
          <Space>
            <PlusOutlined style={{ color: '#1890ff' }} />
            <span>创建视频会商室</span>
          </Space>
        }
        open={createModalVisible}
        onOk={handleCreateRoom}
        onCancel={() => setCreateModalVisible(false)}
        okText="创建并加入"
        cancelText="取消"
        confirmLoading={joinLoading}
        width={520}
        styles={{
          header: { background: '#1a1f35', color: '#e6f1ff', borderBottom: '1px solid #1f2940' },
          body: { background: '#141829' },
          content: { background: '#141829', border: '1px solid rgba(24, 144, 255, 0.3)' },
          footer: { background: '#1a1f35', borderTop: '1px solid #1f2940' }
        }}
      >
        <Form
          form={createForm}
          layout="vertical"
          initialValues={{
            roomName: `${eventName || '突发事件'}视频会商-${dayjs().format('HH:mm')}`,
            maxParticipants: 16,
            passwordRequired: false
          }}
          style={{ color: '#e6f1ff' }}
        >
          <Form.Item label="会商室名称" name="roomName" rules={[{ required: true, message: '请输入会商室名称' }]}>
            <Input placeholder="如：XX案件指挥部会商" maxLength={50} />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item label="房间ID（可选）" name="roomId">
                <Input placeholder="留空自动生成" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="最大参会人数" name="maxParticipants">
                <InputNumber min={2} max={32} step={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="passwordRequired" valuePropName="checked" label="入会密码">
                <Switch checkedChildren="启用" unCheckedChildren="关闭" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="关联事件" style={{ fontSize: 11, color: '#8c9cb8' }}>
                <div style={{ padding: '8px 12px', background: 'rgba(26, 31, 53, 0.5)', borderRadius: 4, fontSize: 12 }}>
                  {eventId} · {eventName || '当前事件'}
                </div>
              </Form.Item>
            </Col>
          </Row>
          <Card size="small" bordered={false}
            styles={{ body: { padding: 8, background: 'rgba(82, 196, 26, 0.08)', fontSize: 11, color: '#52c41a' } }}>
            <InfoCircleOutlined /> 信令通过 RocketMQ 广播转发，适合16人以下小规模会议
          </Card>
        </Form>
      </Modal>
    </div>
  );
};

export default WebrtcVideoPanel;
