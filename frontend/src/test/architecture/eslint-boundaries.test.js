import { Linter } from 'eslint';
import boundaries from 'eslint-plugin-boundaries';
import { cwd } from 'node:process';

import {
  boundaryRules,
  createBoundarySettings,
  featureRestrictedImports,
  sharedRestrictedImports,
} from '../../../eslint.config.js';

const linter = new Linter({ configType: 'flat' });
const fixtureRoot = 'src/test/architecture/fixtures';
const config = {
  files: ['**/*.{ts,tsx}'],
  languageOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
  },
  plugins: { boundaries },
  rules: boundaryRules,
  settings: {
    ...createBoundarySettings(fixtureRoot),
    'boundaries/root-path': cwd(),
  },
};

function lint(source, filename) {
  return linter.verify(source, config, { filename });
}

describe('frontend import boundaries', () => {
  it.each([
    [
      'shared importing app',
      "import '../app/index';",
      `${fixtureRoot}/shared/source.ts`,
    ],
    [
      'shared importing a feature',
      "import '../features/pricing/index';",
      `${fixtureRoot}/shared/source.ts`,
    ],
    [
      'feature importing another feature internal',
      "import '../settlements/internal';",
      `${fixtureRoot}/features/pricing/source.ts`,
    ],
  ])('rejects %s', (_case, source, filename) => {
    const messages = lint(source, filename);

    expect(messages.map((message) => message.ruleId)).toContain('boundaries/element-types');
  });

  it.each([
    [
      'app composing features and shared',
      "import '../features/pricing/index'; import '../shared/value';",
      `${fixtureRoot}/app/source.ts`,
    ],
    [
      'feature consuming shared',
      "import '../../shared/value';",
      `${fixtureRoot}/features/pricing/source.ts`,
    ],
  ])('accepts %s', (_case, source, filename) => {
    expect(lint(source, filename)).toEqual([]);
  });

  it.each([
    ['shared alias to app', "import '@app/internal';", sharedRestrictedImports],
    [
      'feature alias to another feature internal',
      "import '@features/settlements/internal';",
      featureRestrictedImports,
    ],
  ])('rejects %s through no-restricted-imports', (_case, source, restriction) => {
    const messages = linter.verify(
      source,
      { ...config, rules: { 'no-restricted-imports': restriction } },
      { filename: `${fixtureRoot}/shared/source.ts` },
    );

    expect(messages.map((message) => message.ruleId)).toContain('no-restricted-imports');
  });
});
