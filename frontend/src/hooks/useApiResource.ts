import { useCallback, useEffect, useState } from 'react';
import { extractErrorMessage } from '../api/client';

interface ApiResource<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  reload: () => Promise<void>;
}

/**
 * Charge une ressource distante et expose son etat de chargement.
 * Factorise le triptyque data / loading / error repete sur chaque page.
 */
export function useApiResource<T>(loader: () => Promise<T>, deps: unknown[] = []): ApiResource<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Le chargeur est recree a chaque rendu par l'appelant : les dependances
  // explicites determinent seules quand relancer la requete.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const stableLoader = useCallback(loader, deps);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await stableLoader());
    } catch (caught) {
      setError(extractErrorMessage(caught));
    } finally {
      setLoading(false);
    }
  }, [stableLoader]);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { data, loading, error, reload };
}
