import json
import logging
import time
import uuid
import uvicorn
from fastapi import FastAPI
from fastapi import Request
from fastapi_cache import FastAPICache
from fastapi_cache.backends.inmemory import InMemoryBackend
from fastapi.staticfiles import StaticFiles
from app.core.config import settings
from app.api.v1.api import api_router
from app.api.v2.api import api_router_v2
from app.api.v3.api import api_router_v3
from fastapi.middleware.cors import CORSMiddleware
from app.db.base import create_all_tables
from app.websockets import live_session
from app.websockets import player_hub

from fastapi.responses import StreamingResponse
import httpx

logger = logging.getLogger("nestify.backend")
logging.basicConfig(level=logging.INFO, format="%(message)s")

app = FastAPI(
    title=settings.PROJECT_NAME,
    description=settings.DESCRIPTION,
    version=settings.VERSION,
)

app.include_router(api_router, prefix="/api/v1")
app.include_router(api_router_v2, prefix="/api/v2")
app.include_router(api_router_v3, prefix="/api/v3")
app.include_router(live_session.router)
app.include_router(player_hub.router)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # или конкретный список доменов
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.mount("/static", StaticFiles(directory="static"), name="static")


@app.middleware("http")
async def request_logging_middleware(request: Request, call_next):
    request_id = request.headers.get("x-request-id") or str(uuid.uuid4())
    start = time.perf_counter()
    response = None
    error = None

    try:
        response = await call_next(request)
        return response
    except Exception as exc:
        error = exc
        raise
    finally:
        duration_ms = round((time.perf_counter() - start) * 1000, 2)
        client_ip = request.headers.get("x-forwarded-for") or (request.client.host if request.client else "")
        log_payload = {
            "service": "backend",
            "event": "http_request",
            "request_id": request_id,
            "method": request.method,
            "path": request.url.path,
            "query": str(request.url.query or ""),
            "status_code": response.status_code if response is not None else 500,
            "duration_ms": duration_ms,
            "client_ip": client_ip,
        }
        if error is not None:
            log_payload["error"] = repr(error)
        logger.info(json.dumps(log_payload, ensure_ascii=False))
        if response is not None:
            response.headers["x-request-id"] = request_id


@app.get("/health")
async def health():
    return {
        "status": "ok",
        "service": "backend",
        "version": settings.VERSION,
    }


@app.get("/proxy")
async def proxy_video(request: Request, url: str):
    """
    Проксируем MP4/HLS-поток, пробрасывая Range-заголовок и Referer.
    Работает с ReactPlayer / <video>, поддерживает перемотку.
    """
    # какие-то ссылки с voidboost не отдают видео без правильного Referer + UA
    base_headers = {
        "Referer": "https://voidboost.net",
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/123.0.0.0 Safari/537.36"
        ),
    }
    # если браузер прислал Range — пробрасываем
    if rng := request.headers.get("range"):
        base_headers["Range"] = rng

    async def stream_generator():
        # держим клиент и соединение открытыми, пока генерируем чанки
        async with httpx.AsyncClient(follow_redirects=True) as client:
            async with client.stream("GET", url, headers=base_headers) as resp:
                if resp.status_code >= 400:
                    # voidboost может вернуть 403/404
                    raise HTTPException(resp.status_code, "upstream error")

                # копируем важные заголовки для кеша/перемотки
                response_headers = {
                    k: v
                    for k, v in resp.headers.items()
                    if k.lower()
                    in {
                        "content-length",
                        "content-range",
                        "accept-ranges",
                        "content-type",
                    }
                }
                # отправляем их единственный раз – через yield None
                yield response_headers

                async for chunk in resp.aiter_bytes():
                    yield chunk

    gen = stream_generator()

    # «слово-хак»: берём первые yield-данные (заголовки) перед тем,
    # как StreamingResponse начнёт итерацию
    first = await gen.__anext__()  # dict c заголовками
    status = 206 if "Range" in base_headers else 200
    return StreamingResponse(
        gen,  # дальше пойдут сами чанки
        status_code=status,
        headers=first,
        media_type=first.get("Content-Type", "video/mp4"),
    )


@app.on_event("startup")
async def on_startup():
    await create_all_tables()
    print("Database initialized.")
    FastAPICache.init(InMemoryBackend(), prefix="fastapi-cache")
    from app.services.hls_manager import start_cleanup_task
    start_cleanup_task()
    print("HLS cleanup task started.")
    # Load DB settings overrides into memory
    try:
        from sqlalchemy import select as sa_select
        from app.db.session import async_session as _session
        from app.models.app_settings import AppSettings
        async with _session() as session:
            rows = (await session.execute(sa_select(AppSettings))).scalars().all()
        for row in rows:
            if not hasattr(settings, row.key):
                continue
            current = getattr(settings, row.key)
            if isinstance(current, bool):
                setattr(settings, row.key, row.value.lower() in ("true", "1", "yes"))
            elif isinstance(current, int):
                setattr(settings, row.key, int(row.value))
            else:
                setattr(settings, row.key, row.value)
        print(f"Loaded {len(rows)} setting override(s) from DB.")
    except Exception as e:
        print(f"Could not load settings from DB: {e}")


@app.get("/")
async def root():
    return {"message": "Welcome to the API"}


if __name__ == "__main__":
    import os
    import uvicorn

    port = int(os.environ.get("PORT", 8000))
    uvicorn.run("app.main:app", host="0.0.0.0", port=port)
