import { createContext } from 'react';
import type { ToastFn } from './Toast';
export const ToastCtx = createContext<ToastFn | null>(null);
