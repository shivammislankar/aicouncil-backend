$BASE = "https://aicouncil-backend.onrender.com"
$WEBKEY = "AIzaSyDerXbBWG_RAWo9bjpdvDMyGzUjHxwsM1c"
$req = Join-Path $env:TEMP "gr-req.json"
$resp = Join-Path $env:TEMP "gr-resp.json"

$email = "gr-" + [guid]::NewGuid().ToString("N").Substring(0,8) + "@example.com"
[IO.File]::WriteAllText($req, (@{ email=$email; password="E2eTest!12345"; returnSecureToken=$true } | ConvertTo-Json -Compress))
& curl.exe -sS -m 60 -o $resp "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$WEBKEY" -H "Content-Type: application/json" --data "@$req" | Out-Null
$u = Get-Content -Raw $resp | ConvertFrom-Json
if (-not $u.idToken) { "signup failed"; exit 1 }

function Ask($q) {
    [IO.File]::WriteAllText($req, (@{ question = $q } | ConvertTo-Json -Compress))
    $m = & curl.exe -sS -m 180 -o $resp -w "%{http_code} %{time_total}" -X POST "$BASE/api/council/ask" -H "Content-Type: application/json" -H "Authorization: Bearer $($u.idToken)" --data "@$req"
    $j = Get-Content -Raw $resp | ConvertFrom-Json
    $g = if ($j.payload.PSObject.Properties.Name -contains 'greeting') { $j.payload.greeting } else { "FIELD ABSENT" }
    "{0,-34} code/time={1,-18} greeting={2}" -f $q, $m, $g
}

"--- expected greeting=true (instant, no LLM) ---"
foreach ($q in @("hi","hello","good","how are you doing","Hi!","thanks","ok","bye","hello everyone","good morning team")) { Ask $q }

"--- expected greeting=false (real council) ---"
Ask "What are the tradeoffs between SQL and NoSQL databases?"

[IO.File]::WriteAllText($req, (@{ idToken = $u.idToken } | ConvertTo-Json -Compress))
& curl.exe -sS -m 60 -o NUL -X POST "https://identitytoolkit.googleapis.com/v1/accounts:delete?key=$WEBKEY" -H "Content-Type: application/json" --data "@$req" | Out-Null
"cleanup done"
