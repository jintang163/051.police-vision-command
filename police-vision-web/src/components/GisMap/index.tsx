import React, { useEffect, useRef, useState, useImperativeHandle, forwardRef } from 'react';
import { Button, Checkbox, Modal, Tag, Space } from 'antd';
import { PoliceForce, Alarm, Alert, Camera, HeatmapPoint, MapLayer } from '@/types';
import dayjs from 'dayjs';

declare global {
  interface Window {
    TMap: any;
  }
}

interface GisMapProps {
  policeForces: PoliceForce[];
  alarms: Alarm[];
  alerts: Alert[];
  cameras: Camera[];
  heatmapData: HeatmapPoint[];
  onAlarmClick?: (alarm: Alarm) => void;
  onPoliceClick?: (police: PoliceForce) => void;
  onCameraClick?: (camera: Camera) => void;
}

export interface GisMapRef {
  locateToPosition: (lat: number, lng: number, zoom?: number) => void;
  showAlarmPopup: (alarm: Alarm) => void;
}

const defaultLayers: MapLayer[] = [
  { id: 'police', name: '警力部署', visible: true },
  { id: 'alarm', name: '警情点位', visible: true },
  { id: 'alert', name: '告警点位', visible: true },
  { id: 'camera', name: '视频监控', visible: true },
  { id: 'heatmap', name: '热力图', visible: false },
];

const GisMap = forwardRef<GisMapRef, GisMapProps>(({
  policeForces,
  alarms,
  alerts,
  cameras,
  heatmapData,
  onAlarmClick,
  onPoliceClick,
  onCameraClick
}, ref) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const markersRef = useRef<Map<string, any>>(new Map());
  const infoWindowRef = useRef<any>(null);
  const heatmapRef = useRef<any>(null);
  const [layers, setLayers] = useState<MapLayer[]>(defaultLayers);
  const [selectedAlarm, setSelectedAlarm] = useState<Alarm | null>(null);
  const [popupVisible, setPopupVisible] = useState(false);

  useImperativeHandle(ref, () => ({
    locateToPosition: (lat: number, lng: number, zoom = 15) => {
      if (mapRef.current) {
        mapRef.current.easeTo({
          center: new window.TMap.LatLng(lat, lng),
          zoom,
          duration: 1000
        });
      }
    },
    showAlarmPopup: (alarm: Alarm) => {
      setSelectedAlarm(alarm);
      setPopupVisible(true);
      if (mapRef.current && infoWindowRef.current) {
        const position = new window.TMap.LatLng(alarm.position.lat, alarm.position.lng);
        infoWindowRef.current.setPosition(position);
        infoWindowRef.current.setContent(`
          <div style="padding: 12px; min-width: 240px;">
            <h4 style="margin: 0 0 8px 0; color: #1890ff; font-size: 14px;">${alarm.title}</h4>
            <p style="margin: 4px 0; font-size: 12px; color: #8c9cb8;">
              <span style="color: #e6f1ff;">类型：</span>${alarm.typeName}
            </p>
            <p style="margin: 4px 0; font-size: 12px; color: #8c9cb8;">
              <span style="color: #e6f1ff;">级别：</span>
              <span style="color: ${alarm.level === 'high' ? '#ff4d4f' : alarm.level === 'medium' ? '#faad14' : '#52c41a'};">
                ${alarm.levelName}
              </span>
            </p>
            <p style="margin: 4px 0; font-size: 12px; color: #8c9cb8;">
              <span style="color: #e6f1ff;">地址：</span>${alarm.address}
            </p>
            <p style="margin: 4px 0; font-size: 12px; color: #8c9cb8;">
              <span style="color: #e6f1ff;">时间：</span>${dayjs(alarm.reportTime).format('YYYY-MM-DD HH:mm:ss')}
            </p>
          </div>
        `);
        infoWindowRef.current.open();
      }
    }
  }));

  useEffect(() => {
    const initMap = () => {
      if (!window.TMap) {
        setTimeout(initMap, 1000);
        return;
      }

      const center = new window.TMap.LatLng(39.9042, 116.4074);

      mapRef.current = new window.TMap.Map(mapContainerRef.current!, {
        center,
        zoom: 12,
        pitch: 0,
        rotation: 0,
        viewMode: '3D',
        mapStyleId: 'style1',
        baseMap: {
          type: 'vector',
          features: ['base', 'road', 'point']
        }
      });

      infoWindowRef.current = new window.TMap.InfoWindow({
        map: mapRef.current,
        position: center,
        offset: { x: 0, y: -40 }
      });
      infoWindowRef.current.close();

      createHeatmap();
    };

    initMap();

    return () => {
      if (mapRef.current) {
        mapRef.current.destroy();
      }
    };
  }, []);

  const createHeatmap = () => {
    if (!window.TMap || !mapRef.current) return;

    heatmapRef.current = new window.TMap.visualization.Heatmap({
      map: mapRef.current,
      radius: 50,
      max: 10,
      gradient: {
        0.2: '#00d4ff',
        0.4: '#1890ff',
        0.6: '#faad14',
        0.8: '#ff7a00',
        1.0: '#ff4d4f'
      },
      opacity: 0.6,
      zIndex: 100
    });
  };

  useEffect(() => {
    if (!heatmapRef.current || !heatmapData.length) return;

    const heatLayer = layers.find(l => l.id === 'heatmap');
    if (heatLayer?.visible) {
      const data = heatmapData.map(p => ({
        lat: p.lat,
        lng: p.lng,
        count: p.count
      }));
      heatmapRef.current.setData(data);
    } else {
      heatmapRef.current.setData([]);
    }
  }, [heatmapData, layers]);

  useEffect(() => {
    if (!window.TMap || !mapRef.current) return;

    clearMarkersByType('police');

    const policeLayer = layers.find(l => l.id === 'police');
    if (!policeLayer?.visible || !policeForces.length) return;

    const policeMarkers = policeForces.map(police => ({
      id: `police_${police.id}`,
      position: new window.TMap.LatLng(police.position.lat, police.position.lng),
      properties: police,
      styles: 'police'
    }));

    const markerLayer = new window.TMap.MultiMarker({
      map: mapRef.current,
      styles: {
        police: new window.TMap.MarkerStyle({
          width: 32,
          height: 32,
          anchor: { x: 16, y: 16 },
          src: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48Y2lyY2xlIGN4PSIxNiIgY3k9IjE2IiByPSIxNCIgZmlsbD0iIzE4OTBmZiIgc3Ryb2tlPSIjZmZmIiBzdHJva2Utd2lkdGg9IjIiLz48cGF0aCBmaWxsPSIjZmZmIiBkPSJNMTYgOGw0IDhoLTN2OGgtMnYtOGgtNHoiLz48L3N2Zz4='
        })
      },
      geometries: policeMarkers
    });

    markerLayer.on('click', (event: any) => {
      const police = event.geometry.properties;
      onPoliceClick?.(police);
    });

    markersRef.current.set('police', markerLayer);
  }, [policeForces, layers, onPoliceClick]);

  useEffect(() => {
    if (!window.TMap || !mapRef.current) return;

    clearMarkersByType('alarm');

    const alarmLayer = layers.find(l => l.id === 'alarm');
    if (!alarmLayer?.visible || !alarms.length) return;

    const alarmMarkers = alarms.map(alarm => ({
      id: `alarm_${alarm.id}`,
      position: new window.TMap.LatLng(alarm.position.lat, alarm.position.lng),
      properties: alarm,
      styles: `alarm_${alarm.level}`
    }));

    const getColor = (level: string) => {
      switch (level) {
        case 'high': return '#ff4d4f';
        case 'medium': return '#faad14';
        default: return '#52c41a';
      }
    };

    const markerLayer = new window.TMap.MultiMarker({
      map: mapRef.current,
      styles: {
        alarm_high: new window.TMap.MarkerStyle({
          width: 36,
          height: 36,
          anchor: { x: 18, y: 36 },
          src: `data:image/svg+xml;base64,${btoa(`<svg xmlns="http://www.w3.org/2000/svg" width="36" height="36" viewBox="0 0 36 36"><path d="M18 2L2 34h32L18 2z" fill="${getColor('high')}" stroke="#fff" stroke-width="2"/><text x="18" y="24" text-anchor="middle" fill="#fff" font-size="12" font-weight="bold">!</text></svg>`)}`
        }),
        alarm_medium: new window.TMap.MarkerStyle({
          width: 32,
          height: 32,
          anchor: { x: 16, y: 32 },
          src: `data:image/svg+xml;base64,${btoa(`<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32"><path d="M16 2L2 30h28L16 2z" fill="${getColor('medium')}" stroke="#fff" stroke-width="2"/><text x="16" y="22" text-anchor="middle" fill="#fff" font-size="11" font-weight="bold">!</text></svg>`)}`
        }),
        alarm_low: new window.TMap.MarkerStyle({
          width: 28,
          height: 28,
          anchor: { x: 14, y: 28 },
          src: `data:image/svg+xml;base64,${btoa(`<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 28 28"><path d="M14 2L2 26h24L14 2z" fill="${getColor('low')}" stroke="#fff" stroke-width="2"/><text x="14" y="19" text-anchor="middle" fill="#fff" font-size="10" font-weight="bold">!</text></svg>`)}`
        })
      },
      geometries: alarmMarkers
    });

    markerLayer.on('click', (event: any) => {
      const alarm = event.geometry.properties;
      onAlarmClick?.(alarm);
    });

    markersRef.current.set('alarm', markerLayer);
  }, [alarms, layers, onAlarmClick]);

  useEffect(() => {
    if (!window.TMap || !mapRef.current) return;

    clearMarkersByType('alert');

    const alertLayer = layers.find(l => l.id === 'alert');
    if (!alertLayer?.visible || !alerts.length) return;

    const alertMarkers = alerts.map(alert => ({
      id: `alert_${alert.id}`,
      position: new window.TMap.LatLng(alert.position.lat, alert.position.lng),
      properties: alert,
      styles: `alert_${alert.level}`
    }));

    const getColor = (level: string) => {
      switch (level) {
        case 'high': return '#ff4d4f';
        case 'medium': return '#faad14';
        default: return '#52c41a';
      }
    };

    const markerLayer = new window.TMap.MultiMarker({
      map: mapRef.current,
      styles: {
        alert_high: new window.TMap.MarkerStyle({
          width: 24,
          height: 24,
          anchor: { x: 12, y: 12 },
          src: `data:image/svg+xml;base64,${btoa(`<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" fill="${getColor('high')}" stroke="#fff" stroke-width="2"/><circle cx="12" cy="12" r="4" fill="#fff"/></svg>`)}`
        }),
        alert_medium: new window.TMap.MarkerStyle({
          width: 20,
          height: 20,
          anchor: { x: 10, y: 10 },
          src: `data:image/svg+xml;base64,${btoa(`<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 20 20"><circle cx="10" cy="10" r="8" fill="${getColor('medium')}" stroke="#fff" stroke-width="2"/><circle cx="10" cy="10" r="3" fill="#fff"/></svg>`)}`
        }),
        alert_low: new window.TMap.MarkerStyle({
          width: 16,
          height: 16,
          anchor: { x: 8, y: 8 },
          src: `data:image/svg+xml;base64,${btoa(`<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16"><circle cx="8" cy="8" r="6" fill="${getColor('low')}" stroke="#fff" stroke-width="2"/><circle cx="8" cy="8" r="2" fill="#fff"/></svg>`)}`
        })
      },
      geometries: alertMarkers
    });

    markersRef.current.set('alert', markerLayer);
  }, [alerts, layers]);

  useEffect(() => {
    if (!window.TMap || !mapRef.current) return;

    clearMarkersByType('camera');

    const cameraLayer = layers.find(l => l.id === 'camera');
    if (!cameraLayer?.visible || !cameras.length) return;

    const cameraMarkers = cameras.map(camera => ({
      id: `camera_${camera.id}`,
      position: new window.TMap.LatLng(camera.position.lat, camera.position.lng),
      properties: camera,
      styles: camera.status === 'online' ? 'camera_online' : 'camera_offline'
    }));

    const markerLayer = new window.TMap.MultiMarker({
      map: mapRef.current,
      styles: {
        camera_online: new window.TMap.MarkerStyle({
          width: 24,
          height: 24,
          anchor: { x: 12, y: 12 },
          src: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgdmlld0JveD0iMCAwIDI0IDI0Ij48cmVjdCB4PSIyIiB5PSI2IiB3aWR0aD0iMTQiIGhlaWdodD0iMTIiIHJ4PSIyIiBmaWxsPSIjNTJjNDFhIiBzdHJva2U9IiNmZmYiIHN0cm9rZS13aWR0aD0iMiIvPjxjaXJjbGUgY3g9IjE5IiBjeT0iMTIiIHI9IjMiIGZpbGw9IiM1MmM0MWEiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIyIi8+PHBhdGggZD0iTTE2IDEwbDQgMi00IDJ6IiBmaWxsPSIjZmZmIi8+PC9zdmc+'
        }),
        camera_offline: new window.TMap.MarkerStyle({
          width: 24,
          height: 24,
          anchor: { x: 12, y: 12 },
          src: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgdmlld0JveD0iMCAwIDI0IDI0Ij48cmVjdCB4PSIyIiB5PSI2IiB3aWR0aD0iMTQiIGhlaWdodD0iMTIiIHJ4PSIyIiBmaWxsPSIjOGM5Y2I4IiBzdHJva2U9IiNmZmYiIHN0cm9rZS13aWR0aD0iMiIvPjxjaXJjbGUgY3g9IjE5IiBjeT0iMTIiIHI9IjMiIGZpbGw9IiM4YzljYjgiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIyIi8+PHBhdGggZD0iTTE2IDEwbDQgMi00IDJ6IiBmaWxsPSIjZmZmIi8+PC9zdmc+'
        })
      },
      geometries: cameraMarkers
    });

    markerLayer.on('click', (event: any) => {
      const camera = event.geometry.properties;
      onCameraClick?.(camera);
    });

    markersRef.current.set('camera', markerLayer);
  }, [cameras, layers, onCameraClick]);

  const clearMarkersByType = (type: string) => {
    const marker = markersRef.current.get(type);
    if (marker) {
      marker.setMap(null);
      markersRef.current.delete(type);
    }
  };

  const handleLayerChange = (layerId: string, checked: boolean) => {
    setLayers(prev => prev.map(l =>
      l.id === layerId ? { ...l, visible: checked } : l
    ));
  };

  const handleAlarmModalOk = () => {
    setPopupVisible(false);
    setSelectedAlarm(null);
  };

  return (
    <div style={{ position: 'relative', width: '100%', height: '100%' }}>
      <div
        ref={mapContainerRef}
        style={{ width: '100%', height: '100%' }}
      />

      <div style={{
        position: 'absolute',
        top: 16,
        right: 16,
        background: 'rgba(20, 24, 41, 0.9)',
        border: '1px solid rgba(24, 144, 255, 0.3)',
        borderRadius: 8,
        padding: 12,
        backdropFilter: 'blur(8px)'
      }}>
        <div style={{ fontSize: 14, fontWeight: 600, color: '#e6f1ff', marginBottom: 8 }}>
          图层控制
        </div>
        <Space direction="vertical" size={8}>
          {layers.map(layer => (
            <Checkbox
              key={layer.id}
              checked={layer.visible}
              onChange={(e) => handleLayerChange(layer.id, e.target.checked)}
              style={{ color: '#8c9cb8' }}
            >
              {layer.name}
            </Checkbox>
          ))}
        </Space>
      </div>

      <div style={{
        position: 'absolute',
        bottom: 16,
        left: 16,
        display: 'flex',
        gap: 8
      }}>
        <Button
          size="small"
          onClick={() => mapRef.current?.zoomIn()}
          style={{ background: 'rgba(20, 24, 41, 0.9)', borderColor: 'rgba(24, 144, 255, 0.3)' }}
        >
          +
        </Button>
        <Button
          size="small"
          onClick={() => mapRef.current?.zoomOut()}
          style={{ background: 'rgba(20, 24, 41, 0.9)', borderColor: 'rgba(24, 144, 255, 0.3)' }}
        >
          -
        </Button>
      </div>

      <Modal
        title={selectedAlarm?.title}
        open={popupVisible}
        onOk={handleAlarmModalOk}
        onCancel={handleAlarmModalOk}
        width={480}
        styles={{
          header: { background: '#1a1f35', color: '#e6f1ff', borderBottom: '1px solid #1f2940' },
          body: { background: '#141829' },
          content: { background: '#141829', border: '1px solid rgba(24, 144, 255, 0.3)' }
        }}
      >
        {selectedAlarm && (
          <div style={{ color: '#8c9cb8' }}>
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <span>警情类型：</span>
                <Tag color="blue">{selectedAlarm.typeName}</Tag>
                <span>警情级别：</span>
                <Tag color={selectedAlarm.level === 'high' ? 'red' : selectedAlarm.level === 'medium' ? 'orange' : 'green'}>
                  {selectedAlarm.levelName}
                </Tag>
              </div>
              <div>
                <span>发生地点：</span>
                <span style={{ color: '#e6f1ff' }}>{selectedAlarm.address}</span>
              </div>
              <div>
                <span>报警时间：</span>
                <span style={{ color: '#e6f1ff' }}>
                  {dayjs(selectedAlarm.reportTime).format('YYYY-MM-DD HH:mm:ss')}
                </span>
              </div>
              <div>
                <span>报警人：</span>
                <span style={{ color: '#e6f1ff' }}>{selectedAlarm.reporter || '匿名'}</span>
              </div>
              <div>
                <span>联系电话：</span>
                <span style={{ color: '#e6f1ff' }}>{selectedAlarm.reporterPhone || '未提供'}</span>
              </div>
              <div>
                <span>情况描述：</span>
                <p style={{ color: '#e6f1ff', marginTop: 4 }}>{selectedAlarm.description}</p>
              </div>
            </Space>
          </div>
        )}
      </Modal>
    </div>
  );
});

GisMap.displayName = 'GisMap';

export default GisMap;
