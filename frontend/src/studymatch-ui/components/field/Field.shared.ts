import type { ReactNode } from 'react';
import './field.css';

export const describedBy = (id: string, hint?: ReactNode, error?: ReactNode) => (hint || error ? `${id}-hint` : undefined);
