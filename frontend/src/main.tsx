import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './i18n';
import './index.css';
import { StudyMatchProvider } from './studymatch-ui';
createRoot(document.getElementById('root')!).render(<StrictMode><StudyMatchProvider><App /></StudyMatchProvider></StrictMode>);

