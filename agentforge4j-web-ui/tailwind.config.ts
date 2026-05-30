// SPDX-License-Identifier: Apache-2.0
import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg:            'var(--color-bg)',
        'bg-elevated': 'var(--color-bg-elevated)',
        fg:            'var(--color-fg)',
        'fg-muted':    'var(--color-fg-muted)',
        border:        'var(--color-border)',
        brand:         'var(--color-brand)',
        'brand-ink':   'var(--color-brand-ink)',
        accent:        'var(--color-accent)',
        success:       'var(--color-success)',
        warning:       'var(--color-warning)',
        danger:        'var(--color-danger)',
      },
    },
  },
  plugins: [],
} satisfies Config;
