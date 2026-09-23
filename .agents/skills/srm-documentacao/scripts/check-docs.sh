#!/usr/bin/env bash
# Verificação de consistência da documentação do SRM Credit Engine.
# Uso: bash <caminho-da-skill>/scripts/check-docs.sh [raiz-do-repo] [story|release]
set -uo pipefail

ROOT="${1:-.}"
PHASE="${2:-story}"
if [[ "$PHASE" != "story" && "$PHASE" != "release" ]]; then
  echo "Fase inválida: $PHASE (use story ou release)"
  exit 2
fi
cd "$ROOT" || { echo "Raiz inválida: $ROOT"; exit 2; }

errors=0
warns=0
err()  { echo "❌ $*"; errors=$((errors+1)); }
warn() { echo "⚠️  $*"; warns=$((warns+1)); }
ok()   { echo "✅ $*"; }

echo "== Arquivos obrigatórios =="
required=(
  README.md
  AI_USAGE.md
  docs/adr/README.md
  docs/architecture/c4-context.md
  docs/architecture/c4-container.md
  docs/database/er.md
  docs/acceptance-criteria.md
)
optional=(
  .env.example
)
for f in "${required[@]}"; do [[ -f "$f" ]] && ok "$f" || err "Ausente: $f"; done

mig_dir=backend/src/main/resources/db/migration
mapfile -t migrations < <(find "$mig_dir" -maxdepth 1 -type f -name 'V*.sql' -print 2>/dev/null | sort)
if [[ "$PHASE" == "release" ]]; then
  release_required=(
    docs/database/ddl.sql
    docs/scale/high-scale-design.md
    docs/eda/event-model.md
    docs/observability.md
  )
  for f in "${release_required[@]}"; do [[ -f "$f" ]] && ok "$f" || err "Ausente no release: $f"; done
else
  if [[ -f docs/database/ddl.sql ]]; then
    ok "docs/database/ddl.sql"
  elif (( ${#migrations[@]} == 0 )); then
    warn "Ausente durante story sem migrations Flyway: docs/database/ddl.sql"
  else
    err "Ausente com migrations Flyway existentes: docs/database/ddl.sql"
  fi
  story_optional=(
    docs/scale/high-scale-design.md
    docs/eda/event-model.md
    docs/observability.md
  )
  for f in "${story_optional[@]}"; do [[ -f "$f" ]] && ok "$f" || warn "Ausente durante story (obrigatório no release): $f"; done
fi
for f in "${optional[@]}"; do [[ -f "$f" ]] && ok "$f" || warn "Ausente (recomendado): $f"; done

echo
echo "== Índice de ADRs =="
if [[ -d docs/adr ]]; then
  for adr in docs/adr/[0-9][0-9][0-9][0-9]-*.md; do
    [[ -e "$adr" ]] || { warn "Nenhum ADR encontrado em docs/adr"; break; }
    name="$(basename "$adr")"
    grep -q "$name" docs/adr/README.md 2>/dev/null || err "ADR fora do índice: $name"
    grep -qiE '^\- \*\*Status:\*\*|^Status:' "$adr" || warn "ADR sem campo Status: $name"
  done
  dups=$(ls docs/adr 2>/dev/null | grep -oE '^[0-9]{4}' | sort | uniq -d)
  [[ -n "$dups" ]] && err "Numeração de ADR duplicada: $dups"
fi

echo
echo "== Links relativos em Markdown =="
tmp_broken="$(mktemp)"
while IFS= read -r md; do
  dir="$(dirname "$md")"
  while IFS= read -r target; do
    target="${target%% *}"
    [[ "$target" =~ ^(https?:|mailto:|#) ]] && continue
    path="${target%%#*}"
    [[ -z "$path" ]] && continue
    [[ -e "$dir/$path" ]] || echo "$md -> $target" >> "$tmp_broken"
  done < <(grep -oE '\]\([^)]+\)' "$md" | sed -E 's/^\]\(//; s/\)$//')
done < <(find . \
          \( -type d \( -name node_modules -o -name target -o -name dist -o -name coverage \
             -o -name .git -o -name _bmad -o -name _bmad-output -o -name .bmad \
             -o -name .claude -o -name .cursor \) \) -prune \
          -o -name '*.md' -print)
if [[ -s "$tmp_broken" ]]; then
  while IFS= read -r l; do err "Link quebrado: $l"; done < "$tmp_broken"
else
  ok "Nenhum link relativo quebrado"
fi
rm -f "$tmp_broken"

echo
echo "== Pendências esquecidas =="
if grep -rnE 'TODO|TBD|FIXME|XXX|<preencher>' README.md AI_USAGE.md docs 2>/dev/null; then
  warn "Há marcadores de pendência na documentação (listados acima)"
else
  ok "Sem marcadores de pendência"
fi

echo
echo "== Coerência básica com o código =="
compose=""
for candidate in compose.yaml compose.yml docker-compose.yaml docker-compose.yml; do
  if [[ -f "$candidate" ]]; then
    compose="$candidate"
    break
  fi
done
if [[ -n "$compose" ]]; then
  ok "Compose detectado: $compose"
  grep -oE '"?[0-9]{2,5}:[0-9]{2,5}"?' "$compose" | tr -d '"' | cut -d: -f1 | sort -u | while read -r port; do
    grep -q "$port" README.md 2>/dev/null || echo "⚠️  Porta $port do compose não aparece no README"
  done
else
  warn "Arquivo Compose não encontrado (compose.yaml, compose.yml, docker-compose.yaml ou docker-compose.yml)"
fi
if (( ${#migrations[@]} > 0 )); then
  newest_mig="${migrations[0]}"
  for migration in "${migrations[@]}"; do
    [[ "$migration" -nt "$newest_mig" ]] && newest_mig="$migration"
  done
  if [[ ! -f docs/database/ddl.sql ]]; then
    err "Migrations Flyway existem, mas docs/database/ddl.sql está ausente"
  elif [[ "$newest_mig" -nt docs/database/ddl.sql ]]; then
    err "Há migration mais nova que docs/database/ddl.sql ($newest_mig) — regenerar DDL"
  else
    ok "DDL está atualizado em relação às migrations Flyway"
  fi
  if [[ ! -f docs/database/er.md ]]; then
    err "Migrations Flyway existem, mas docs/database/er.md está ausente"
  elif [[ "$newest_mig" -nt docs/database/er.md ]]; then
    err "Há migration mais nova que docs/database/er.md ($newest_mig) — atualizar ER"
  else
    ok "ER está atualizado em relação às migrations Flyway"
  fi
fi

echo
echo "Resumo: $errors erro(s), $warns aviso(s)"
[[ $errors -eq 0 ]]
