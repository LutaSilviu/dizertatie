$ErrorActionPreference = 'Stop'
$out = $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)
$results = New-Object System.Collections.Generic.List[object]
function Case($name, $method, $path, $body = $null) {
    $args = @('-sS', '--max-time', '100', '-X', $method, '-D', "$out/$name.headers.txt", '-o', "$out/$name.response.json", '-w', '%{http_code}', "http://localhost:8080$path")
    if ($null -ne $body) {
        [IO.File]::WriteAllText("$out/$name.request.json", $body, $utf8)
        $args += @('-H','Content-Type: application/json', '--data-binary', "@$out/$name.request.json")
    }
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $status = & curl.exe @args
    $timer.Stop()
    $results.Add([pscustomobject]@{case=$name;method=$method;path=$path;status=$status;seconds=[Math]::Round($timer.Elapsed.TotalSeconds,2)})
    $results | ConvertTo-Json -Depth 5 | Set-Content "$out/cases.json" -Encoding UTF8
    Write-Output "$name HTTP $status ($($timer.Elapsed.TotalSeconds.ToString('F2')) s)"
}
Case 'health' GET '/health'
Case 'history-initial' GET '/scans?size=5'
foreach($sample in @('before','after')) {
    Case "w3c-$sample-post" POST '/scans' "{`"url`":`"https://www.w3.org/WAI/demos/bad/$sample/home.html`",`"method`":`"AXE`"}"
    $response = Get-Content "$out/w3c-$sample-post.response.json" -Raw | ConvertFrom-Json
    if($response.id) {
        Case "w3c-$sample-get" GET "/scans/$($response.id)"
        Case "w3c-$sample-csv" GET "/scans/$($response.id)/export.csv"
    }
}
Case 'unsupported-method' POST '/scans' '{"url":"https://www.w3.org/WAI/demos/bad/before/home.html","method":"NOPE"}'
foreach($sample in @('before','after')) {
    Case "fixture-$sample-post" POST '/scans' "{`"url`":`"http://localhost:8080/fixtures/wai-bad/$sample/home.html`",`"method`":`"AXE`"}"
    $response = Get-Content "$out/fixture-$sample-post.response.json" -Raw | ConvertFrom-Json
    if($response.id) {
        Case "fixture-$sample-get" GET "/scans/$($response.id)"
        Case "fixture-$sample-csv" GET "/scans/$($response.id)/export.csv"
    }
}
Case 'unsupported-model' POST '/scans' '{"url":"https://www.w3.org/WAI/demos/bad/before/home.html","method":"AI","model":"invalid-test-model"}'
Case 'missing-method' POST '/scans' '{"url":"https://www.w3.org/WAI/demos/bad/before/home.html"}'
Case 'empty-url' POST '/scans' '{"url":"","method":"AXE"}'
Case 'null-url' POST '/scans' '{"url":null,"method":"AXE"}'
Case 'malformed-url' POST '/scans' '{"url":"not a URL","method":"AXE"}'
Case 'missing-id' GET '/scans/9223372036854775807'
Case 'bad-id' GET '/scans/not-a-number'
Case 'negative-page' GET '/scans?page=-1&size=20'
Case 'zero-size' GET '/scans?page=0&size=0'
Case 'negative-size' GET '/scans?page=0&size=-1'
Case 'malformed-json' POST '/scans' '{'
Case 'history-final' GET '/scans?size=100'
Case 'openapi' GET '/v3/api-docs'
