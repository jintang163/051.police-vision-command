import React, { useEffect, useRef, useState } from 'react';
import { Drawer, Button, Space, Tag, message } from 'antd';
import {
  SketchOutlined,
  ClearOutlined,
  CheckOutlined,
  CloseOutlined
} from '@ant-design/icons';
import { Position } from '@/types';

declare global {
  interface Window {
    TMap: any;
  }
}

interface EventMapDrawerProps {
  open: boolean;
  onClose: () => void;
  onConfirm: (points: Position[]) => void;
  initialPoints?: Position[];
}

const EventMapDrawer: React.FC<EventMapDrawerProps> = ({
  open,
  onClose,
  onConfirm,
  initialPoints = []
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const polygonEditorRef = useRef<any>(null);
  const polygonRef = useRef<any>(null);
  const [vertexCount, setVertexCount] = useState(0);
  const [area, setArea] = useState(0);
  const [isDrawing, setIsDrawing] = useState(false);

  useEffect(() => {
    if (!open) return;

    const initMap = () => {
      if (!window.TMap) {
        setTimeout(initMap, 500);
        return;
      }

      if (mapRef.current) {
        return;
      }

      const center = new window.TMap.LatLng(39.9042, 116.4074);

      mapRef.current = new window.TMap.Map(mapContainerRef.current!, {
        center,
        zoom: 12,
        pitch: 0,
        rotation: 0,
        viewMode: '2D',
        mapStyleId: 'style1',
        baseMap: {
          type: 'vector',
          features: ['base', 'road', 'point']
        }
      });

      initPolygonEditor();
    };

    const initPolygonEditor = () => {
      if (!window.TMap || !mapRef.current) return;

      polygonEditorRef.current = new window.TMap.tools.PolygonEditor({
        map: mapRef.current,
        strokeColor: '#1890ff',
        strokeWeight: 3,
        strokeOpacity: 0.9,
        fillColor: '#1890ff',
        fillOpacity: 0.2
      });

      polygonEditorRef.current.on('draw_complete', (event: any) => {
        setIsDrawing(false);
        updatePolygonInfo(event.polygon);
      });

      polygonEditorRef.current.on('adjust', (event: any) => {
        updatePolygonInfo(event.polygon);
      });

      if (initialPoints.length > 0) {
        const path = initialPoints.map(
          (p) => new window.TMap.LatLng(p.lat, p.lng)
        );
        polygonEditorRef.current.setPath(path);
        updatePolygonInfo(polygonEditorRef.current.getPolygon());
      }
    };

    initMap();

    return () => {
      if (polygonEditorRef.current) {
        polygonEditorRef.current.destroy();
        polygonEditorRef.current = null;
      }
      if (mapRef.current) {
        mapRef.current.destroy();
        mapRef.current = null;
      }
    };
  }, [open, initialPoints]);

  const updatePolygonInfo = (polygon: any) => {
    if (!polygon) {
      setVertexCount(0);
      setArea(0);
      return;
    }

    const path = polygon.getPath();
    setVertexCount(path.length);

    if (path.length >= 3) {
      const areaValue = calculatePolygonArea(path);
      setArea(areaValue);
    } else {
      setArea(0);
    }
  };

  const calculatePolygonArea = (path: any[]): number => {
    if (path.length < 3) return 0;

    let area = 0;
    const R = 6378137;

    for (let i = 0; i < path.length; i++) {
      const p1 = path[i];
      const p2 = path[(i + 1) % path.length];

      const lat1 = (p1.lat * Math.PI) / 180;
      const lat2 = (p2.lat * Math.PI) / 180;
      const lng1 = (p1.lng * Math.PI) / 180;
      const lng2 = (p2.lng * Math.PI) / 180;

      area +=
        (lng2 - lng1) *
        (2 + Math.sin(lat1) + Math.sin(lat2)) *
        R *
        R *
        0.5;
    }

    return Math.abs(area);
  };

  const handleDraw = () => {
    if (!polygonEditorRef.current) return;

    polygonEditorRef.current.startDraw();
    setIsDrawing(true);
    message.info('请在地图上点击添加顶点，双击结束绘制');
  };

  const handleClear = () => {
    if (!polygonEditorRef.current) return;

    polygonEditorRef.current.clear();
    setVertexCount(0);
    setArea(0);
    setIsDrawing(false);
  };

  const handleConfirm = () => {
    if (!polygonEditorRef.current) {
      message.warning('地图未初始化完成');
      return;
    }

    const polygon = polygonEditorRef.current.getPolygon();
    if (!polygon) {
      message.warning('请先绘制多边形区域');
      return;
    }

    const path = polygon.getPath();
    if (path.length < 3) {
      message.warning('多边形至少需要3个顶点');
      return;
    }

    const points: Position[] = path.map((p: any) => ({
      lat: p.lat,
      lng: p.lng
    }));

    onConfirm(points);
    onClose();
  };

  const handleCancel = () => {
    onClose();
  };

  const formatArea = (areaValue: number): string => {
    if (areaValue < 1000000) {
      return `${areaValue.toFixed(2)} ㎡`;
    }
    return `${(areaValue / 1000000).toFixed(2)} ㎢`;
  };

  return (
    <Drawer
      title="地图圈选"
      placement="right"
      width={600}
      open={open}
      onClose={onClose}
      bodyStyle={{ padding: 0, height: 'calc(100% - 55px)' }}
      styles={{
        header: {
          background: 'linear-gradient(90deg, #1a1f35 0%, #141829 100%)',
          color: '#e6f1ff',
          borderBottom: '1px solid #1f2940'
        },
        content: {
          background: '#0a0e1a'
        }
      }}
      extra={
        <Space>
          <Tag color="blue">顶点: {vertexCount}</Tag>
          <Tag color="green">面积: {formatArea(area)}</Tag>
        </Space>
      }
    >
      <div style={{ position: 'relative', width: '100%', height: '100%' }}>
        <div
          ref={mapContainerRef}
          style={{ width: '100%', height: '100%' }}
        />

        <div
          style={{
            position: 'absolute',
            top: 16,
            left: 16,
            background: 'rgba(20, 24, 41, 0.95)',
            border: '1px solid rgba(24, 144, 255, 0.3)',
            borderRadius: 8,
            padding: 12,
            backdropFilter: 'blur(8px)'
          }}
        >
          <Space direction="vertical" size={12}>
            <Space>
              <Button
                type={isDrawing ? 'primary' : 'default'}
                icon={<SketchOutlined />}
                onClick={handleDraw}
                disabled={isDrawing}
                style={{
                  background: isDrawing
                    ? 'linear-gradient(135deg, #1890ff 0%, #00d4ff 100%)'
                    : 'rgba(24, 144, 255, 0.1)',
                  borderColor: 'rgba(24, 144, 255, 0.5)',
                  color: '#e6f1ff'
                }}
              >
                绘制多边形
              </Button>
              <Button
                icon={<ClearOutlined />}
                onClick={handleClear}
                style={{
                  background: 'rgba(255, 77, 79, 0.1)',
                  borderColor: 'rgba(255, 77, 79, 0.3)',
                  color: '#ff7875'
                }}
              >
                清空
              </Button>
            </Space>
            <div style={{ color: '#8c9cb8', fontSize: 12 }}>
              提示：点击地图添加顶点，双击结束绘制
            </div>
          </Space>
        </div>

        <div
          style={{
            position: 'absolute',
            bottom: 20,
            left: '50%',
            transform: 'translateX(-50%)',
            display: 'flex',
            gap: 12
          }}
        >
          <Button
            size="large"
            icon={<CloseOutlined />}
            onClick={handleCancel}
            style={{
              background: 'rgba(20, 24, 41, 0.95)',
              borderColor: 'rgba(255, 77, 79, 0.3)',
              color: '#ff7875',
              minWidth: 100
            }}
          >
            取消
          </Button>
          <Button
            type="primary"
            size="large"
            icon={<CheckOutlined />}
            onClick={handleConfirm}
            style={{
              background: 'linear-gradient(135deg, #1890ff 0%, #00d4ff 100%)',
              border: 'none',
              minWidth: 100
            }}
          >
            确认
          </Button>
        </div>
      </div>
    </Drawer>
  );
};

export default EventMapDrawer;
