# EDYLASER NFC

Aplicación Android interna para escanear el QR de una placa EDYLASER3D y grabar exactamente esa URL en su chip NFC.

## Flujo

1. Pulsa **Escanear QR**.
2. La aplicación valida que sea una dirección HTTPS de `edylaser3d.com`.
3. Acerca la placa NFC al teléfono.
4. La aplicación escribe un registro NDEF, lo vuelve a leer y confirma que coincida.
5. Vibra, muestra confirmación y suma la placa al contador.

## Compilación

El workflow `.github/workflows/build-apk.yml` genera `app-release.apk` como artefacto descargable en GitHub Actions.

> La versión 1.0 usa una firma de prueba para instalación interna. Antes de publicarla en Play Store debe crearse una firma privada de producción.
