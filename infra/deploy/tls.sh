#!/usr/bin/env bash

set -Eeuo pipefail

readonly DOMAIN="api.wardtech.xyz"
readonly EXPECTED_IP="168.110.104.124"
readonly CERTBOT_EMAIL="rlawlsaud2149@gmail.com"
readonly WEBROOT="/var/www/certbot"
readonly AVAILABLE_CONFIG="/etc/nginx/sites-available/techview-api.conf"
readonly ENABLED_CONFIG="/etc/nginx/sites-enabled/techview-api.conf"

if [[ "${EUID}" -ne 0 ]]; then
  echo "[techview] run this script with sudo" >&2
  exit 1
fi

if [[ "$#" -ne 1 ]]; then
  echo "usage: sudo bash $0 /path/to/techview-api.conf" >&2
  exit 1
fi

readonly SOURCE_CONFIG="$1"

if [[ ! -f "$SOURCE_CONFIG" ]]; then
  echo "[techview] Nginx source config not found: $SOURCE_CONFIG" >&2
  exit 1
fi

for command in certbot getent nginx systemctl; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "[techview] required command not found: $command" >&2
    exit 1
  fi
done

echo "[techview] verifying DNS for ${DOMAIN}"
if ! getent ahostsv4 "$DOMAIN" | awk '{print $1}' | sort -u | grep -Fqx "$EXPECTED_IP"; then
  echo "[techview] ${DOMAIN} does not resolve to ${EXPECTED_IP}" >&2
  exit 1
fi

readonly BACKUP_DIR="$(mktemp -d /tmp/techview-nginx-backup.XXXXXX)"
BACKUP_AVAILABLE=false
BACKUP_ENABLED=false
ROLLBACK_REQUIRED=true

cleanup_backup() {
  rm -f -- "$BACKUP_DIR/available" "$BACKUP_DIR/enabled"
  rmdir -- "$BACKUP_DIR"
}

backup_nginx_config() {
  if [[ -e "$AVAILABLE_CONFIG" || -L "$AVAILABLE_CONFIG" ]]; then
    cp -a -- "$AVAILABLE_CONFIG" "$BACKUP_DIR/available"
    BACKUP_AVAILABLE=true
  fi

  if [[ -e "$ENABLED_CONFIG" || -L "$ENABLED_CONFIG" ]]; then
    cp -a -- "$ENABLED_CONFIG" "$BACKUP_DIR/enabled"
    BACKUP_ENABLED=true
  fi
}

restore_nginx_config() {
  local exit_code="$?"
  trap - ERR

  if [[ "$ROLLBACK_REQUIRED" == true ]]; then
    echo "[techview] restoring the previous Nginx configuration" >&2
    set +e
    rm -f -- "$AVAILABLE_CONFIG" "$ENABLED_CONFIG"

    if [[ "$BACKUP_AVAILABLE" == true ]]; then
      cp -a -- "$BACKUP_DIR/available" "$AVAILABLE_CONFIG"
    fi

    if [[ "$BACKUP_ENABLED" == true ]]; then
      cp -a -- "$BACKUP_DIR/enabled" "$ENABLED_CONFIG"
    fi

    nginx -t
    systemctl reload nginx
    systemctl is-active --quiet nginx
    set -e
  fi

  cleanup_backup
  exit "$exit_code"
}

trap restore_nginx_config ERR

backup_nginx_config
install -d -m 0755 "$WEBROOT/.well-known/acme-challenge"

echo "[techview] installing temporary HTTP configuration"
cat > "$AVAILABLE_CONFIG" <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name ${DOMAIN};

    location ^~ /.well-known/acme-challenge/ {
        root ${WEBROOT};
        default_type text/plain;
    }

    location / {
        return 404;
    }
}
EOF

ln -sfn "$AVAILABLE_CONFIG" "$ENABLED_CONFIG"
nginx -t
systemctl reload nginx

echo "[techview] requesting or reusing the TLS certificate"
certbot certonly \
  --webroot \
  --webroot-path "$WEBROOT" \
  --domain "$DOMAIN" \
  --cert-name "$DOMAIN" \
  --email "$CERTBOT_EMAIL" \
  --agree-tos \
  --no-eff-email \
  --non-interactive \
  --keep-until-expiring

echo "[techview] installing final HTTPS configuration"
install -m 0644 "$SOURCE_CONFIG" "$AVAILABLE_CONFIG"
ln -sfn "$AVAILABLE_CONFIG" "$ENABLED_CONFIG"
nginx -t
systemctl reload nginx
systemctl is-active --quiet nginx

# 최종 HTTPS 설정이 활성화된 뒤에는 갱신 검증 실패가 정상 서비스를 되돌리지 않게 한다.
ROLLBACK_REQUIRED=false
trap - ERR

systemctl enable --now certbot.timer
if ! certbot renew --dry-run; then
  cleanup_backup
  echo "[techview] certificate renewal dry-run failed" >&2
  exit 1
fi

cleanup_backup
echo "[techview] domain and TLS setup completed"
