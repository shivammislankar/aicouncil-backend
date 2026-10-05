$BASE   = "https://aicouncil-backend.onrender.com"
$WEBKEY = "AIzaSyDerXbBWG_RAWo9bjpdvDMyGzUjHxwsM1c"
$ORIGIN = "https://aicouncil-one-livid.vercel.app"
$tmp = Join-Path $env:TEMP "e2e-body.txt"

function PostJson($url, $json, $token) {
    $bodyFile = Join-Path $env:TEMP "e2e-req.json"
    if ($null -ne $json) { [IO.File]::WriteAllText($bodyFile, $json) }
    $a = @("-sS","-m","180","-o",$tmp,"-w","%{http_code} %{time_total}","-X","POST",$url,"-H","Content-Type: application/json")
    if ($token) { $a += @("-H","Authorization: Bearer $token") }
    if ($null -ne $json) { $a += @("--data", "@$bodyFile") }
    $meta = & curl.exe @a 2>&1
    $code, $time = ($meta -split ' ',2)
    return @{ code=$code; time=$time; body=(Get-Content -Raw $tmp) }
}

Write-Output "=== 1. HEALTH ==="
& curl.exe -sS -m 90 -o - -w "`nHTTP=%{http_code} time=%{time_total}s" "$BASE/health"
Write-Output ""

Write-Output "=== 2. CORS PREFLIGHT (real origin) ==="
& curl.exe -sS -m 60 -o - -D - -X OPTIONS "$BASE/api/council/ask" -H "Origin: $ORIGIN" -H "Access-Control-Request-Method: POST" -H "Access-Control-Request-Headers: authorization,content-type" -w "`nHTTP=%{http_code}" | Select-String -Pattern "access-control-allow-origin|HTTP="

Write-Output "=== 3. TEMP FIREBASE ACCOUNT ==="
$email = "e2e-" + [guid]::NewGuid().ToString("N").Substring(0,12) + "@example.com"
$signUp = PostJson "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$WEBKEY" (@{ email=$email; password="E2eTest!12345"; returnSecureToken=$true } | ConvertTo-Json -Compress) $null
if ($signUp.code -ne "200") { Write-Output "signup FAILED code=$($signUp.code) $($signUp.body)"; exit 1 }
$u = ConvertFrom-Json $signUp.body
$tok = $u.idToken; $uid = $u.localId
Write-Output "created uid=$uid email=$email"

try {
    Write-Output "=== 4. GREETING (should skip AI agents) ==="
    $g = PostJson "$BASE/api/council/ask" (@{ question="hi" } | ConvertTo-Json -Compress) $tok
    Write-Output "code=$($g.code) time=$($g.time)s"
    Write-Output $g.body

    Write-Output "=== 5. REAL QUESTION (full council) ==="
    $q = PostJson "$BASE/api/council/ask" (@{ question="Should a startup focus on product quality or speed to market first?" } | ConvertTo-Json -Compress) $tok
    Write-Output "code=$($q.code) time=$($q.time)s"
    Write-Output $q.body
} finally {
    Write-Output "=== 6. CLEANUP ==="
    $d = PostJson "https://identitytoolkit.googleapis.com/v1/accounts:delete?key=$WEBKEY" (@{ idToken=$tok } | ConvertTo-Json -Compress) $null
    Write-Output "delete code=$($d.code)"
}
