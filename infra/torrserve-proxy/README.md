# TorrServe Proxy

This folder contains a drop-in Dokploy compose setup for putting `nginx` in front of `TorrServe`.

It solves the browser-side problems with direct `.m3u` playback by adding:

- CORS headers
- exposed range headers
- no `Content-Disposition: attachment`

## Files

- `docker-compose.yml` — runs `torrserve` and `streams-proxy`
- `nginx.conf` — proxy config with CORS headers

## Dokploy steps

1. Open the existing raw compose app that currently runs TorrServe.
2. Replace its compose content with `docker-compose.yml` from this folder.
3. Make sure `nginx.conf` from this folder is available next to the compose file in the same Dokploy app context.
4. Redeploy the app.
5. Keep `streams.nestify.club` attached to this compose app.

## Expected result

After redeploy this command should show CORS headers:

```bash
curl -I "https://streams.nestify.club/stream/test.m3u?link=test&m3u"
```

You should see:

```text
Access-Control-Allow-Origin: *
```
