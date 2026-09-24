import axe, { type AxeResults, type RunOptions } from 'axe-core';

const jsdomOptions: RunOptions = {
  rules: {
    'color-contrast': { enabled: false },
  },
};

export async function expectNoAccessibilityViolations(
  context: Element | Document = document,
): Promise<void> {
  const results = await axe.run(context, jsdomOptions);
  if (results.violations.length > 0) {
    throw new Error(formatViolations(results));
  }
}

function formatViolations(results: AxeResults): string {
  return results.violations
    .map(
      (violation) =>
        `${violation.id}: ${violation.help}\n${violation.nodes
          .map((node) => `  ${node.target.join(' ')}: ${node.failureSummary ?? 'sem detalhe'}`)
          .join('\n')}`,
    )
    .join('\n');
}
