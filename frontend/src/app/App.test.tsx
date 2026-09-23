import { render, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';

import { server, readinessUrl } from '../test/testServer';
import { App } from './App';

describe('App', () => {
  it('informa quando a API está disponível', async () => {
    render(<App />);

    expect(screen.getByRole('status')).toHaveTextContent('Verificando disponibilidade da API');
    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent('API disponível');
    });
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('SRM Credit Engine');
  });

  it('informa quando a API responde sem estar pronta', async () => {
    server.use(http.get(readinessUrl, () => HttpResponse.json({ status: 'DOWN' })));

    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent('API indisponível');
    });
  });

  it('informa quando a API responde com erro HTTP', async () => {
    server.use(http.get(readinessUrl, () => new HttpResponse(null, { status: 503 })));

    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent('API indisponível');
    });
  });

  it('informa quando a API não pode ser alcançada', async () => {
    server.use(http.get(readinessUrl, () => HttpResponse.error()));

    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent('API indisponível');
    });
  });
});
