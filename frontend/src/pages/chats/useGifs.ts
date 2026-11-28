import { useCallback, useEffect, useRef, useState } from 'react';
import { GiphyError, gifsByIds, MAX_GIF_IDS, searchGifs, trendingGifs, type GifData, type GiphyProblem } from '../../api/giphy';

// how long typing has to stop before searching, so each letter isn't a call
export const GIF_SEARCH_DELAY_MS = 400;

export type GifSearch = { status: 'loading' } | { status: 'ready'; gifs: GifData[] } | { status: 'failed'; problem: GiphyProblem };

const problemOf = (error: unknown): GiphyProblem => error instanceof GiphyError ? error.problem : 'down';

// the panel's GIFs: trending with no text, otherwise what's typed once typing stops. It lives as long as the panel is
// open, so opening it again starts from trending. Only the answer to the latest search counts, and a failed one
// stays failed until Try again
export function useGifSearch() {
  const [query, setQuery] = useState('');
  const [attempt, setAttempt] = useState(0);
  const wanted = query.trim();
  const request = `${attempt}:${wanted}`;
  const [answer, setAnswer] = useState<{ request: string; result: GifSearch }>();

  useEffect(() => {
    let current = true;
    const timer = setTimeout(() => {
      (wanted ? searchGifs(wanted) : trendingGifs()).then(
        gifs => current && setAnswer({ request, result: { status: 'ready', gifs } }),
        error => current && setAnswer({ request, result: { status: 'failed', problem: problemOf(error) } }),
      );
    }, wanted ? GIF_SEARCH_DELAY_MS : 0);
    return () => {
      current = false;
      clearTimeout(timer);
    };
  }, [request, wanted]);

  // until the latest search answers, the grid shows grey boxes
  const result: GifSearch = answer?.request === request ? answer.result : { status: 'loading' };
  const retry = useCallback(() => setAttempt(count => count + 1), []);
  return { query, setQuery, result, retry };
}

// the GIFs of the messages on screen, asked for together. Each id is asked once while the chat is open: one GIPHY
// doesn't have, or that a failed call was for, is null until the chat is opened again
export function useMessageGifs(ids: readonly string[]) {
  const [gifs, setGifs] = useState<ReadonlyMap<string, GifData | null>>(new Map());
  // the ones on their way, so a new message doesn't ask for them again
  const asked = useRef(new Set<string>());
  const key = [...new Set(ids)].filter(id => !gifs.has(id)).join(',');

  useEffect(() => {
    // a second run of the same effect finds them already asked
    const batch = key.split(',').filter(id => id && !asked.current.has(id));
    if (!batch.length) return;
    batch.forEach(id => asked.current.add(id));
    const chunks = Array.from({ length: Math.ceil(batch.length / MAX_GIF_IDS) }, (_, index) => batch.slice(index * MAX_GIF_IDS, (index + 1) * MAX_GIF_IDS));
    chunks.forEach(chunk => {
      const settle = (found: GifData[]) => setGifs(current => {
        const next = new Map(current);
        chunk.forEach(id => next.set(id, found.find(gif => gif.id === id) ?? null));
        return next;
      });
      gifsByIds(chunk).then(settle, () => settle([]));
    });
  }, [key]);

  // a GIF picked in the panel is already known
  const remember = useCallback((gif: GifData) => {
    asked.current.add(gif.id);
    setGifs(current => new Map(current).set(gif.id, gif));
  }, []);

  return { gifs, remember };
}
