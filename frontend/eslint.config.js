import js from '@eslint/js';
import globals from 'globals';
import svelte from 'eslint-plugin-svelte';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist/', 'node_modules/'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...svelte.configs['flat/recommended'],
  {
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
    rules: {
      eqeqeq: ['error', 'always', { null: 'ignore' }],
      'no-console': 'warn',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      // We render only static, trusted SVG fragments (see Icon.svelte); no user input reaches {@html}.
      'svelte/no-at-html-tags': 'off',
    },
  },
  {
    files: ['**/*.svelte', '**/*.svelte.ts'],
    languageOptions: { parserOptions: { parser: tseslint.parser } },
  },
  {
    files: ['**/*.svelte'],
    rules: {
      // The app has no router; navigation is internal state, so resolve() does not apply.
      'svelte/no-navigation-without-resolve': 'off',
    },
  },
);
