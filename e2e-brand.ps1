$BASE = "https://aicouncil-backend.onrender.com"
$WEBKEY = "AIzaSyDerXbBWG_RAWo9bjpdvDMyGzUjHxwsM1c"
$req = Join-Path $env:TEMP "brand-req.json"
$resp = Join-Path $env:TEMP "brand-resp.json"

$email = "brand-" + [guid]::NewGuid().ToString("N").Substring(0,8) + "@example.com"
[IO.File]::WriteAllText($req, (@{ email=$email; password="E2eTest!12345"; returnSecureToken=$true } | ConvertTo-Json -Compress))
& curl.exe -sS -m 60 -o $resp "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$WEBKEY" -H "Content-Type: application/json" --data "@$req" | Out-Null
$u = Get-Content -Raw $resp | ConvertFrom-Json
if (-not $u.idToken) { "signup failed"; exit 1 }

[IO.File]::WriteAllText($req, (@{ question = "hi" } | ConvertTo-Json -Compress))
& curl.exe -sS -m 180 -o $resp -w "HTTP=%{http_code} time=%{time_total}s" -X POST "$BASE/api/council/ask" -H "Content-Type: application/json" -H "Authorization: Bearer $($u.idToken)" --data "@$req" | Out-Null
$body = Get-Content -Raw $resp
Write-Output "--- finalAnswer ---"
Write-Output ((ConvertFrom-Json $body).payload.finalAnswer)
Write-Output "--- brand check ---"
if ($body -match "AI Council") { "STILL SAYS 'AI Council'" } elseif ($body -match "Veritas") { "OK: contains Veritas" } else { "no brand string found" }

[IO.File]::WriteAllText($req, (@{ idToken = $u.idToken } | ConvertTo-Json -Compress))
& curl.exe -sS -m 60 -o NUL -X POST "https://identitytoolkit.googleapis.com/v1/accounts:delete?key=$WEBKEY" -H "Content-Type: application/json" --data "@$req" | Out-Null
"cleanup done"
