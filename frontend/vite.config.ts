import { defineConfig } from 'vitest/config';
import { svelte } from '@sveltejs/vite-plugin-svelte';

export default defineConfig({
  // Enforce Svelte 5 runes ($state/$derived/$props) in our own components (not in node_modules).
  plugins: [
    svelte({
      dynamicCompileOptions: ({ filename }) => (filename.includes('node_modules') ? undefined : { runes: true }),
    }),
  ],
  resolve: { conditions: ['browser'] },
  server: { proxy: { '/graphql': 'http://localhost:8080', '/api': 'http://localhost:8080' } },
  test: { environment: 'jsdom', include: ['src/**/*.test.ts'], setupFiles: ['./src/test-setup.ts'] },
});
