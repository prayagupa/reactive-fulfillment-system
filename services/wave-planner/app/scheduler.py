import os
from apscheduler.schedulers.background import BackgroundScheduler
from app.wave.algorithm import generate_wave

FC_IDS = os.getenv("FC_IDS", "FC-EAST-1").split(",")

_scheduler = BackgroundScheduler()


def _run_waves():
    for fc_id in FC_IDS:
        result = generate_wave(fc_id.strip())
        if result["wave_id"]:
            print(f"[wave-planner] Generated wave={result['wave_id']} "
                  f"orders={result['order_count']} zones={result['zone_count']} fc={fc_id}")


def start_scheduler():
    _scheduler.add_job(_run_waves, "interval", minutes=15, id="wave-gen")
    _scheduler.start()


def stop_scheduler():
    _scheduler.shutdown(wait=False)
