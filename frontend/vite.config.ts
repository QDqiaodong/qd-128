import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    host: '127.0.0.1',
    port: Number(process.env.FRONTEND_PORT || 8228),
    strictPort: true,
    proxy: {
      '/api': {
        target: `http://localhost:${process.env.BACKEND_PORT || 8328}`,
        changeOrigin: true
      }
    }
  },
  preview: {
    host: '127.0.0.1',
    port: Number(process.env.FRONTEND_PORT || 8228),
    strictPort: true
  }
})
