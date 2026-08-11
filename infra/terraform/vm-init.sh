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
DOCKER_SYSTEMD_DROP_IN_DIR="/etc/systemd/system/docker.service.d"

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

# Docker가 동적 iptables 체인을 만든 뒤 OCI 기본 방화벽 규칙이 복원되면
# DOCKER-FORWARD 체인이 사라진다. 방화벽 복원을 Docker의 필수 선행 조건으로 고정한다.
install -d -m 0755 "$DOCKER_SYSTEMD_DROP_IN_DIR"
cat > "$DOCKER_SYSTEMD_DROP_IN_DIR/10-netfilter-persistent.conf" <<'EOF'
[Unit]
Requires=netfilter-persistent.service
After=netfilter-persistent.service
EOF

/usr/sbin/iptables-restore < "$IPTABLES_RULES_V4"
systemctl enable --now netfilter-persistent
systemctl daemon-reload

echo "[techview] enabling Docker"
systemctl enable docker
# 패키지 설치 중 이미 시작된 Docker를 재시작하여 복원된 방화벽 위에
# Docker 전용 체인을 다시 생성한다.
systemctl restart docker
systemctl enable --now nginx

echo "[techview] verifying Docker bridge networking"
docker network create techview-init-network-check >/dev/null
docker network rm techview-init-network-check >/dev/null

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
