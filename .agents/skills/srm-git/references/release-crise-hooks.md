# Release, gestão de crise e hooks

## Hooks (Husky + lint-staged + commitlint na raiz)

O repositório tem front (Node) e back (Java); Husky na raiz aproveita o Node já necessário. Registre a escolha em ADR (alternativa: framework `pre-commit` em Python).

`package.json` (raiz):
```json
{
  "private": true,
  "scripts": { "prepare": "husky" },
  "devDependencies": {
    "husky": "^9", "lint-staged": "^15",
    "@commitlint/cli": "^19", "@commitlint/config-conventional": "^19"
  },
  "lint-staged": {
    "frontend/**/*.{ts,tsx}": ["npm --prefix frontend exec eslint --fix", "npm --prefix frontend exec prettier --write"],
    "backend/**/*.java": ["bash -c 'cd backend && ./mvnw -q spotless:apply'"]
  }
}
```
Confira as versões atuais antes de instalar.

`.husky/pre-commit`
```sh
npx lint-staged
```
`.husky/commit-msg`
```sh
npx --no -- commitlint --edit "$1"
```
`.husky/pre-push`
```sh
(cd backend && ./mvnw -q test) && npm --prefix frontend run test -- --run
```
`commitlint.config.cjs`
```js
module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'scope-enum': [2, 'always', ['pricing','currency','settlement','reporting','shared','db','api','web','infra','obs','docs','adr','deps','ci','release']],
    'header-max-length': [2, 'always', 72],
  },
};
```
O CI deve repetir as mesmas verificações (hooks podem ser pulados localmente; o CI não).

## Release com SemVer

```bash
git switch main && git pull --ff-only
<gate completo + scripts/check-docs.sh da skill srm-documentacao>
git tag -a v1.0.0 -m "v1.0.0: pricing, fx, settlement, reporting, observability"
git push origin v1.0.0          # somente com confirmação
```
- MAJOR: quebra de contrato da API; MINOR: funcionalidade compatível; PATCH: correção.
- Opcional: `CHANGELOG.md` gerado dos Conventional Commits (git-cliff / conventional-changelog).
- Tag anotada, nunca leve; nunca mover tag publicada (crie `v1.0.1`).

## Simulação de gestão de crise (item Especialista)

Faça de forma **deliberada e documentada** — o objetivo é demonstrar controle, então o README deve narrar o cenário com links para commits/PRs.

### Cenário A — `git revert` de bug crítico na main
1. Branch `fix/crisis-demo-inverted-fx` introduz bug realista e visível, ex.: conversão BRL→USD multiplicando em vez de dividir. Merge via PR (o bug "passou na revisão").
2. Detecção: teste de regressão/alerta ou operador reporta valores absurdos. Registre o "incidente" (horário, impacto, detecção).
3. Mitigação imediata na `main`:
   ```bash
   git switch main && git pull --ff-only
   git revert <sha-do-commit-com-bug>        # ou -m 1 <sha-merge> se houve merge commit
   # mensagem: revert: "fix(currency): ..." + corpo com motivo e referência ao incidente
   ```
   Abra PR do revert (rápido, com CI). O revert preserva histórico — não reescreve a `main`.
4. Correção definitiva em nova branch com **teste que reproduz o bug primeiro**, depois o fix.
5. Postmortem curto em `docs/incidents/AAAA-MM-DD-fx-inversion.md` (linha do tempo, causa raiz, ação preventiva).

### Cenário B — `cherry-pick` de hotfix para produção
1. Produção está em `v1.0.0`; `main` já avançou com features não liberadas.
2. ```bash
   git switch -c hotfix/1.0.1-fx-inversion v1.0.0
   git cherry-pick -x <sha-do-fix-na-main>   # -x registra a origem no commit
   <gate>
   git tag -a v1.0.1 -m "v1.0.1: hotfix fx conversion direction"
   ```
3. Documente por que cherry-pick (não levar features não validadas para produção).

## Checklist de release

- [ ] `main` verde no CI
- [ ] Docs verificadas (`check-docs.sh`), README com versão e como rodar conferido
- [ ] `AI_USAGE.md` atualizado
- [ ] Tag anotada SemVer criada e publicada (com confirmação)
- [ ] Stories do épico com status `Done`
