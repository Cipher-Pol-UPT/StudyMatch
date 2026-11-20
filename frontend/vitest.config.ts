import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    include: ['tests/**/*.test.{ts,tsx}'],
    // only with --coverage. lcov.info is what sonarqube reads, every file in src counts even if no test opens it.
    // studymatch-ui is our component library, the pages that use it are what we test
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/studymatch-ui/**'],
      reporter: ['text-summary', 'lcov'],
    },
  },
});
