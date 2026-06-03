// SPDX-License-Identifier: Apache-2.0
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

const alias: Record<string, string> = {
  '@': resolve(__dirname, 'src'),
};

if (process.env.AFB_LOCAL_BUILDER) {
  alias['@agentforge4j/workflow-builder-react/styles/tokens.css'] = resolve(
    __dirname,
    '../agentforge4j-workflow-builder/src/styles/tokens.css',
  );
  alias['@agentforge4j/workflow-builder-react'] = resolve(
    __dirname,
    '../agentforge4j-workflow-builder/src/index.ts',
  );
}

export default defineConfig({
  plugins: [react()],
  resolve: { alias },
  optimizeDeps: process.env.AFB_LOCAL_BUILDER
    ? { exclude: ['@agentforge4j/workflow-builder-react'] }
    : {},
  build: { outDir: 'dist', sourcemap: true },
});
