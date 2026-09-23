import type { ApiReadiness } from './useApiReadiness';

const messages: Record<ApiReadiness, string> = {
  loading: 'Verificando disponibilidade da API…',
  available: 'API disponível',
  unavailable: 'API indisponível',
};

interface HealthStatusProps {
  readiness: ApiReadiness;
}

export function HealthStatus({ readiness }: HealthStatusProps) {
  return (
    <section className={`health health--${readiness}`} aria-labelledby="health-title">
      <h2 id="health-title">Estado do ambiente</h2>
      <p role="status" aria-live="polite">
        {messages[readiness]}
      </p>
    </section>
  );
}
