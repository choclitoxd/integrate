# ADR-0052: Endurecer la publicación de la PWA

- **Estado:** Aceptado
- **Proyecto / Módulo:** Intégrate · `frontend-pwa/` y Cloudflare Pages
- **Fecha:** 2026-11-10
- **Hallazgo relacionado:** H-07

## Contexto

El seguimiento de auditoría identificó `sourcemap: true` en el build de la PWA y no encontró configuración versionada de cabeceras para el sitio publicado. Los mapas de código de producción pueden revelar código fuente y las cabeceras del navegador reducen riesgos de carga, inclusión en marcos y filtración de referencias. La aplicación requiere conectarse a los dominios de API y servicios que usa realmente; una política demasiado restrictiva puede romper funciones legítimas.

## Decisión

Desactivar los source maps en el artefacto de producción y versionar las cabeceras estáticas en `frontend-pwa/public/_headers`. Aplicar `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `X-Frame-Options: DENY` y una `Permissions-Policy` que permita la cámara al mismo origen para el lector QR y desactive geolocalización y micrófono. Servir siempre por HTTPS y usar HSTS solo después de confirmar HTTPS estable en todos los subdominios incluidos.

Definir CSP a partir del inventario real de orígenes necesarios. La política inicial usa `Content-Security-Policy-Report-Only`, permite recursos del mismo origen y los marcos de Google Maps y OpenStreetMap, y admite estilos inline que React usa. No permite scripts inline ni `unsafe-eval` ni comodines. Se excluye `/manual/*` de CSP mientras el manual autónomo conserve estilos inline e imágenes incrustadas; las demás cabeceras siguen aplicándose allí. Desplegar primero en `pruebas`, revisar las violaciones en navegador, corregir solo las dependencias legítimas y después cambiar a modo de bloqueo. La configuración `_headers` de Pages se aplica a respuestas estáticas, no a respuestas de Pages Functions como `/api/*`. Verificar el artefacto y las cabeceras desde el host público después del despliegue; la configuración local no cierra H-07.

## Alternativas Descartadas

- **Conservar source maps públicos:** facilita depuración, pero expone el bundle original en producción.
- **Configurar CSP permisiva desde el inicio:** puede aparentar cumplimiento sin limitar efectivamente la carga de contenido.
- **Añadir cabeceras solo en el servidor de desarrollo:** no protege la PWA publicada en Pages.

## Consecuencias

- **Positivas:** menor exposición del código y defensa adicional en el navegador con configuración reproducible.
- **Negativas / Riesgos asumidos:** CSP puede bloquear integraciones o recursos legítimos; se requiere una fase de observación y regresión antes de hacerla cumplir.

## Criterios de aceptación

- El build de producción no publica archivos `.map` ni referencias a mapas.
- El directorio `dist` contiene `_headers` y el service worker no intenta almacenarlo en caché.
- La aplicación desplegada responde con las cabeceras aprobadas y solo por HTTPS.
- CSP se prueba primero en modo de reporte y luego en bloqueo, sin errores en los flujos principales.
- Se verifica en Chrome de escritorio y móvil: ingreso, catálogo, reserva, pago simulado, voucher y panel administrativo.

