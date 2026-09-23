const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

export async function checkApiReadiness(signal?: AbortSignal): Promise<void> {
  const requestSignal = signal
    ? AbortSignal.any([signal, AbortSignal.timeout(5000)])
    : AbortSignal.timeout(5000);
  const response = await fetch(`${apiUrl}/actuator/health/readiness`, {
    headers: { Accept: 'application/json' },
    signal: requestSignal,
  });

  if (!response.ok) {
    throw new Error('API indisponível');
  }

  const health: unknown = await response.json();
  if (!isReady(health)) {
    throw new Error('API não está pronta');
  }
}

function isReady(health: unknown): health is { status: 'UP' } {
  return (
    typeof health === 'object' &&
    health !== null &&
    'status' in health &&
    health.status === 'UP'
  );
}
