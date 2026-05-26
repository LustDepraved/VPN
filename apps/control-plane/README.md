# Control Plane API (MVP)

## Endpoints
- `GET /health`
- `GET /v1/regions`
- `GET /v1/regions/{code}`
- `GET /v1/recommendation`
- `GET /v1/session/stats`

## Regions filtering
`GET /v1/regions` supports optional query params:
- `use_case=gaming|streaming|torrent`
- `protocol=wireguard|openvpn|shadowsocks|hysteria2|tuic`
- `ipv6=true|false`

## Run
```bash
uvicorn app.main:app --reload --port 8000
```
