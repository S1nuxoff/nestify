# Nestify

Main monorepo for the active Nestify stack.

## Apps

- `backend/` — FastAPI backend, TV/device APIs, streaming and watch progress
- `web/` — main web frontend built with React + Vite
- `tv/` — TV-optimized frontend plus Android TV shell with WebView + native ExoPlayer
- `jacred/` — tracker parsing service used by the platform

## Local-only legacy code

Legacy apps are kept only on the local machine under `legacy/` and are ignored by git.

## Deploy targets

- `web` -> `nestify.club`
- `tv` -> `tv.nestify.club`
- `backend` -> `api.nestify.club`
