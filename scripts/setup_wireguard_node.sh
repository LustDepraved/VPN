#!/usr/bin/env bash
set -euo pipefail

WG_IFACE=${WG_IFACE:-wg0}
WG_PORT=${WG_PORT:-51820}
WG_NET4=${WG_NET4:-10.66.66.1/24}
WG_NET6=${WG_NET6:-fd42:42:42::1/64}

sudo apt-get update
sudo apt-get install -y wireguard wireguard-tools nftables qrencode

sudo mkdir -p /etc/wireguard
umask 077
if [ ! -f /etc/wireguard/server_private.key ]; then
  wg genkey | sudo tee /etc/wireguard/server_private.key >/dev/null
fi
sudo cat /etc/wireguard/server_private.key | wg pubkey | sudo tee /etc/wireguard/server_public.key >/dev/null

SERVER_PRIV=$(sudo cat /etc/wireguard/server_private.key)

sudo tee /etc/wireguard/${WG_IFACE}.conf >/dev/null <<CONF
[Interface]
Address = ${WG_NET4}, ${WG_NET6}
ListenPort = ${WG_PORT}
PrivateKey = ${SERVER_PRIV}
SaveConfig = true
PostUp = sysctl -w net.ipv4.ip_forward=1; sysctl -w net.ipv6.conf.all.forwarding=1; nft add table ip nat; nft 'add chain ip nat postrouting { type nat hook postrouting priority 100 ; }'; nft add rule ip nat postrouting oifname "eth0" masquerade
PostDown = nft delete table ip nat
CONF

sudo systemctl enable wg-quick@${WG_IFACE}
sudo systemctl restart wg-quick@${WG_IFACE}

echo "Server public key:" 
sudo cat /etc/wireguard/server_public.key
