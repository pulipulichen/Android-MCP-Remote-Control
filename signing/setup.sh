#!/usr/bin/env bash
set -euo pipefail
umask 077
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIR="$ROOT/signing"
KEY="$DIR/release.keystore"
PROPS="$DIR/signing.properties"
ALIAS="android-mcp-remote-control"
REPO="pulipulichen/Android-MCP-Remote-Control"

usage() {
  cat <<'HELP'
Usage: ./signing/setup.sh [--verify | --sync-github | --generate | --print-base64]

  --verify        Verify the existing permanent signing keystore (default).
  --sync-github   Upload four signing secrets via authenticated GitHub CLI.
  --generate      Generate a NEW signing identity only when none exists.
  --print-base64  Reveal Base64 to manually configure GitHub Secrets (sensitive).

Restore the provided permanent signing backup before using --sync-github.
Never generate another identity for an installed app, and never commit secrets.
HELP
}

MODE="${1:---verify}"
case "$MODE" in
  --verify|--sync-github|--generate|--print-base64) ;;
  -h|--help) usage; exit 0 ;;
  *) usage >&2; exit 2 ;;
esac
if (( $# > 1 )); then usage >&2; exit 2; fi

command -v keytool >/dev/null || { echo 'JDK keytool is required.' >&2; exit 1; }
if [[ "$MODE" == "--generate" ]]; then
  if [[ -e "$KEY" || -e "$PROPS" ]]; then
    echo 'Signing material already exists. Refusing to overwrite or rotate the signing identity.' >&2
    exit 1
  fi
  command -v openssl >/dev/null || { echo 'openssl is required to generate a random password.' >&2; exit 1; }
  mkdir -p "$DIR"
  PASS="$(openssl rand -hex 24)"
  trap 'unset PASS' EXIT
  keytool -genkeypair -noprompt -storetype PKCS12 \
    -keystore "$KEY" -storepass "$PASS" -keypass "$PASS" \
    -alias "$ALIAS" -keyalg RSA -keysize 3072 -validity 10000 \
    -dname 'CN=Android MCP Remote Control, OU=Android, O=Pulipuli, C=TW'
  printf 'storePassword=%s\nkeyPassword=%s\nkeyAlias=%s\n' \
    "$PASS" "$PASS" "$ALIAS" > "$PROPS"
  chmod 600 "$KEY" "$PROPS"
  echo 'Created a new permanent identity. BACK UP signing/release.keystore and signing/signing.properties securely.'
  exit 0
fi

if [[ ! -s "$KEY" || ! -s "$PROPS" ]]; then
  echo 'Missing permanent signing files. Restore the signing/ directory from your private backup.' >&2
  echo 'Only if no key has EVER been used, you may explicitly run --generate.' >&2
  exit 1
fi
get_prop() {
  sed -n "s/^$1=//p" "$PROPS" | head -n 1
}
PASS="$(get_prop storePassword)"
KEYPASS="$(get_prop keyPassword)"
EXISTING_ALIAS="$(get_prop keyAlias)"
if [[ -z "$PASS" || -z "$KEYPASS" || "$EXISTING_ALIAS" != "$ALIAS" ]]; then
  echo 'Invalid signing.properties or unexpected key alias. Refusing to rotate key.' >&2
  exit 1
fi
if ! keytool -list -keystore "$KEY" -storepass "$PASS" -alias "$ALIAS" >/dev/null 2>&1; then
  echo 'The keystore, password, or alias is invalid.' >&2
  exit 1
fi
trap 'unset PASS KEYPASS' EXIT
case "$MODE" in
  --verify)
    keytool -list -v -keystore "$KEY" -storepass "$PASS" -alias "$ALIAS" \
      | grep -E 'Alias name:|Entry type:|SHA256:|Valid from:'
    ;;
  --sync-github)
    command -v gh >/dev/null || { echo 'Install GitHub CLI (gh) first.' >&2; exit 1; }
    gh auth status >/dev/null 2>&1 || { echo 'Run gh auth login first.' >&2; exit 1; }
    echo "Uploading permanent signing identity to repository secrets for $REPO..."
    base64 < "$KEY" | tr -d '\n' | gh secret set SIGNING_KEYSTORE_BASE64 --repo "$REPO"
    printf '%s' "$PASS" | gh secret set SIGNING_STORE_PASSWORD --repo "$REPO"
    printf '%s' "$KEYPASS" | gh secret set SIGNING_KEY_PASSWORD --repo "$REPO"
    printf '%s' "$ALIAS" | gh secret set SIGNING_KEY_ALIAS --repo "$REPO"
    echo 'Four repository secrets updated. The permanent signing identity was not printed.'
    ;;
  --print-base64)
    echo 'WARNING: this is private signing material; do not put it in git or chat.' >&2
    printf 'SIGNING_KEYSTORE_BASE64='
    base64 < "$KEY" | tr -d '\n'
    printf '\n'
    ;;
esac
