import { defineConfig } from 'vitest/config';
import { svelte } from '@sveltejs/vite-plugin-svelte';

export default defineConfig({
  plugins: [svelte()],
  resolve: { conditions: ['browser'] },
  server: { proxy: { '/graphql': 'http://localhost:8080', '/api': 'http://localhost:8080' } },
  test: { environment: 'jsdom', include: ['src/**/*.test.ts'], setupFiles: ['./src/test-setup.ts'] },
});

