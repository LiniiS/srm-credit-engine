import { afterEach, describe, expect, it, vi } from 'vitest';

import { checkApiReadiness } from './health';

describe('checkApiReadiness', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('interrompe uma requisição pendente quando o timeout expira', async () => {
    vi.useFakeTimers();
    vi.spyOn(AbortSignal, 'timeout').mockImplementation((milliseconds) => {
      const controller = new AbortController();
      setTimeout(() => {
        controller.abort(new DOMException('The operation timed out', 'TimeoutError'));
      }, milliseconds);
      return controller.signal;
    });
    vi.stubGlobal(
      'fetch',
      vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
        return new Promise<Response>((_resolve, reject) => {
          const signal = init?.signal;
          signal?.addEventListener(
            'abort',
            () => {
              reject(new DOMException('The operation timed out', 'TimeoutError'));
            },
            { once: true },
          );
        });
      }),
    );

    const request = expect(checkApiReadiness()).rejects.toMatchObject({ name: 'TimeoutError' });

    await vi.advanceTimersByTimeAsync(5000);
    await request;
  });
});
