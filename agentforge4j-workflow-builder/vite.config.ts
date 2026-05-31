import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  root: 'dev',
  publicDir: false,
  server: {
    open: true,
  },
  build: {
    outDir: '../dev-dist',
    emptyOutDir: true,
  },
});
