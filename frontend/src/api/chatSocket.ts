import { Client, type StompSubscription } from '@stomp/stompjs';
import type { ChatEvent } from '../types/chat';
import { readCsrfToken } from './client';

const RECONNECT_DELAY_MS = 5000;

interface Listener {
  destination: string;
  onEvent: (event: ChatEvent) => void;
  subscription?: StompSubscription;
}

let client: Client | null = null;
const listeners = new Set<Listener>();

// one connection for the whole app, opened after login. stompjs reconnects by itself
export function connectChatSocket(): void {
  if (client !== null) {
    return;
  }
  client = new Client({
    brokerURL: socketUrl(),
    reconnectDelay: RECONNECT_DELAY_MS,
    // read on every connect, the token can change after login
    beforeConnect: connecting => {
      connecting.connectHeaders = { 'X-XSRF-TOKEN': readCsrfToken() ?? '' };
    },
    // a reconnect is a new session on the server, so every subscription is made again
    onConnect: () => listeners.forEach(listen),
  });
  client.activate();
}

export async function disconnectChatSocket(): Promise<void> {
  const closing = client;
  client = null;
  listeners.forEach(listener => { listener.subscription = undefined; });
  await closing?.deactivate();
}

// returns the unsubscribe. works before the connection is up too, it subscribes once it is
export function subscribeToChatEvents(destination: string, onEvent: (event: ChatEvent) => void): () => void {
  const listener: Listener = { destination, onEvent };
  listeners.add(listener);
  if (client?.connected) {
    listen(listener);
  }
  return () => {
    listeners.delete(listener);
    if (client?.connected) {
      listener.subscription?.unsubscribe();
    }
  };
}

function listen(listener: Listener) {
  listener.subscription = client?.subscribe(listener.destination, message => {
    listener.onEvent(JSON.parse(message.body) as ChatEvent);
  });
}

// same origin as the page, through the /api proxy like every other call
function socketUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${protocol}://${window.location.host}/api/ws`;
}
