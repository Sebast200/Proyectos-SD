Write-Host "👀 INICIANDO VIGILANCIA DEL HOSPITAL..." -ForegroundColor Cyan

$masterContainer = "pg-master"
$replicaContainer = "pg-replica"

while ($true) {
    $status = docker inspect -f '{{.State.Running}}' $masterContainer 2>$null

    if ($status -eq "true") {
        Write-Host "✅ Maestro vivo. Todo en orden." -NoNewline -ForegroundColor Green
        Write-Host "" -NoNewline
    }
    else {
        Write-Host "
❌ ALERTA: ¡EL MAESTRO HA CAÍDO!" -ForegroundColor Red
        Write-Host "⚡ Iniciando Protocolo de Emergencia Automático..." -ForegroundColor Yellow

        Write-Host "   -> Promoviendo Réplica a nuevo Maestro..."
        docker exec $replicaContainer su-exec postgres pg_ctl promote -D /var/lib/postgresql/data
        
        if ($?) {
            Write-Host "   ✅ ¡Réplica Promovida con Éxito!" -ForegroundColor Green
            Write-Host "   ℹ️  HAProxy detectará el cambio y usará el Backup automáticamente."
            Write-Host "🚀 SISTEMA RECUPERADO. La Réplica ahora tiene el control." -ForegroundColor Cyan
            break 
        } else {
            Write-Host "   ❌ Falló la promoción. Revisa los logs." -ForegroundColor Red
        }
    }
    Start-Sleep -Seconds 2
}
