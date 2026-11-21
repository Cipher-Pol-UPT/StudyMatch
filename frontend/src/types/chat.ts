import type { ChatEventType } from './enums';

// what the backend pushes over the websocket, the payload depends on the type
export interface ChatEvent<T = unknown> {
  type: ChatEventType;
  payload: T;
}
