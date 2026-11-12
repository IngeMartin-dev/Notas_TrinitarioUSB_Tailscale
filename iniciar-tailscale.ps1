# Arranca el frontend y publica el puerto 4200 con Tailscale Funnel.
# Antes: MySQL (3306) y el backend (8080) deben estar corriendo.
#   .\iniciar-tailscale.ps1        -> modo desarrollo (ng serve)
#   .\iniciar-tailscale.ps1 -Pwa   -> compilacion de produccion: app instalable (PWA) y service worker
param([switch]$Pwa)
$ErrorActionPreference = "Stop"
Set-Location "$PSScriptRoot\Frontend"
if (-not (Test-Path node_modules)) { npm install }
tailscale funnel --bg 4200
tailscale funnel status
Write-Host "Frontend en http://localhost:4200 (Ctrl+C para detener; 'tailscale funnel reset' cierra el acceso público)"
if ($Pwa) { npm run start:pwa } else { npm start }
