from fastapi import FastAPI
from contextlib import asynccontextmanager
from prometheus_fastapi_instrumentator import Instrumentator

from app.scheduler import start_scheduler, stop_scheduler
from app.kafka.consumer import start_consumer
from app.api.routes import router


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Start background tasks on startup; clean up on shutdown."""
    await start_consumer()
    start_scheduler()
    yield
    stop_scheduler()


app = FastAPI(
    title="Wave Planner",
    description="Clusters allocated orders into pick waves; publishes WaveReleased and PickList events.",
    version="0.1.0",
    lifespan=lifespan,
)

Instrumentator().instrument(app).expose(app, endpoint="/metrics")

app.include_router(router, prefix="/api/v1")
