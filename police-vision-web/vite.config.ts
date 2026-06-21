import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    host: '0.0.0.0',
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8085',
        changeOrigin: true
      }
    }
  },
  css: {
    preprocessorOptions: {
      less: {
        modifyVars: {
          'primary-color': '#1890ff',
          'layout-body-background': '#0a0e1a',
          'component-background': '#141829',
          'text-color': '#e6f1ff',
          'text-color-secondary': '#8c9cb8',
          'border-color-base': '#1f2940'
        },
        javascriptEnabled: true
      }
    }
  }
});
