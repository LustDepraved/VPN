import base64
import ipaddress
import os
import sqlite3
import subprocess
import threading
import uuid
from enum import Enum
from pathlib import Path

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel

app = FastAPI(title="VPN Control Plane", version="1.0.0")
DB_PATH = Path(os.getenv("VPN_DB_PATH", "apps/control-plane/data/freevpn.db"))
DB_PATH.parent.mkdir(parents=True, exist_ok=True)
DB_LOCK = threading.Lock()


class Protocol(str, Enum):
    wireguard = "wireguard"


class Region(BaseModel):
    code: str
    country: str
    city: str
    endpoint_host: str
    endpoint_port: int
    public_key: str
    dns_servers: list[str]
    supports_ipv6: bool
    exit_ip_country: str


class ProvisionRequest(BaseModel):
    region_code: str
    device_name: str


class TunnelConfig(BaseModel):
    peer_id: str
    protocol: Protocol
    region_code: str
    private_key: str
    client_address_v4: str
    client_address_v6: str
    dns_servers: list[str]
    mtu: int
    peer_public_key: str
    peer_endpoint: str
    allowed_ips: list[str]
    keepalive_seconds: int
    exit_ip_country: str


def get_conn() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    with DB_LOCK:
        conn = get_conn()
        cur = conn.cursor()
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS regions (
                code TEXT PRIMARY KEY,
                country TEXT NOT NULL,
                city TEXT NOT NULL,
                endpoint_host TEXT NOT NULL,
                endpoint_port INTEGER NOT NULL,
                public_key TEXT NOT NULL,
                dns_servers TEXT NOT NULL,
                supports_ipv6 INTEGER NOT NULL,
                exit_ip_country TEXT NOT NULL
            );
            """
        )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS peers (
                peer_id TEXT PRIMARY KEY,
                region_code TEXT NOT NULL,
                device_name TEXT NOT NULL,
                private_key TEXT NOT NULL,
                public_key TEXT NOT NULL,
                client_address_v4 TEXT NOT NULL,
                client_address_v6 TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY(region_code) REFERENCES regions(code)
            );
            """
        )
        conn.commit()
        ensure_seed_regions(conn)
        conn.close()


def ensure_seed_regions(conn: sqlite3.Connection) -> None:
    existing = conn.execute("SELECT COUNT(*) as c FROM regions").fetchone()["c"]
    if existing:
        return
    regions = [
        ("local-lan-1", "Local", "LAN", os.getenv("VPN_ENDPOINT_HOST", "192.168.1.10"), 51820, os.getenv("VPN_SERVER_PUBLIC_KEY", ""), "1.1.1.1,8.8.8.8", 1, "LOCAL"),
    ]
    for r in regions:
        conn.execute(
            "INSERT INTO regions(code,country,city,endpoint_host,endpoint_port,public_key,dns_servers,supports_ipv6,exit_ip_country) VALUES(?,?,?,?,?,?,?,?,?)",
            r,
        )
    conn.commit()


def gen_wg_keypair() -> tuple[str, str]:
    try:
        private_key = subprocess.check_output(["wg", "genkey"], text=True).strip()
        public_key = subprocess.check_output(["wg", "pubkey"], text=True, input=private_key + "\n").strip()
        return private_key, public_key
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"wireguard-tools required: {exc}") from exc


init_db()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "db": str(DB_PATH)}


@app.get("/v1/regions", response_model=list[Region])
def list_regions(ipv6: bool | None = Query(default=None)) -> list[Region]:
    conn = get_conn()
    rows = conn.execute("SELECT * FROM regions").fetchall()
    conn.close()
    items = []
    for row in rows:
        region = Region(
            code=row["code"], country=row["country"], city=row["city"], endpoint_host=row["endpoint_host"], endpoint_port=row["endpoint_port"],
            public_key=row["public_key"], dns_servers=row["dns_servers"].split(","), supports_ipv6=bool(row["supports_ipv6"]), exit_ip_country=row["exit_ip_country"]
        )
        if ipv6 is None or region.supports_ipv6 == ipv6:
            items.append(region)
    return items


@app.post("/v1/provision", response_model=TunnelConfig)
def provision_peer(req: ProvisionRequest) -> TunnelConfig:
    conn = get_conn()
    region = conn.execute("SELECT * FROM regions WHERE code = ?", (req.region_code,)).fetchone()
    if not region:
        conn.close()
        raise HTTPException(status_code=404, detail="Region not found")

    private_key, public_key = gen_wg_keypair()
    peer_id = str(uuid.uuid4())

    peer_index = conn.execute("SELECT COUNT(*) as c FROM peers WHERE region_code = ?", (req.region_code,)).fetchone()["c"] + 2
    client_v4 = f"10.66.66.{peer_index}/32"
    client_v6 = f"fd42:42:42::{peer_index}/128"

    conn.execute(
        "INSERT INTO peers(peer_id,region_code,device_name,private_key,public_key,client_address_v4,client_address_v6,created_at) VALUES(?,?,?,?,?,?,?,datetime('now'))",
        (peer_id, req.region_code, req.device_name, private_key, public_key, client_v4, client_v6),
    )
    conn.commit()
    conn.close()

    return TunnelConfig(
        peer_id=peer_id,
        protocol=Protocol.wireguard,
        region_code=req.region_code,
        private_key=private_key,
        client_address_v4=client_v4,
        client_address_v6=client_v6,
        dns_servers=region["dns_servers"].split(","),
        mtu=1280,
        peer_public_key=region["public_key"],
        peer_endpoint=f"{region['endpoint_host']}:{region['endpoint_port']}",
        allowed_ips=["0.0.0.0/0", "::/0"],
        keepalive_seconds=25,
        exit_ip_country=region["exit_ip_country"],
    )


@app.get("/v1/peers/{peer_id}", response_model=TunnelConfig)
def get_peer_config(peer_id: str) -> TunnelConfig:
    conn = get_conn()
    peer = conn.execute("SELECT * FROM peers WHERE peer_id = ?", (peer_id,)).fetchone()
    if not peer:
        conn.close()
        raise HTTPException(status_code=404, detail="Peer not found")
    region = conn.execute("SELECT * FROM regions WHERE code = ?", (peer["region_code"],)).fetchone()
    conn.close()
    return TunnelConfig(
        peer_id=peer_id,
        protocol=Protocol.wireguard,
        region_code=peer["region_code"],
        private_key=peer["private_key"],
        client_address_v4=peer["client_address_v4"],
        client_address_v6=peer["client_address_v6"],
        dns_servers=region["dns_servers"].split(","),
        mtu=1280,
        peer_public_key=region["public_key"],
        peer_endpoint=f"{region['endpoint_host']}:{region['endpoint_port']}",
        allowed_ips=["0.0.0.0/0", "::/0"],
        keepalive_seconds=25,
        exit_ip_country=region["exit_ip_country"],
    )
