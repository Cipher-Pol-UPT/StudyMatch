import { useContext } from 'react';
import { ToastCtx } from './toastContext';
export function useToast() {
  const context = useContext(ToastCtx);
  if (!context) throw new Error('StudyMatchProvider is required.');
  return context;
}
