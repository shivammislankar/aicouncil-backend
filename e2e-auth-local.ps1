$signinBody = '{"email":"otp-local-test@example.com","password":"LocalTest!12345","returnSecureToken":true}'
$signinBody | Out-File -Encoding utf8 -NoNewline "$env:TEMP\otp-signin.json"
$tok = (& curl.exe -sS -m 30 -X POST 'https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=AIzaSyDerXbBWG_RAWo9bjpdvDMyGzUjHxwsM1c' -H 'Content-Type: application/json' --data "@$env:TEMP\otp-signin.json" | ConvertFrom-Json).idToken
if (-not $tok) { 'signin failed'; exit 1 }

'=== verification-status (OTP disabled -> verified:true) ==='
& curl.exe -sS -m 20 'http://localhost:8080/api/auth/verification-status' -H "Authorization: Bearer $tok"
''
'=== otp/verify passthrough (OTP disabled -> verified:true) ==='
'{"code":"000000"}' | Out-File -Encoding utf8 -NoNewline "$env:TEMP\otp-verify.json"
& curl.exe -sS -m 20 -X POST 'http://localhost:8080/api/auth/otp/verify' -H 'Content-Type: application/json' -H "Authorization: Bearer $tok" --data "@$env:TEMP\otp-verify.json"
''
'=== oauth-sync (password user -> must gain nothing) ==='
& curl.exe -sS -m 30 -X POST 'http://localhost:8080/api/auth/oauth-sync' -H "Authorization: Bearer $tok"
''
'=== ask with greeting (gate passthrough) ==='
'{"question":"hi"}' | Out-File -Encoding utf8 -NoNewline "$env:TEMP\otp-ask.json"
$ans = & curl.exe -sS -m 60 -X POST 'http://localhost:8080/api/council/ask' -H 'Content-Type: application/json' -H "Authorization: Bearer $tok" --data "@$env:TEMP\otp-ask.json" | ConvertFrom-Json
'greeting=' + $ans.payload.greeting + ' answer=' + $ans.payload.finalAnswer.Substring(0, 40) + '...'
