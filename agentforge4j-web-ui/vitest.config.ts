// SPDX-License-Identifier: Apache-2.0
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': resolve(__dirname, 'src') } },
  test: {
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    include: ['tests/**/*.test.{ts,tsx}'],
    // The builder package (and its @xyflow/react dependency) ships a CSS side-effect import
    // in its ESM entrypoint. Vitest externalizes node_modules by default, which skips Vite's
    // asset transform and fails on the raw `.css` import; inlining these two packages routes
    // them through Vite's transform pipeline instead.
    server: {
      deps: {
        inline: [/@agentforge4j\/workflow-builder-react/, /@xyflow\/react/],
      },
    },
  },
});
