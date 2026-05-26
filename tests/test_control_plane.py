from fastapi.testclient import TestClient

from app import main
from app.main import app

client = TestClient(app)


def test_health() -> None:
    response = client.get("/health")
    assert response.status_code == 200


def test_regions() -> None:
    response = client.get("/v1/regions")
    assert response.status_code == 200
    assert len(response.json()) >= 1


def test_provision_and_get_peer(monkeypatch) -> None:
    keys = iter(["privkey", "pubkey"])

    def fake_check_output(*args, **kwargs):
        return next(keys)

    monkeypatch.setattr(main.subprocess, "check_output", fake_check_output)
    response = client.post("/v1/provision", json={"region_code": "local-lan-1", "device_name": "pixel"})
    assert response.status_code == 200
    cfg = response.json()
    assert cfg["peer_public_key"] is not None
    peer_id = cfg["peer_id"]

    response2 = client.get(f"/v1/peers/{peer_id}")
    assert response2.status_code == 200
    assert response2.json()["peer_id"] == peer_id
