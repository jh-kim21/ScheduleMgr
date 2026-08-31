import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        // 기본값은 백엔드 기본 포트. 다른 포트로 띄운 인스턴스에 붙여볼 때만
        // VITE_API_TARGET으로 바꾼다 (예: 검증용 임시 인스턴스).
        target: process.env.VITE_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
