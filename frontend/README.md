# RetinaVision Web

Vue 3 browser client for RetinaVision. The frontend handles authentication, patient and case workflows, analysis task views, doctor review, knowledge retrieval, and role-aware Agent experiences. All clinical and AI business operations go through the Java API.

## Requirements

- Node.js 20
- npm
- RetinaVision Java API at `http://127.0.0.1:8080`

## Local development

```powershell
npm.cmd ci
npm.cmd run dev
```

Vite listens on `http://127.0.0.1:5173`. Requests under `/api` are proxied to `http://127.0.0.1:8080` by `vite.config.ts`.

Copy `.env.development.example` to a local `.env.development` only when a different browser API base path is required. Local environment files are ignored by Git.

## Verification

```powershell
npm.cmd run type-check
npm.cmd run build
```

The production build is written to the ignored `dist/` directory.

## Security boundary

- The request client in `src/utils/request.ts` is the only place that attaches the Bearer token.
- Browser code must not contain API keys, database credentials, model credentials, or patient-identifying data.
- Agent navigation accepts only backend-controlled application paths; arbitrary URLs are not executed.
- AI output is assistive information. Formal medical conclusions remain subject to doctor review and report signing.
