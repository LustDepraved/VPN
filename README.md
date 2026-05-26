# FreeVPN Production-Ready Free Stack

## 1) Backend run
```bash
python -m venv .venv
source .venv/bin/activate
pip install -r apps/control-plane/requirements.txt pytest
make test-control
make run-control
```

## 2) Self-hosted free WireGuard node
```bash
bash scripts/setup_wireguard_node.sh
export VPN_ENDPOINT_HOST=$(curl -4 -s ifconfig.me)
export VPN_SERVER_PUBLIC_KEY=$(sudo cat /etc/wireguard/server_public.key)
make run-control
```

## 3) Provision peer config
```bash
curl -s -X POST http://127.0.0.1:8000/v1/provision \
  -H 'content-type: application/json' \
  -d '{"region_code":"local-lan-1","device_name":"android-phone"}' | jq
```

## 4) Android build
```bash
cd apps/mobile-android
./gradlew assembleDebug
```
