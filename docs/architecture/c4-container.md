# C4 — Containers

```mermaid
C4Container
  title SRM Credit Engine - Containers propostos
  Person(operator, "Operador da mesa")
  Person(auditor, "Auditoria/Compliance")
  System_Boundary(srm, "SRM Credit Engine") {
    Container(web, "Web SPA", "React, TypeScript, Vite", "Formulário, simulação e extrato")
    Container(api, "API", "Java 21, Spring Boot 3", "Câmbio, pricing, settlement e reporting")
    ContainerDb(db, "Banco", "PostgreSQL 16", "Fonte transacional e consultas jOOQ")
  }
  System_Ext(fx, "Provedor de câmbio mock", "Taxas externas simuladas")
  System_Ext(obs, "Prometheus/Grafana", "Métricas e dashboards opcionais do MVP")
  Rel(operator, web, "Usa", "HTTPS")
  Rel(auditor, web, "Usa", "HTTPS")
  Rel(web, api, "Chama", "JSON/HTTPS")
  Rel(api, db, "Lê e escreve", "JDBC/JPA/jOOQ")
  Rel(api, fx, "Consulta", "HTTP com Resilience4j")
  Rel(obs, api, "Coleta", "/actuator/prometheus")
```

Na fundação E0-S1, o `compose.yaml` implementa `frontend` (`5173`), `backend` (`8080`) e `postgres` (`5432`), todos com healthchecks. A SPA consulta a readiness técnica do backend em `/actuator/health/readiness`; funcionalidades de negócio, provedor de câmbio e observabilidade permanecem propostas para stories posteriores.
