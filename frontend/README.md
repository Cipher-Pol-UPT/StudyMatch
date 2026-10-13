# StudyMatch frontend

React, TypeScript and Vite. Shared components are in `src/studymatch-ui`.

```sh
npm ci
npm run dev
```

```tsx
import { Button } from './studymatch-ui';
import { useTranslation } from 'react-i18next';

export function SaveButton({ onSave }: { onSave: () => void }) {
  const { t } = useTranslation();
  return <Button onClick={onSave}>{t('common.save')}</Button>;
}
```

Components receive data and callbacks; the backend owns business rules.
Use the demo language selector to switch between English and Portuguese.
Run `npm run build`, `npm run lint` and `npm test` to validate the frontend.
