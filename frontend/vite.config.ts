import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '..', '')

  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          headers: {
            'X-API-Key': env.APP_API_KEY ?? 'local-development-only',
          },
        },
      },
    },
  }
})
