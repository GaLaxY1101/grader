#!/bin/sh
# Post-start Keycloak configuration that cannot sit in realm-export.json
# (JSON is read only on first import; this runs on every stack start).
#
# Each step is skipped when its env var is empty, so adding one later does not
# force re-imports or manual UI clicks.
#
#   KEYCLOAK_ADMIN_EMAIL    sets email + emailVerified on master 'admin' so SMTP
#                           "Test connection" has a recipient.
#   SMTP_PASSWORD           writes the SMTP password into realm 'university-grader'
#                           (realm JSON intentionally has no plaintext password).
set -eu

: "${KEYCLOAK_URL:=http://keycloak:8080}"
: "${KEYCLOAK_ADMIN_USERNAME:?set KEYCLOAK_ADMIN_USERNAME}"
: "${KEYCLOAK_ADMIN_PASSWORD:?set KEYCLOAK_ADMIN_PASSWORD}"
: "${KEYCLOAK_ADMIN_EMAIL:=}"
: "${SMTP_PASSWORD:=}"
: "${SMTP_REALM:=university-grader}"

KCADM=/opt/keycloak/bin/kcadm.sh

$KCADM config credentials \
  --server "$KEYCLOAK_URL" \
  --realm master \
  --user "$KEYCLOAK_ADMIN_USERNAME" \
  --password "$KEYCLOAK_ADMIN_PASSWORD"

if [ -n "$KEYCLOAK_ADMIN_EMAIL" ]; then
  ID=$($KCADM get users -r master -q "username=$KEYCLOAK_ADMIN_USERNAME" --fields id --format csv --noquotes | head -n1 | tr -d '\r')
  if [ -z "$ID" ]; then
    echo "configure: master user '$KEYCLOAK_ADMIN_USERNAME' not found" >&2
    exit 1
  fi
  $KCADM update "users/$ID" -r master \
    -s "email=$KEYCLOAK_ADMIN_EMAIL" \
    -s 'emailVerified=true'
  echo "configure: master admin '$KEYCLOAK_ADMIN_USERNAME' email set"
else
  echo "configure: KEYCLOAK_ADMIN_EMAIL empty, skipping admin email step"
fi

if [ -n "$SMTP_PASSWORD" ]; then
  # Merge-updates smtpServer; other fields (host, port, user, from...) come
  # from realm-export.json on first import and persist in the DB afterwards.
  $KCADM update "realms/$SMTP_REALM" \
    -s "smtpServer.password=$SMTP_PASSWORD"
  echo "configure: realm '$SMTP_REALM' SMTP password updated"
else
  echo "configure: SMTP_PASSWORD empty, skipping SMTP password step"
fi
