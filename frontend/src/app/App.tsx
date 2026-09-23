import { HealthStatus } from './HealthStatus';
import { useApiReadiness } from './useApiReadiness';

export function App() {
  const readiness = useApiReadiness();

  return (
    <main className="page" tabIndex={-1}>
      <header>
        <p className="eyebrow">Fundação técnica</p>
        <h1>SRM Credit Engine</h1>
        <p className="description">
          Ambiente inicial da plataforma de cessão de crédito multimoedas.
        </p>
      </header>
      <HealthStatus readiness={readiness} />
      <p className="scope-note">
        Esta versão valida apenas a integração da aplicação. Nenhuma funcionalidade financeira foi
        implementada.
      </p>
    </main>
  );
}
