import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import App from './App';
import './index.css';

const theme = {
  token: {
    colorPrimary: '#1890ff',
    colorBgContainer: '#141829',
    colorBgLayout: '#0a0e1a',
    colorText: '#e6f1ff',
    colorTextSecondary: '#8c9cb8',
    colorBorder: '#1f2940',
    borderRadius: 4,
  },
  components: {
    Card: {
      colorBgContainer: '#141829',
      colorBorderSecondary: '#1f2940',
    },
    Table: {
      colorBgContainer: '#141829',
      colorBgElevated: '#1a1f35',
      colorBorderSecondary: '#1f2940',
    },
    List: {
      colorBgContainer: '#141829',
    },
  },
};

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider locale={zhCN} theme={theme}>
      <App />
    </ConfigProvider>
  </React.StrictMode>
);
