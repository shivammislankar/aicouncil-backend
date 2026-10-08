$BASE = "https://aicouncil-backend.onrender.com"
$WEBKEY = "AIzaSyDerXbBWG_RAWo9bjpdvDMyGzUjHxwsM1c"
$req = Join-Path $env:TEMP "rl-req.json"
$resp = Join-Path $env:TEMP "rl-resp.json"

Write-Host "=== health + HTTPS (checklist item 9) ==="
& curl.exe -sS -m 45 -o $resp -w "health=%{http_code}`n" "$BASE/health"
Get-Content -Raw $resp
$h = & curl.exe -sS -m 30 -o NUL -w "%{http_code}|%{redirect_url}" "http://aicouncil-backend.onrender.com/health"
"plain http -> $($h -split '\|' | Select-Object -Skip 1) (code $(($h -split '\|')[0]))"

Write-Host ""
Write-Host "=== rate limit test ==="
$email = "rl-" + [guid]::NewGuid().ToString("N").Substring(0, 8) + "@example.com"
[IO.File]::WriteAllText($req, (@{ email = $email; password = "E2eTest!12345"; returnSecureToken = $true } | ConvertTo-Json -Compress))
& curl.exe -sS -m 60 -o $resp "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$WEBKEY" -H "Content-Type: application/json" --data "@$req" | Out-Null
$u = Get-Content -Raw $resp | ConvertFrom-Json
if (-not $u.idToken) { "signup failed"; exit 1 }

[IO.File]::WriteAllText($req, (@{ question = "hi" } | ConvertTo-Json -Compress))

$first429 = 0
for ($i = 1; $i -le 20; $i++) {
  $out = & curl.exe -sS -m 60 -o $resp -D "$env:TEMP\rl-hdr.txt" -w "%{http_code}" -X POST "$BASE/api/council/ask" `
          -H "Content-Type: application/json" -H "Authorization: Bearer $($u.idToken)" --data "@$req"
  if ($out -eq "429" -and $first429 -eq 0) {
    $first429 = $i
    $retry = (Select-String -Path "$env:TEMP\rl-hdr.txt" -Pattern "^retry-after:" -CaseSensitive:$false)
    $body = Get-Content -Raw $resp
    "req #$i -> 429 (first block at request $i)"
    "Retry-After header: $(if ($retry) { ($retry.Line -split ':')[1].Trim() } else { 'MISSING' })"
    "body: $($body.Substring(0, [Math]::Min(160, $body.Length)))"
  } elseif ($first429 -eq 0) {
    "req #$i -> $out"
  }
}
if ($first429 -eq 0) {
  "FAIL: no 429 after 20 requests - rate limiter not deployed"
} else {
  "PASS: limiter active, blocked starting at request #$first429"
}

[IO.File]::WriteAllText($req, (@{ idToken = $u.idToken } | ConvertTo-Json -Compress))
& curl.exe -sS -m 60 -o NUL -X POST "https://identitytoolkit.googleapis.com/v1/accounts:delete?key=$WEBKEY" -H "Content-Type: application/json" --data "@$req" | Out-Null
"cleanup done"
