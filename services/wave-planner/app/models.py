"""SQLAlchemy ORM models for Wave Planner (PostgreSQL)."""

from datetime import datetime, timezone
from sqlalchemy import Column, String, Integer, DateTime, ForeignKey
from sqlalchemy.orm import declarative_base

Base = declarative_base()


class Wave(Base):
    __tablename__ = "waves"

    wave_id = Column(String, primary_key=True)
    fc_id = Column(String, nullable=False)
    status = Column(String, nullable=False, default="RELEASED")
    order_count = Column(Integer, default=0)
    created_at = Column(DateTime(timezone=True), default=lambda: datetime.now(tz=timezone.utc))


class WaveOrder(Base):
    __tablename__ = "wave_orders"

    order_id = Column(String, primary_key=True)
    fc_id = Column(String, nullable=False)
    wave_id = Column(String, ForeignKey("waves.wave_id"), nullable=True)
    status = Column(String, nullable=False, default="ELIGIBLE")
    zone = Column(String, nullable=True)
    requested_delivery_date = Column(DateTime(timezone=True), nullable=True)
    pick_items = Column(String, nullable=True)   # JSON blob — replace with relationship in v2
    created_at = Column(DateTime(timezone=True), default=lambda: datetime.now(tz=timezone.utc))
