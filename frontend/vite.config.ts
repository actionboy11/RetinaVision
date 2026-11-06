import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    rollupOptions: {
      output: {
        // Element Plus 已改为按需引入（src/plugins/element-plus.ts 静态登记组件），
        // 体积仍较大且版本稳定，单独成 chunk 便于浏览器长缓存；Vue runtime 等公共
        // 依赖会被 Rollup 一并归入该 chunk，其余模块交给默认分包，避免切得太细
        // 引入循环依赖。
        manualChunks(id) {
          // Rollup 在 Windows 下可能给出反斜杠路径，统一归一化后再匹配
          const path = id.replace(/\\/g, '/')
          if (
            path.includes('/node_modules/element-plus/') ||
            path.includes('/node_modules/@element-plus/')
          ) {
            return 'element-plus'
          }
          return undefined
        },
      },
    },
  },
})
