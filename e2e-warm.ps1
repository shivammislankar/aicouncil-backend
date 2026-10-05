$BASE = "https://aicouncil-backend.onrender.com"
$WEBKEY = "AIzaSyDerXbBWG_RAWo9bjpdvDMyGzUjHxwsM1c"
$req = Join-Path $env:TEMP "warm-req.json"
$resp = Join-Path $env:TEMP "warm-resp.json"

$email = "warm-" + [guid]::NewGuid().ToString("N").Substring(0,8) + "@example.com"
[IO.File]::WriteAllText($req, (@{ email=$email; password="E2eTest!12345"; returnSecureToken=$true } | ConvertTo-Json -Compress))
& curl.exe -sS -m 60 -o $resp "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$WEBKEY" -H "Content-Type: application/json" --data "@$req" | Out-Null
$u = Get-Content -Raw $resp | ConvertFrom-Json
if (-not $u.idToken) { "signup failed"; exit 1 }

[IO.File]::WriteAllText($req, (@{ question = "hi" } | ConvertTo-Json -Compress))
foreach ($i in 1..3) {
    $m = & curl.exe -sS -m 180 -o $resp -w "%{http_code} %{time_total}" -X POST "$BASE/api/council/ask" -H "Content-Type: application/json" -H "Authorization: Bearer $($u.idToken)" --data "@$req"
    "greeting #$i -> $m"
}

[IO.File]::WriteAllText($req, (@{ idToken = $u.idToken } | ConvertTo-Json -Compress))
$d = & curl.exe -sS -m 60 -o NUL -w "%{http_code}" -X POST "https://identitytoolkit.googleapis.com/v1/accounts:delete?key=$WEBKEY" -H "Content-Type: application/json" --data "@$req"
"cleanup=$d"
