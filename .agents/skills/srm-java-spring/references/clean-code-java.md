# Clean Code e princípios aplicados ao Java deste projeto

## Nomes
- Nomes do domínio (ver linguagem ubíqua). `calculatePresentValue`, não `calc`/`process`/`handle`.
- Classes são substantivos (`PricingService`), métodos são verbos (`settle`, `resolve`).
- Booleanos como pergunta: `isActive`, `hasExpired`.
- Sem sufixos genéricos (`Manager`, `Helper`, `Util`) quando existir nome melhor.

## Funções
- Curtas, um nível de abstração, até ~3 parâmetros (acima disso, `record` de comando).
- Sem argumentos booleanos que mudam comportamento (`price(x, true)`) — crie dois métodos ou strategy.
- Guard clauses no início em vez de `if` aninhado.
- Não retorne `null`: use `Optional` em buscas, coleção vazia em listas, exceção para violação.

## Classes
- Imutabilidade por padrão: `record`, campos `final`, coleções `List.copyOf`.
- Injeção por construtor (Lombok `@RequiredArgsConstructor` é aceitável; nunca `@Autowired` em campo).
- Visibilidade mínima: package-private para implementações internas do módulo.
- Composição sobre herança; herança só para hierarquia real de exceções.

## Comentários
- Código deve se explicar. Comente o **porquê** (ex.: "HALF_EVEN por exigência de ADR-0003"), nunca o **o quê**.
- Javadoc nas ports e na `PricingStrategy` (contrato público).
- Sem código comentado, sem `TODO` sem link para issue/story.

## Exemplos de aplicação dos princípios

**OCP/Strategy — errado:**
```java
BigDecimal spread = switch (type) { case "DUPLICATA" -> new BigDecimal("0.015"); case "CHEQUE" -> ...; };
```
**Certo:** `registry.resolve(type).spread(context)` — novo tipo não mexe aqui.

**YAGNI — errado:** `AbstractPricingStrategy<T extends Receivable, R extends PricingResult>` com template methods para uma regra que hoje é "spread fixo por tipo".
**Certo:** interface de 2 métodos; generalize quando a segunda regra real divergir.

**KISS — errado:** framework de regras (Drools/SpEL) para dois spreads.
**Certo:** strategies Java testáveis.

**DRY verdadeiro:** conversão de câmbio implementada uma única vez em `ExchangeRate.convert(Money)`; simulação e liquidação usam o mesmo `PricingService` — nunca duas implementações da fórmula (inclusive no frontend).

## Estilo e ferramentas
- Formatação automática (Spotless + google-java-format ou palantir-java-format) — decisão única no projeto.
- Lint estático opcional (Checkstyle/Error Prone/SpotBugs) no CI; zero warnings novos.
- `var` quando o tipo é óbvio pelo lado direito; tipo explícito quando melhora a leitura.
- Streams quando claros; loop simples quando o stream vira quebra-cabeça.
