import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { StudyMatchProvider } from './studymatch-ui'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <StudyMatchProvider>
      <App />
    </StudyMatchProvider>
  </StrictMode>,
)
