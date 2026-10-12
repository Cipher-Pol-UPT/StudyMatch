# StudyMatch frontend

React 19, TypeScript and Vite, with the complete StudyMatch UI 1.0.0 library
integrated for [issue #86](https://github.com/Cipher-Pol-UPT/StudyMatch/issues/86).

## Development

```sh
npm ci
npm run dev
```

Run `npm run build` for TypeScript validation and a production build, and
`npm run lint` for ESLint validation.

## Using the components

The official package is committed in `vendor/studymatch-ui-1.0.0.tgz` and installed
as a local file dependency. A fresh checkout needs only `npm ci`.

`src/main.tsx` imports the library stylesheet once and wraps the application in
`StudyMatchProvider`, which supplies base styles and toast notifications.
Import components and their TypeScript types directly from the package:

```tsx
import { Button, Card, useToast } from '@studymatch/ui'

export function Example() {
  const toast = useToast()
  return (
    <Card title="StudyMatch">
      <Button onClick={() => toast.success('Ready!')}>Show notification</Button>
    </Card>
  )
}
```

The package includes form controls, navigation, cards, tables, charts, overlays,
achievements, and blocks for authentication, accounts, profiles, personality tests
and StudyRooms. The application currently displays a small interactive preview.
Data-backed blocks still require adapters and backend integration.

- [Component documentation](https://studymatch-ui.vercel.app/)
- [Installation and requirements](https://studymatch-ui.vercel.app/installation)
- [Official downloads](https://studymatch-ui.vercel.app/download)

## Updating the vendored package

Download the new official archive, place it in `vendor/`, then run
`npm install ./vendor/<new-package>.tgz`. Commit the archive, `package.json` and
`package-lock.json` together, and run the build and lint checks.
The package declares the MIT license. Its stylesheet loads Geist fonts from
Google Fonts; system fonts remain available as a fallback.
