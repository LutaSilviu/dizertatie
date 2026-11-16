import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '..', '')

  return {
    plugins: [react()],
    server: {
      proxy: {
        // Mirrors nginx.conf: /api/scans -> backend /scans (the backend has no /api prefix).
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ''),
          // Scans run synchronously and can take minutes.
          timeout: 300_000,
          proxyTimeout: 300_000,
          headers: {
            'X-API-Key': env.APP_API_KEY || 'local-development-only',
          },
        },
      },
    },
  }
})
