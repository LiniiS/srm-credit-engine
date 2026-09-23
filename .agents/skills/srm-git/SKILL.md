---
name: srm-git
description: Conduz o versionamento do SRM Credit Engine no padrão sênior/especialista exigido pelo desafio — branches curtas por story, commits atômicos em Conventional Commits, descrição de Pull Request, histórico linear com rebase interativo (fixup/autosquash), hooks (Husky/commitlint/lint-staged), tags SemVer e simulação de gestão de crise (git revert e cherry-pick de hotfix). Use SEMPRE antes de qualquer commit, ao criar branch, ao abrir ou descrever PR, ao organizar histórico antes de merge, ao preparar release/tag, ao configurar hooks, ou ao concluir uma task/story do BMAD — mesmo que o usuário só diga "salva isso" ou "commita".
---

# SRM Credit Engine — Git e versionamento

O desafio avalia se o histórico **conta uma história**: cada commit é uma unidade lógica rastreável até uma story, compila, passa nos testes e tem mensagem que explica o porquê. O agente prepara e executa commits locais; ações que reescrevem ou publicam histórico exigem confirmação explícita do usuário.

## Limites de autonomia (segurança)

- Pode executar sem perguntar: `git status`, `git diff`, `git log`, `git add` seletivo, `git commit` na branch de feature, `git commit --fixup`, `git switch -c`.
- **Pedir confirmação explícita antes de:** `git push` (qualquer), `push --force-with-lease`, `rebase` de branch já publicada, `tag` + push de tag, `revert`/`cherry-pick` na `main`, `reset --hard`, deletar branch.
- Nunca: `push --force` (sem lease), commitar na `main` diretamente, commitar com `--no-verify`, commitar segredos, reescrever histórico da `main` publicada.

## Passo 0 — Contexto

1. `git status` e `git branch --show-current`. Se estiver na `main`, crie a branch antes de qualquer commit.
2. Identifique a story BMAD em andamento (id, título, épico) — ela define branch, escopo e referências.
3. Leia `references/convencoes.md` (tipos, escopos, branches, exemplos). Para release/crise/hooks, leia `references/release-crise-hooks.md`.

## Branches

`<tipo>/<story-id>-<slug-curto>` — ex.: `feature/2.3-pricing-strategy`, `fix/3.1-rounding-half-even`, `docs/adr-currency-convention`, `chore/ci-pipeline`, `hotfix/1.0.1-fx-inversion`.
Estratégia padrão: **GitHub Flow com branches curtas + rebase** (justificativa e alternativa em `references/convencoes.md`; se o usuário escolher outra, registre ADR via `srm-arquitetura`).

## Workflow de commit (sempre)

1. **Revisar o diff:** `git diff` e `git diff --staged`. Procure: segredos, `.env`, arquivos gerados, debug esquecido (`System.out`, `console.log`), código comentado, arquivos fora do escopo da task.
2. **Particionar em unidades lógicas.** Um commit = uma intenção. Separe: migração / domínio+testes / API / docs / config. Teste junto com o código que ele prova (não em commit separado). Use `git add -p` para dividir arquivos com mudanças de intenções diferentes.
3. **Gate antes do commit:** o gate da skill correspondente (`srm-java-spring` ou `srm-react`) para a parte tocada. Cada commit deve compilar e passar nos testes — isso preserva `git bisect`.
4. **Mensagem Conventional Commits** (ver formato abaixo). Descrição em inglês, imperativo, minúscula, sem ponto final, ≤ 72 caracteres. Corpo explica **o porquê** e o impacto. Rodapé referencia story/ADR.
5. **Commit** e confirme com `git log --oneline -5`.
6. Correção de um commit anterior da mesma branch → `git commit --fixup=<sha>` (será squashado no rebase), não "fix typo" solto.

## Formato da mensagem

```
<tipo>(<escopo>): <descrição imperativa>

<corpo: por que a mudança existe, decisões, efeitos colaterais — linhas ≤ 100>

Refs: story <id>
ADR: 0004            (quando aplicável)
BREAKING CHANGE: ... (quando aplicável)
```
Tipos: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`, `style`, `revert`.
Escopos: `pricing`, `currency`, `settlement`, `reporting`, `shared`, `db`, `api`, `web`, `infra`, `obs`, `docs`, `adr`, `deps`.

Exemplos:
- `feat(pricing): add strategy registry resolving spread by receivable type`
- `fix(currency): convert BRL to USD by dividing by USD/BRL rate`
- `test(settlement): prove single winner under concurrent settlement`
- `feat(db): add settlement tables with optimistic lock version column`
- `docs(adr): record decision on fractional exponent with big-math`

## Antes do PR (organizar histórico)

```bash
git fetch origin
git rebase -i --autosquash origin/main   # squash dos fixup!, reordenar, reescrever mensagens
<rodar gate completo>                    # garantir que o resultado final está verde
```
Critérios do histórico: sem commits "wip"/"fix"/"ajustes"; sem merge commits de sincronização (use rebase); ordem lógica (schema → domínio → serviço → API → front → docs).
Push da branch rebaseada: `git push --force-with-lease` — **só com confirmação**.

## Pull Request

Gere a descrição com o template em `references/convencoes.md` (seção PR). Título = Conventional Commit do conjunto. Merge preferencial: **Rebase and merge** (preserva commits atômicos e linearidade). Squash merge só se a branch tiver um único propósito pequeno — escolha uma política e mantenha.

## Integração com BMAD

- Início da story (dev-story): criar branch da story.
- Fim de cada task: commits atômicos da task (gate verde).
- Fim da story: rebase interativo, PR com link para a story e checklist de ACs; atualizar `Change Log` da story com o PR.
- Fim do épico / versão: tag SemVer anotada (ver `references/release-crise-hooks.md`), coordenada com `srm-documentacao`.

## Saída esperada

```markdown
## Git
- Branch: feature/2.3-pricing-strategy
- Commits:
  - abc1234 feat(pricing): ...
  - def5678 test(pricing): ...
- Gate antes do commit: ✅
- Ações pendentes de confirmação: push --force-with-lease / abrir PR / tag
- Descrição do PR: <bloco pronto para colar>
```
