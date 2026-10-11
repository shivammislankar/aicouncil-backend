$BASE = 'https://aicouncil-backend.onrender.com'
$WEBKEY = 'AIzaSyDerXbBWG_RAWo9bjpdvDMyGzUjHxwsM1c'
$req = Join-Path $env:TEMP 'av-req.json'
$resp = Join-Path $env:TEMP 'av-resp.json'

$email = 'av-' + [guid]::NewGuid().ToString('N').Substring(0, 8) + '@example.com'
(@{ email = $email; password = 'E2eTest!12345'; returnSecureToken = $true } | ConvertTo-Json -Compress) | Out-File -Encoding utf8 -NoNewline $req
& curl.exe -sS -m 60 -o $resp "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$WEBKEY" -H 'Content-Type: application/json' --data "@$req" | Out-Null
$u = Get-Content -Raw $resp | ConvertFrom-Json
if (-not $u.idToken) { 'signup failed'; exit 1 }

'=== verification-status (OTP off -> verified:true, nothing breaks) ==='
& curl.exe -sS -m 30 -w ' [%{http_code}]' "$BASE/api/auth/verification-status" -H "Authorization: Bearer $($u.idToken)"
''
'=== oauth-sync as password user (must gain nothing new) ==='
& curl.exe -sS -m 30 -w ' [%{http_code}]' -X POST "$BASE/api/auth/oauth-sync" -H "Authorization: Bearer $($u.idToken)"
''
'=== greeting still works through the gate ==='
(@{ question = 'hi' } | ConvertTo-Json -Compress) | Out-File -Encoding utf8 -NoNewline $req
$ans = & curl.exe -sS -m 60 -X POST "$BASE/api/council/ask" -H 'Content-Type: application/json' -H "Authorization: Bearer $($u.idToken)" --data "@$req" | ConvertFrom-Json
'greeting=' + $ans.payload.greeting
''
'=== rate limiter still active (16th quick hit -> 429) ==='
$blocked = $false
for ($i = 1; $i -le 17; $i++) {
  $c = & curl.exe -sS -m 30 -o NUL -w '%{http_code}' -X POST "$BASE/api/council/ask" -H 'Content-Type: application/json' -H "Authorization: Bearer $($u.idToken)" --data "@$req"
  if ($c -eq '429') { "blocked at request #$i"; $blocked = $true; break }
}
if (-not $blocked) { 'FAIL: no 429 seen' }

(@{ idToken = $u.idToken } | ConvertTo-Json -Compress) | Out-File -Encoding utf8 -NoNewline $req
& curl.exe -sS -m 60 -o NUL -X POST "https://identitytoolkit.googleapis.com/v1/accounts:delete?key=$WEBKEY" -H 'Content-Type: application/json' --data "@$req" | Out-Null
'cleanup done'
