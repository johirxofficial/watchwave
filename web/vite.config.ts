import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const host = process.env.WATCHWAVE_DEV_HOST ?? 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: host,
        changeOrigin: true,
        ws: true,
      },
    },
  },
  preview: {
    port: 4173,
  },
  build: {
    target: 'es2020',
    sourcemap: false,
    assetsDir: 'assets',
  },
})
