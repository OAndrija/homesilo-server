#!/usr/bin/env bash
# Seeds a database through the API of the original (pre-hardening) server, the
# way a real install would have been used. Writes e2e-data/seed.json for the tests.
set -euo pipefail
API=${API:-http://localhost:8080/api/v1}
OUT=${OUT:-e2e-data/seed.json}
mkdir -p "$(dirname "$OUT")"
tmp=$(mktemp -d)

register() {
  curl -fsS -X POST "$API/auth/register" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"email\":\"$1@example.com\",\"password\":\"$2\"}" | jq -r .token
}
folder() { # token name parentId|null
  local parent=$3; [ "$parent" = null ] || parent="\"$parent\""
  curl -fsS -X POST "$API/folders" -H "Authorization: Bearer $1" -H 'Content-Type: application/json' \
    -d "{\"name\":\"$2\",\"parentId\":$parent}" | jq -r .id
}
upload() { # token path folderId|""
  local args=(-F "file=@$2")
  [ -n "${3:-}" ] && args+=(-F "folderId=$3")
  curl -fsS -X POST "$API/files/upload" -H "Authorization: Bearer $1" "${args[@]}" | jq -r .id
}

alice=$(register legacy-alice legacy-password-1)
bob=$(register legacy-bob legacy-password-2)

printf 'hello from the old version' > "$tmp/readme.txt"
printf 'draft that was trashed before the upgrade' > "$tmp/old-draft.txt"
printf '# Trip notes\n' > "$tmp/notes.md"
# 1x1 transparent PNG
base64 -d > "$tmp/beach.png" <<<'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII='
printf 'bob private file' > "$tmp/bob.txt"

photos=$(folder "$alice" Photos null)
trip=$(folder "$alice" 2024 "$photos")
readme=$(upload "$alice" "$tmp/readme.txt")
beach=$(upload "$alice" "$tmp/beach.png" "$photos")
notes=$(upload "$alice" "$tmp/notes.md" "$trip")
draft=$(upload "$alice" "$tmp/old-draft.txt")
upload "$bob" "$tmp/bob.txt" >/dev/null

curl -fsS -X PATCH "$API/files/$readme/star" -H "Authorization: Bearer $alice" >/dev/null
curl -fsS -X PATCH "$API/files/$draft/trash" -H "Authorization: Bearer $alice" >/dev/null

jq -n --arg readme "$readme" --arg beach "$beach" --arg notes "$notes" --arg draft "$draft" \
  --arg photos "$photos" --arg trip "$trip" \
  '{files:{readme:$readme,beach:$beach,notes:$notes,draft:$draft},folders:{photos:$photos,trip:$trip}}' > "$OUT"
echo "Seeded legacy data:"; cat "$OUT"
