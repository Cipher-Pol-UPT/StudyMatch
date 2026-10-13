# StudyMatch UI 1.0.0 · source

Copy this folder into your project (for example `src/studymatch-ui`) and install the two runtime dependencies:

    npm install framer-motion lucide-react

Then import from it:

    import { StudyMatchProvider, Button } from './studymatch-ui';

`index.ts` imports every stylesheet, so Vite bundles the CSS automatically.
React 18 or 19 is required. All classes use the `sm-` prefix and all tokens the `--sm-` prefix.
