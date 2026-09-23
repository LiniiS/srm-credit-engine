import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';

export const readinessUrl = 'http://localhost:8080/actuator/health/readiness';

export const server = setupServer(
  http.get(readinessUrl, () => HttpResponse.json({ status: 'UP' })),
);
