import { useEffect, useState } from 'react';

import { checkApiReadiness } from '../shared/api/health';

export type ApiReadiness = 'loading' | 'available' | 'unavailable';

export function useApiReadiness(): ApiReadiness {
  const [readiness, setReadiness] = useState<ApiReadiness>('loading');

  useEffect(() => {
    const controller = new AbortController();

    void checkApiReadiness(controller.signal)
      .then(() => {
        setReadiness('available');
      })
      .catch((error: unknown) => {
        if (!(error instanceof DOMException && error.name === 'AbortError')) {
          setReadiness('unavailable');
        }
      });

    return () => {
      controller.abort();
    };
  }, []);

  return readiness;
}
