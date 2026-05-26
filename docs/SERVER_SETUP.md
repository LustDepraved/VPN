# Server Setup (Free Self-Hosted)

```bash
bash scripts/setup_wireguard_node.sh
```

Read server public key:
```bash
sudo cat /etc/wireguard/server_public.key
```

Run control-plane with env values:
```bash
export VPN_ENDPOINT_HOST=$(curl -4 -s ifconfig.me)
export VPN_SERVER_PUBLIC_KEY=$(sudo cat /etc/wireguard/server_public.key)
make run-control
```
