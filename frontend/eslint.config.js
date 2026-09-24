import js from '@eslint/js';
import boundaries from 'eslint-plugin-boundaries';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export function createBoundarySettings(root = 'src') {
  return {
  'import/resolver': {
    typescript: {
      project: './tsconfig.app.json',
    },
  },
  'boundaries/elements': [
    { type: 'app', pattern: `${root}/app/**`, mode: 'full' },
    { type: 'app', pattern: `${root}/*.ts`, mode: 'full' },
    { type: 'app', pattern: `${root}/*.tsx`, mode: 'full' },
    {
      type: 'feature',
      pattern: `${root}/features/*/**`,
      mode: 'full',
      capture: ['feature'],
    },
    { type: 'shared', pattern: `${root}/shared/**`, mode: 'full' },
  ],
  };
}

export const boundarySettings = createBoundarySettings();

export const boundaryRules = {
  'boundaries/no-unknown': 'error',
  'boundaries/no-unknown-files': 'error',
  'boundaries/element-types': [
    'error',
    {
      default: 'disallow',
      rules: [
        { from: 'app', allow: ['app', 'feature', 'shared'] },
        { from: 'feature', allow: [['feature', { feature: '${from.feature}' }], 'shared'] },
        { from: 'shared', allow: ['shared'] },
      ],
    },
  ],
};

export const sharedRestrictedImports = [
  'error',
  {
    patterns: [
      { group: ['@app/**', '@features/**'], message: 'shared cannot depend on app or features' },
    ],
  },
];

export const featureRestrictedImports = [
  'error',
  {
    patterns: [
      { group: ['@app/**'], message: 'features cannot depend on app' },
      {
        group: ['@features/*/**'],
        message: 'features must consume another feature only through its public entry point',
      },
    ],
  },
];

export default tseslint.config(
  { ignores: ['dist', 'coverage', 'eslint.config.js'] },
  js.configs.recommended,
  ...tseslint.configs.strictTypeChecked,
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: { ...globals.browser, ...globals.node },
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      boundaries,
      'jsx-a11y': jsxA11y,
      'react-hooks': reactHooks,
    },
    rules: {
      ...boundaryRules,
      ...jsxA11y.flatConfigs.recommended.rules,
      ...reactHooks.configs.recommended.rules,
      '@typescript-eslint/no-explicit-any': 'error',
    },
    settings: boundarySettings,
  },
  {
    files: ['*.{ts,tsx}'],
    rules: {
      'boundaries/no-unknown': 'off',
      'boundaries/no-unknown-files': 'off',
    },
  },
  {
    files: ['src/shared/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': sharedRestrictedImports,
    },
  },
  {
    files: ['src/features/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': featureRestrictedImports,
    },
  },
  {
    files: [
      'src/test/**/*.{ts,tsx}',
      'src/**/*.test.{ts,tsx}',
      'src/test/architecture/**/*.test.js',
    ],
    ...tseslint.configs.disableTypeChecked,
    languageOptions: {
      ...tseslint.configs.disableTypeChecked.languageOptions,
      globals: {
        describe: 'readonly',
        expect: 'readonly',
        it: 'readonly',
      },
    },
    rules: {
      ...tseslint.configs.disableTypeChecked.rules,
      'boundaries/no-unknown': 'off',
      'boundaries/no-unknown-files': 'off',
    },
  },
);
