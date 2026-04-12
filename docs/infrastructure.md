# Nestify Infrastructure

This document describes the infrastructure layer of the project and the current deployment model.

## Core Services

### Product Services

- `Backend` — API, authentication, TV device flows, websocket hubs, watch progress, playback dispatch
- `Web` — main browser frontend
- `TV` — TV frontend and Android TV shell
- `Jacred` — torrent search and source aggregation service

### Infrastructure Services

- `Postgres` — primary database
- `TorrServe` — streaming engine for torrent playback
- `Jackett` — tracker source aggregator
- `FlareSolverr` — Cloudflare bypass service for protected sources

## Current Deployment Model

The project is deployed as a set of separate services in Dokploy.

Each service has:

- its own deploy target
- its own build context
- an independent lifecycle
- separate environment configuration

This model works well for a monorepo where `backend`, `web`, `tv`, and `jacred` evolve separately while still remaining parts of one platform.

## TorrServe

Example of the current compose configuration:

```yaml
services:
  torrserve:
    image: ghcr.io/yourok/torrserver:latest
    container_name: torrserve
    restart: unless-stopped
    ports:
      - "8090:8090"
    volumes:
      - torrserve_data:/opt/torrserver/db
    environment:
      - TZ=Europe/Warsaw

volumes:
  torrserve_data:
```

Role in the system:

- creates torrent sessions
- serves playback streams
- is used by the backend as the streaming backend

## Jackett

Example of the current compose configuration:

```yaml
services:
  jackett:
    image: ghcr.io/linuxserver/jackett
    container_name: jackett
    restart: unless-stopped
    environment:
      - PUID=1000
      - PGID=1000
      - TZ=Europe/Warsaw
    volumes:
      - jackett_config:/config
    ports:
      - "9117:9117"

volumes:
  jackett_config:
```

Role in the system:

- aggregates tracker sources
- is used by the `jacred` service

## FlareSolverr

Example of the current compose configuration:

```yaml
services:
  flaresolverr:
    image: ghcr.io/flaresolverr/flaresolverr:latest
    container_name: flaresolverr
    restart: unless-stopped
    ports:
      - "8191:8191"
    environment:
      LOG_LEVEL: info
      LOG_HTML: "false"
      CAPTCHA_SOLVER: none
      TZ: Europe/Warsaw
```

Role in the system:

- helper service for sources protected by Cloudflare
- used where a regular HTTP client is not sufficient

## Practical Notes

- `TorrServe`, `Jackett`, and `FlareSolverr` are infrastructure dependencies, not standalone product applications
- `Jacred` connects part of the source-discovery logic to this infrastructure layer
- `Backend` sits on top of this infrastructure and acts as the main access point for clients
- `Web` and `TV` should not depend on infrastructure details more than necessary
