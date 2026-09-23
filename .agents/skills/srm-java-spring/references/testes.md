# Testes do backend

## Pirâmide e ferramentas

| Nível | Ferramenta | O que prova | Velocidade |
|---|---|---|---|
| Unitário de domínio | JUnit 5 + AssertJ | Strategies, cálculo, VOs, validações de regra | ms |
| Contrato HTTP | `@WebMvcTest` + MockMvc | Status, ProblemDetail, validação, serialização de decimal | rápido |
| Integração | `@SpringBootTest` + Testcontainers (PostgreSQL) | Migrações, transação, rollback, locking, idempotência, queries do relatório | segundos |
| Arquitetura | ArchUnit | Camadas e limites de módulo | rápido |
| Resiliência | Teste do adapter com WireMock ou mock com falha injetada | Retry, circuit breaker, fallback | rápido |

## Convenções

- Nome: `metodo_deveComportamento_quandoCondicao` ou frase em snake_case em inglês — escolha uma e mantenha.
- Estrutura Given/When/Then visível (linhas em branco ou comentários curtos).
- Um comportamento por teste; asserts de valores monetários com `isEqualByComparingTo`.
- Parametrizados (`@ParameterizedTest` + `@CsvSource`/`@MethodSource`) para tabelas de cálculo.
- Sem `Thread.sleep` em testes; use `Clock` fixo (`Clock.fixed(...)`) e latches.
- Testcontainers com container reutilizado entre classes (`@ServiceConnection` + singleton) para manter o CI rápido.
- Mocks só nas fronteiras (ports, adapters externos). Não mocke `BigDecimal`, VOs ou a própria classe sob teste.

## ArchUnit mínimo

```java
@AnalyzeClasses(packages = "com.srm.creditengine")
class ArchitectureTest {
    @ArchTest
    static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
        .layer("Api").definedBy("..api..")
        .layer("Service").definedBy("..service..")
        .layer("Domain").definedBy("..domain..")
        .layer("Persistence").definedBy("..persistence..")
        .whereLayer("Api").mayNotBeAccessedByAnyLayer()
        .whereLayer("Service").mayOnlyBeAccessedByLayers("Api")
        .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Api") // só reporting.api → reporting.persistence
        .ignoreDependency(resideInAPackage("..reporting.api.."), resideInAPackage("..reporting.persistence.."));

    @ArchTest
    static final ArchRule domainIsPure = noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.persistence..", "com.fasterxml.jackson..");

    @ArchTest
    static final ArchRule noDoubleForMoney = noFields().that().haveNameMatching(".*(amount|value|rate|spread).*")
        .should().haveRawType(double.class).orShould().haveRawType(Double.class);

    @ArchTest
    static final ArchRule noCycles = slices().matching("com.srm.creditengine.(*)..").should().beFreeOfCycles();
}
```
Ajuste as regras à estrutura real e ao ADR de camadas; a regra de `Persistence` acima é ilustrativa — o ponto é que controllers comuns **não** acessam persistência, só o `reporting`.

## Cobertura

JaCoCo com `check` no `verify`: falha o build abaixo do limite acordado. Cobertura é piso, não meta: um teste sem asserção relevante não conta na revisão.
