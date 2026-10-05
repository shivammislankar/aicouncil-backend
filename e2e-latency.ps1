$BASE = "https://aicouncil-backend.onrender.com"
$WEBKEY = "AIzaSyDerXbBWG_RAWo9bjpdvDMyGzUjHxwsM1c"
$req = Join-Path $env:TEMP "lat-req.json"
$resp = Join-Path $env:TEMP "lat-resp.json"

$email = "lat-" + [guid]::NewGuid().ToString("N").Substring(0,8) + "@example.com"
[IO.File]::WriteAllText($req, (@{ email=$email; password="E2eTest!12345"; returnSecureToken=$true } | ConvertTo-Json -Compress))
& curl.exe -sS -m 60 -o $resp "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$WEBKEY" -H "Content-Type: application/json" --data "@$req" | Out-Null
$u = Get-Content -Raw $resp | ConvertFrom-Json
if (-not $u.idToken) { "signup failed"; exit 1 }

$questions = @(
  "What is the best way to learn a new programming language?",
  "Should I use a monolith or microservices for a small team?",
  "How can a startup reduce customer churn?"
)
foreach ($q in $questions) {
  [IO.File]::WriteAllText($req, (@{ question = $q } | ConvertTo-Json -Compress))
  $m = & curl.exe -sS -m 300 -o $resp -w "%{http_code} %{time_total}" -X POST "$BASE/api/council/ask" -H "Content-Type: application/json" -H "Authorization: Bearer $($u.idToken)" --data "@$req"
  $conf = "?"
  try { $j = Get-Content -Raw $resp | ConvertFrom-Json; $conf = $j.payload.confidence } catch {}
  "{0,-55} {1}  conf={2}" -f $q, $m, $conf
}

[IO.File]::WriteAllText($req, (@{ idToken = $u.idToken } | ConvertTo-Json -Compress))
& curl.exe -sS -m 60 -o NUL -X POST "https://identitytoolkit.googleapis.com/v1/accounts:delete?key=$WEBKEY" -H "Content-Type: application/json" --data "@$req" | Out-Null
"cleanup done"
