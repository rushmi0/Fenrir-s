import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    babel({ presets: [reactCompilerPreset()] })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      // Production serves this SPA and the relay's `/inter/api/v1/**` from the same origin
      // (this app's build output ships into the relay's classpath:public). In dev, proxy to a
      // locally running relay (`just run-jvm`, default port 6724) instead.
      '/inter': 'http://localhost:6724',
    },
  },
})
