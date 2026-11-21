import { cleanup, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { connectChatSocket, disconnectChatSocket, subscribeToChatEvents } from '../src/api/chatSocket';
import { useChatEvents, useChatListEvents } from '../src/hooks/useChatEvents';
import type { ChatEvent } from '../src/types/chat';

interface FakeSubscription {
  destination: string;
  callback: (message: { body: string }) => void;
  unsubscribe: ReturnType<typeof vi.fn>;
}

interface FakeConfig {
  brokerURL: string;
  reconnectDelay: number;
  beforeConnect: (client: FakeClient) => void;
  onConnect: () => void;
}

interface FakeClient {
  config: FakeConfig;
  connected: boolean;
  connectHeaders: Record<string, string>;
  subscriptions: FakeSubscription[];
  activate: ReturnType<typeof vi.fn>;
  deactivate: ReturnType<typeof vi.fn>;
}

// stompjs replaced by a fake, so connecting and reconnecting happen when the test says
const stomp = vi.hoisted(() => ({ clients: [] as FakeClient[] }));

vi.mock('@stomp/stompjs', () => ({
  Client: class {
    connected = false;
    connectHeaders: Record<string, string> = {};
    subscriptions: FakeSubscription[] = [];
    activate = vi.fn();
    deactivate = vi.fn(async () => { this.connected = false; });

    constructor(public config: FakeConfig) {
      stomp.clients.push(this as unknown as FakeClient);
    }

    subscribe(destination: string, callback: (message: { body: string }) => void) {
      const subscription = { destination, callback, unsubscribe: vi.fn() };
      this.subscriptions.push(subscription);
      return subscription;
    }
  },
}));

// the socket keeps its connection and listeners in the module, so each test leaves it empty
const unsubscribes: (() => void)[] = [];

afterEach(async () => {
  cleanup();
  unsubscribes.splice(0).forEach(unsubscribe => unsubscribe());
  await disconnectChatSocket();
  stomp.clients.length = 0;
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
});

function subscribe(destination: string, onEvent: (event: ChatEvent) => void = () => {}) {
  const unsubscribe = subscribeToChatEvents(destination, onEvent);
  unsubscribes.push(unsubscribe);
  return unsubscribe;
}

function connect(client: FakeClient) {
  client.config.beforeConnect(client);
  client.connected = true;
  client.config.onConnect();
}

function connectedClient(): FakeClient {
  connectChatSocket();
  const client = stomp.clients[0];
  connect(client);
  return client;
}

function push(subscription: FakeSubscription, event: ChatEvent) {
  subscription.callback({ body: JSON.stringify(event) });
}

describe('chat socket', () => {
  it('opens one connection to /api/ws on the page origin, with automatic reconnects', () => {
    connectChatSocket();
    connectChatSocket();

    expect(stomp.clients).toHaveLength(1);
    expect(stomp.clients[0].config.brokerURL).toBe(`ws://${window.location.host}/api/ws`);
    expect(stomp.clients[0].config.reconnectDelay).toBeGreaterThan(0);
    expect(stomp.clients[0].activate).toHaveBeenCalledOnce();
  });

  it('sends the csrf cookie on every connect', () => {
    document.cookie = 'XSRF-TOKEN=first';
    const client = connectedClient();
    expect(client.connectHeaders).toEqual({ 'X-XSRF-TOKEN': 'first' });

    document.cookie = 'XSRF-TOKEN=second';
    connect(client);
    expect(client.connectHeaders).toEqual({ 'X-XSRF-TOKEN': 'second' });
  });

  it('subscribes once connected when asked before, and passes on the parsed events', () => {
    const received: ChatEvent[] = [];
    subscribe('/topic/chats/5', event => received.push(event));
    connectChatSocket();
    const client = stomp.clients[0];
    expect(client.subscriptions).toHaveLength(0);

    connect(client);
    push(client.subscriptions[0], { type: 'MESSAGE_CREATED', payload: { text: 'hello' } });

    expect(client.subscriptions[0].destination).toBe('/topic/chats/5');
    expect(received).toEqual([{ type: 'MESSAGE_CREATED', payload: { text: 'hello' } }]);
  });

  it('subscribes straight away when already connected, and again after a reconnect', () => {
    const client = connectedClient();

    subscribe('/user/queue/chats');
    expect(client.subscriptions.map(s => s.destination)).toEqual(['/user/queue/chats']);

    connect(client);
    expect(client.subscriptions.map(s => s.destination)).toEqual(['/user/queue/chats', '/user/queue/chats']);
  });

  it('stops listening after unsubscribing, also after a reconnect', () => {
    const client = connectedClient();
    const unsubscribe = subscribe('/topic/chats/5');

    unsubscribe();
    connect(client);

    expect(client.subscriptions[0].unsubscribe).toHaveBeenCalledOnce();
    expect(client.subscriptions).toHaveLength(1);
  });

  it('closes the connection on logout and opens a new one on the next login', async () => {
    connectChatSocket();
    const first = stomp.clients[0];

    await disconnectChatSocket();
    connectChatSocket();

    expect(first.deactivate).toHaveBeenCalledOnce();
    expect(stomp.clients).toHaveLength(2);
  });
});

describe('chat event hooks', () => {
  it('listens to a chat while mounted, with the latest handler, and stops on unmount', () => {
    const client = connectedClient();
    const first = vi.fn();
    const second = vi.fn();

    const { rerender, unmount } = renderHook(({ onEvent }) => useChatEvents(5, onEvent),
      { initialProps: { onEvent: first } });
    rerender({ onEvent: second });
    push(client.subscriptions[0], { type: 'MESSAGE_DELETED', payload: 1 });
    unmount();

    expect(client.subscriptions).toHaveLength(1);
    expect(client.subscriptions[0].destination).toBe('/topic/chats/5');
    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledWith({ type: 'MESSAGE_DELETED', payload: 1 });
    expect(client.subscriptions[0].unsubscribe).toHaveBeenCalledOnce();
  });

  it('listens to the user chat list', () => {
    const client = connectedClient();

    renderHook(() => useChatListEvents(() => {}));

    expect(client.subscriptions[0].destination).toBe('/user/queue/chats');
  });
});
