import { useCallback, useEffect, useState } from 'react';

type Result<T> = { key: string; data: T; error?: never } | { key: string; data?: never; error: unknown };

export function useRemoteData<T>(key: string, load: () => Promise<T>) {
  const [result, setResult] = useState<Result<T>>();
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let current = true;
    load().then(data => { if (current) setResult({ key, data }); })
      .catch((error: unknown) => { if (current) setResult({ key, error }); });
    return () => { current = false; };
  }, [key, load, attempt]);
  const retry = useCallback(() => { setResult(undefined); setAttempt(value => value + 1); }, []);
  const active = result?.key === key ? result : undefined;
  return { data: active?.data, error: active && 'error' in active ? active.error : undefined, loading: active === undefined, retry };
}
