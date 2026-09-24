import { ESLint } from 'eslint';
import { mkdir, rm, rmdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { cwd } from 'node:process';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

const eslint = new ESLint({ cwd: cwd() });
const lintResults = new Map();
const createdFiles = new Set();
const fixtureFiles = new Map([
  [
    'src/app/__architecture_allowed.ts',
    "import '../features/__architecture_pricing/index'; import '../shared/__architecture_value';",
  ],
  [
    'src/features/__architecture_pricing/__architecture_allowed.ts',
    "import '../../shared/__architecture_value';",
  ],
  ['src/features/__architecture_pricing/index.ts', 'export {};'],
  ['src/features/__architecture_settlements/internal.ts', 'export {};'],
  ['src/shared/__architecture_value.ts', 'export {};'],
  ['src/shared/__architecture_imports_app.ts', "import '../app/App';"],
  [
    'src/shared/__architecture_imports_feature.ts',
    "import '../features/__architecture_pricing/index';",
  ],
  [
    'src/features/__architecture_pricing/__architecture_imports_other_feature.ts',
    "import '../__architecture_settlements/internal';",
  ],
  ['src/shared/__architecture_alias_app.ts', "import '@app/internal';"],
  [
    'src/features/__architecture_pricing/__architecture_alias_feature.ts',
    "import '@features/other/internal';",
  ],
  ['src/orphan/__architecture_unknown.ts', 'export {};'],
]);

beforeAll(async () => {
  for (const [relativePath, source] of fixtureFiles) {
    const absolutePath = resolve(relativePath);
    await mkdir(dirname(absolutePath), { recursive: true });
    await writeFile(absolutePath, source, { encoding: 'utf8', flag: 'wx' });
    createdFiles.add(absolutePath);
  }
  const results = await eslint.lintFiles([...fixtureFiles.keys()]);
  for (const result of results) {
    lintResults.set(
      result.filePath,
      result.messages.map((message) => message.ruleId),
    );
  }
}, 30_000);

afterAll(async () => {
  await Promise.all([...createdFiles].map((file) => rm(file, { force: true })));
  await Promise.all(
    [
      'src/features/__architecture_pricing',
      'src/features/__architecture_settlements',
      'src/features',
      'src/orphan',
    ].map((directory) => rmdir(resolve(directory)).catch(() => undefined)),
  );
});

function ruleIds(relativePath) {
  return lintResults.get(resolve(relativePath));
}

describe('frontend import boundaries through the production ESLint config', () => {
  it.each([
    'src/shared/__architecture_imports_app.ts',
    'src/shared/__architecture_imports_feature.ts',
    'src/features/__architecture_pricing/__architecture_imports_other_feature.ts',
  ])('rejects the prohibited import in %s', (relativePath) => {
    expect(ruleIds(relativePath)).toContain('boundaries/element-types');
  });

  it.each([
    'src/app/__architecture_allowed.ts',
    'src/features/__architecture_pricing/__architecture_allowed.ts',
  ])('accepts the permitted imports in %s', (relativePath) => {
    expect(ruleIds(relativePath)).toEqual([]);
  });

  it('rejects a source file outside app, features and shared', () => {
    expect(ruleIds('src/orphan/__architecture_unknown.ts')).toContain(
      'boundaries/no-unknown-files',
    );
  });

  it.each([
    'src/shared/__architecture_alias_app.ts',
    'src/features/__architecture_pricing/__architecture_alias_feature.ts',
  ])('applies the effective no-restricted-imports override to %s', (relativePath) => {
    expect(ruleIds(relativePath)).toContain('no-restricted-imports');
  });
});
