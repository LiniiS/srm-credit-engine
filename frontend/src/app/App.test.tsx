import { render, screen, waitFor } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';

import { expectNoAccessibilityViolations } from '../test/accessibility';
import { server, readinessUrl } from '../test/testServer';
import { App } from './App';

describe('App', () => {
  it('informa quando a API está disponível', async () => {
    const { container } = render(<App />);

    expect(screen.getByRole('status')).toHaveTextContent('Verificando disponibilidade da API');
    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent('API disponível');
    });
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('SRM Credit Engine');
    await expectNoAccessibilityViolations(container);
  });

  it('mantém o estado de carregamento acessível', async () => {
    server.use(
      http.get(readinessUrl, async () => {
        await delay('infinite');
        return HttpResponse.json({ status: 'UP' });
      }),
    );

    const { container } = render(<App />);

    expect(screen.getByRole('main')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Estado do ambiente' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveAccessibleName('');
    expect(screen.getByRole('status')).toHaveTextContent('Verificando disponibilidade da API');
    await expectNoAccessibilityViolations(container);
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
