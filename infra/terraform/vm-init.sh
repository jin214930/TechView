#!/usr/bin/env bash

set -Eeuo pipefail

export DEBIAN_FRONTEND=noninteractive

install -d -m 0750 /var/log/techview
exec > >(tee -a /var/log/techview/vm-init.log) 2>&1

echo "[techview] installing operating system packages"
apt-get update
apt-get install -y --no-install-recommends \
  ca-certificates \
  certbot \
  curl \
  docker-compose-v2 \
  docker.io \
  fail2ban \
  iptables-persistent \
  jq \
  nginx \
  python3-certbot-nginx

echo "[techview] allowing public HTTP and HTTPS traffic"
IPTABLES_RULES_V4="/etc/iptables/rules.v4"

if ! grep -q '^-A INPUT -j REJECT' "$IPTABLES_RULES_V4"; then
  echo "[techview] expected OCI INPUT reject rule was not found" >&2
  exit 1
fi

ensure_tcp_input_rule() {
  local port="$1"
  local rule="-A INPUT -p tcp -m state --state NEW -m tcp --dport ${port} -j ACCEPT"

  if grep -Fqx -- "$rule" "$IPTABLES_RULES_V4"; then
    return
  fi

  sed -i "/^-A INPUT -j REJECT/i ${rule}" "$IPTABLES_RULES_V4"
}

ensure_tcp_input_rule 80
ensure_tcp_input_rule 443

/usr/sbin/iptables-restore < "$IPTABLES_RULES_V4"
systemctl enable --now netfilter-persistent

echo "[techview] enabling Docker"
systemctl enable --now docker
systemctl enable --now nginx

if id ubuntu >/dev/null 2>&1; then
  usermod -aG docker ubuntu
fi

echo "[techview] preparing deployment directories"
install -d -o ubuntu -g ubuntu -m 0750 /opt/techview
install -d -o ubuntu -g ubuntu -m 0750 /opt/techview/deploy

echo "[techview] configuring fail2ban for SSH"
install -d -m 0755 /etc/fail2ban/jail.d
cat > /etc/fail2ban/jail.d/sshd.local <<'EOF'
[sshd]
enabled = true
backend = systemd
port = ssh
maxretry = 5
findtime = 10m
bantime = 1h
EOF

systemctl enable --now fail2ban
systemctl restart fail2ban

echo "[techview] applying SSH hardening"
install -d -m 0755 /etc/ssh/sshd_config.d
cat > /etc/ssh/sshd_config.d/99-techview-hardening.conf <<'EOF'
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
EOF

/usr/sbin/sshd -t
systemctl reload ssh

echo "[techview] VM initialization completed"
