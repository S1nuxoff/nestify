<p align="center">
  <img src="./web/src/assets/icons/logo.svg" alt="Nestify" width="120" />
</p>

<h1 align="center">Nestify</h1>

**Nestify** is a monorepo for the active platform stack: API, web client, TV client, and source aggregation service.

---

## Structure

```text
backend/   — FastAPI API, streaming, auth, device flows, websocket
web/       — React + Vite client (main UI)
tv/        — TV UI + Android TV shell (WebView + ExoPlayer)
jacred/    — tracker parsing and source aggregation
```

---

## Components

### backend

- API
- authentication and profiles
- TV pairing (QR login)
- playback control
- streaming endpoints
- WebSocket (live / now playing)

### web

- browser client
- content browsing
- profile management
- API integration

### tv

- TV interface
- Android shell
- player integration (ExoPlayer)

### jacred

- torrent search and aggregation
- tracker integration
- source preparation for streaming

---

## Technologies

- Backend: FastAPI
- Frontend: React + Vite
- TV player: ExoPlayer
- Streaming: TorrServer
- Trackers: Jackett

---

## Infrastructure

Infrastructure notes and deployment details are documented in [docs/infrastructure.md](/Users/vadymshchypanskyi/Desktop/Nestify/docs/infrastructure.md).

---

## Acknowledgements

- [jac.red](https://github.com/YouROK/jac.red)
- [TorrServer](https://github.com/yourok/torrserver)
- [Jackett](https://github.com/Jackett/Jackett)
