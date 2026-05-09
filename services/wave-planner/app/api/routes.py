from fastapi import APIRouter
from app.wave.algorithm import generate_wave
from app.db.postgres import get_session
from app.models import Wave, WaveOrder

router = APIRouter()


@router.get("/waves/{fc_id}")
def list_waves(fc_id: str):
    with get_session() as session:
        waves = session.query(Wave).filter(Wave.fc_id == fc_id).order_by(Wave.created_at.desc()).limit(20).all()
    return [{"waveId": w.wave_id, "fcId": w.fc_id, "status": w.status, "orderCount": w.order_count} for w in waves]


@router.post("/waves/{fc_id}/trigger")
def trigger_wave(fc_id: str):
    """Manual trigger for testing / ops."""
    result = generate_wave(fc_id)
    return result


@router.get("/picklists/{wave_id}")
def get_picklists(wave_id: str):
    with get_session() as session:
        orders = session.query(WaveOrder).filter(WaveOrder.wave_id == wave_id).all()
    return [{"orderId": o.order_id, "status": o.status} for o in orders]
