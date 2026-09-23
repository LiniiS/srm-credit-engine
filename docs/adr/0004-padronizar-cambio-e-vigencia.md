# ADR-0004: Padronizar câmbio e vigência

- **Status:** Proposto
- **Data:** 2026-09-23
- **Decisores:** responsável pelo projeto
- **Relacionado a:** RF-01, RF-02, RF-05

## Contexto

Cross-currency exige direção inequívoca, instante de seleção e política para taxa velha. Inverter multiplicação/divisão é um risco financeiro material.

## Opções consideradas

1. **Base/quote = quote por 1 base, conversão ao final e snapshot** — convenção explícita e auditável.
2. **Armazenar ambos os sentidos** — leitura simples; duplicação e risco de inconsistência.
3. **Conversão antes do pricing** — mistura risco do ativo e câmbio, dificultando auditoria.

## Decisão

Propor opção 1. Ex.: USD/BRL=5,10 significa 1 USD=5,10 BRL; BRL→USD divide, USD→BRL multiplica. Seleciona-se a taxa vigente no instante da operação, converte-se após o VP e grava-se id+valor. Idade máxima proposta: 15 minutos para sync externo e configuração explícita para operação manual.

## Limites e regras resultantes

- Taxas são append-only; mesma moeda não usa FX.
- Taxa ausente/expirada bloqueia liquidação; fallback só usa taxa persistida ainda válida.
- Chamada ao provedor ocorre antes da transação de liquidação.

## Verificação

- Casos de contrato para ambos os sentidos, mesma moeda, fronteira de validade e clock controlado.

## Consequências

- Positivas: direção e histórico claros.
- Negativas: operações podem ser recusadas durante indisponibilidade prolongada.
- Revisitar se: mesa definir fonte oficial, janela ou regra de fixing distinta.

