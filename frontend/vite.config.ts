/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 개발 중에는 화면과 서버를 같은 주소처럼 쓴다. 쿠키·CSRF 설정이 단순해진다 (research R-02)
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/images': 'http://localhost:8080',
      // 소셜 로그인 시작과 제공자 콜백은 서버가 처리한다
      '/oauth2': 'http://localhost:8080',
      '/login/oauth2': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
  },
})
