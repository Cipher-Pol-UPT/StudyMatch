// GIPHY is called from the browser, its terms don't let it go through our backend (#213). The key comes from
// VITE_GIPHY_API_KEY and anyone can read it in the browser; a beta key has 100 calls an hour, so nothing here retries
const API = 'https://api.giphy.com/v1/gifs';
const TIMEOUT_MS = 5000;
// what /v1/gifs?ids= takes at once
export const MAX_GIF_IDS = 100;
const PAGE = 24;

export interface GifData {
  id: string;
  title: string;
  // the fixed width rendition, used as GIPHY gives it
  url: string;
  width: number;
  height: number;
}

// every way a call can fail ends in one of these: no key set, no calls left (429), or GIPHY not answering properly
export type GiphyProblem = 'noKey' | 'limit' | 'down';

export class GiphyError extends Error {
  readonly problem: GiphyProblem;
  constructor(problem: GiphyProblem) {
    super(`GIPHY: ${problem}`);
    this.problem = problem;
  }
}

const key = () => (import.meta.env.VITE_GIPHY_API_KEY as string | undefined)?.trim() || undefined;

// one GIF of an answer, or undefined when it isn't what we expect
function gifOf(raw: unknown): GifData | undefined {
  if (typeof raw !== 'object' || raw === null) return undefined;
  const { id, title, images } = raw as { id?: unknown; title?: unknown; images?: { fixed_width?: { url?: unknown; width?: unknown; height?: unknown } } };
  const image = images?.fixed_width;
  const width = Number(image?.width);
  const height = Number(image?.height);
  if (typeof id !== 'string' || !id || typeof image?.url !== 'string' || !image.url || !(width > 0) || !(height > 0)) return undefined;
  return { id, title: typeof title === 'string' ? title : '', url: image.url, width, height };
}

async function call(path: string, params: Record<string, string>): Promise<GifData[]> {
  const apiKey = key();
  if (!apiKey) throw new GiphyError('noKey');
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    let response: Response;
    try {
      response = await fetch(`${API}${path}?${new URLSearchParams({ api_key: apiKey, ...params })}`, { signal: controller.signal });
    } catch {
      // no network, or no answer in time
      throw new GiphyError('down');
    }
    if (response.status === 429) throw new GiphyError('limit');
    if (!response.ok) throw new GiphyError('down');
    const body: unknown = await response.json().catch(() => undefined);
    const data = (body as { data?: unknown } | undefined)?.data;
    // GIPHY can answer 200 with no data
    if (!Array.isArray(data)) throw new GiphyError('down');
    return data.map(gifOf).filter((gif): gif is GifData => gif !== undefined);
  } finally {
    clearTimeout(timer);
  }
}

// the panel opens on the trending ones, and a search replaces them
export const trendingGifs = () => call('/trending', { limit: String(PAGE), rating: 'g' });
export const searchGifs = (query: string) => call('/search', { q: query, limit: String(PAGE), rating: 'g' });
// the GIFs of the messages on screen, up to 100 at once; GIPHY leaves out the ones it doesn't have
export const gifsByIds = (ids: string[]) => call('', { ids: ids.slice(0, MAX_GIF_IDS).join(',') });
