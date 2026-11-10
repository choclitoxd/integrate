# Trabajo en curso (coordinación entre cambios)

Este archivo evita que dos cambios choquen en los mismos archivos. Se lee **antes** de empezar a editar y se actualiza **al empezar y al terminar** cada cambio.

## Cómo se usa

1. **Antes de editar:** revisar las filas de abajo. Si el área que vas a tocar está ocupada por otro cambio, avisar antes de tocarla.
2. **Al empezar:** agregar una fila con tu nombre, el área (carpeta o archivos), qué haces y la hora.
3. **Al terminar:** quitar tu fila (o marcarla `Terminado` si quedó algo pendiente que alguien debe revisar).
4. **Ramas:** cada cambio grande va en su propia rama `feature/...`. Los cambios sin comitear en `main` o en la rama compartida se avisan aquí.

## Cambios en curso

| Quién | Área / archivos | Qué se está haciendo | Desde | Estado |
| :--- | :--- | :--- | :--- | :--- |
| Equipo de Desarrollo | `frontend-pwa/src/features/admin/*`, `backend-api/` y `docs/` | ADR-0047 y ADR-0048: Edición administrativa de experiencias/asociaciones, altas de usuarios/experiencias y listado por roles | 2026-11-10 16:44 | Terminado (ADR-0048 completado y verificado) |
| Sin identificar | `docs/manual/*`, `frontend-pwa/public/manual*`, `ADR-0027`, `ADR-0028`, `.github/workflows/deploy-manual.yml`, `infra/`, `render.yaml` | Manual de usuario y despliegue | 2026-10-27 | Sin comitear |
| Claude (sesión con Sebastián) | `frontend-pwa/src/features/catalog/components/ExperienceDetailParts.tsx` | Ficha: aporte del 85 % a la comunidad y frase de nivel de esfuerzo (sin datos nuevos) | 2026-11-10 | En curso |

> Completa o corrige la columna **Quién** con tu nombre. Si no sabes quién dejó una fila, pregúntalo antes de tocar esa área.
