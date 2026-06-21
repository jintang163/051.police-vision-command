import React, { useMemo } from 'react';
import { Card, Row, Col, Statistic } from 'antd';
import ReactECharts from 'echarts-for-react';
import { StatsData, ChartData } from '@/types';
import {
  WarningOutlined,
  BellOutlined,
  TeamOutlined,
  VideoCameraOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined
} from '@ant-design/icons';

interface StatsPanelProps {
  stats: StatsData;
  alarmTypeData: ChartData[];
  alertLevelData: ChartData[];
}

const StatsPanel: React.FC<StatsPanelProps> = ({ stats, alarmTypeData, alertLevelData }) => {
  const pieOption = useMemo(() => ({
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(20, 24, 41, 0.9)',
      borderColor: 'rgba(24, 144, 255, 0.3)',
      textStyle: {
        color: '#e6f1ff'
      }
    },
    legend: {
      orient: 'vertical',
      right: '5%',
      top: 'center',
      textStyle: {
        color: '#8c9cb8',
        fontSize: 12
      },
      itemWidth: 12,
      itemHeight: 12
    },
    color: ['#1890ff', '#ff4d4f', '#faad14', '#52c41a', '#722ed1'],
    series: [
      {
        name: '警情类型',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 4,
          borderColor: '#141829',
          borderWidth: 2
        },
        label: {
          show: false
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 14,
            fontWeight: 'bold',
            color: '#e6f1ff'
          },
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(24, 144, 255, 0.5)'
          }
        },
        labelLine: {
          show: false
        },
        data: alarmTypeData
      }
    ]
  }), [alarmTypeData]);

  const barOption = useMemo(() => ({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(20, 24, 41, 0.9)',
      borderColor: 'rgba(24, 144, 255, 0.3)',
      textStyle: {
        color: '#e6f1ff'
      },
      axisPointer: {
        type: 'shadow'
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: alertLevelData.map(item => item.name),
      axisLine: {
        lineStyle: {
          color: '#1f2940'
        }
      },
      axisLabel: {
        color: '#8c9cb8',
        fontSize: 12
      }
    },
    yAxis: {
      type: 'value',
      axisLine: {
        show: false
      },
      axisTick: {
        show: false
      },
      axisLabel: {
        color: '#8c9cb8',
        fontSize: 12
      },
      splitLine: {
        lineStyle: {
          color: '#1f2940',
          type: 'dashed'
        }
      }
    },
    series: [
      {
        name: '告警数量',
        type: 'bar',
        barWidth: '50%',
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: '#1890ff' },
              { offset: 1, color: '#00d4ff' }
            ]
          }
        },
        emphasis: {
          itemStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: '#00d4ff' },
                { offset: 1, color: '#1890ff' }
              ]
            }
          }
        },
        data: alertLevelData.map(item => item.value)
      }
    ]
  }), [alertLevelData]);

  const statCards = [
    {
      title: '今日警情',
      value: stats.totalAlarms,
      icon: <WarningOutlined style={{ color: '#ff4d4f' }} />,
      color: '#ff4d4f'
    },
    {
      title: '待处理',
      value: stats.pendingAlarms,
      icon: <ClockCircleOutlined style={{ color: '#faad14' }} />,
      color: '#faad14'
    },
    {
      title: '已处理',
      value: stats.resolvedAlarms,
      icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
      color: '#52c41a'
    },
    {
      title: 'AI告警',
      value: stats.totalAlerts,
      icon: <BellOutlined style={{ color: '#722ed1' }} />,
      color: '#722ed1'
    },
    {
      title: '警力总数',
      value: `${stats.onlinePoliceCount}/${stats.policeForceCount}`,
      icon: <TeamOutlined style={{ color: '#1890ff' }} />,
      color: '#1890ff'
    },
    {
      title: '监控在线',
      value: `${stats.onlineCameraCount}/${stats.cameraCount}`,
      icon: <VideoCameraOutlined style={{ color: '#13c2c2' }} />,
      color: '#13c2c2'
    }
  ];

  return (
    <div className="tech-card" style={{ height: '100%', padding: 16 }}>
      <div className="corner-decoration corner-tl" />
      <div className="corner-decoration corner-tr" />
      <div className="corner-decoration corner-bl" />
      <div className="corner-decoration corner-br" />

      <div className="panel-header">
        <span className="panel-title">实时统计</span>
      </div>

      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        {statCards.map((card, index) => (
          <Col span={8} key={index}>
            <Card
              size="small"
              styles={{
                body: { padding: '12px 8px', textAlign: 'center', background: 'rgba(26, 31, 53, 0.5)' }
              }}
              bordered={false}
            >
              <div style={{ fontSize: 20, marginBottom: 4 }}>{card.icon}</div>
              <Statistic
                title={<span style={{ fontSize: 11, color: '#8c9cb8' }}>{card.title}</span>}
                value={card.value}
                valueStyle={{ color: card.color, fontSize: 18, fontWeight: 600 }}
              />
            </Card>
          </Col>
        ))}
      </Row>

      <div className="panel-header" style={{ marginTop: 16 }}>
        <span className="panel-title">警情类型分布</span>
      </div>
      <div style={{ height: 180, marginBottom: 16 }}>
        <ReactECharts
          option={pieOption}
          style={{ height: '100%', width: '100%' }}
          opts={{ renderer: 'canvas' }}
        />
      </div>

      <div className="panel-header">
        <span className="panel-title">告警级别分布</span>
      </div>
      <div style={{ height: 160 }}>
        <ReactECharts
          option={barOption}
          style={{ height: '100%', width: '100%' }}
          opts={{ renderer: 'canvas' }}
        />
      </div>
    </div>
  );
};

export default StatsPanel;
