import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
    exclude: [
      '**/node_modules/**',
      'src/tests/adminPrototypePages.test.js',
      'src/tests/adminSidebar.test.js',
      'src/tests/voiceGenerationApi.test.js',
      'src/tests/voiceGenerationWorkbench.test.js'
    ]
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
})
