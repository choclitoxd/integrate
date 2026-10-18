# Manual de usuario

Guía paso a paso para usar Intégrate según el rol: Turista, Gestor de Enlace, Anfitrión comunitario y
Administrador. Versión **Fase 1 · MVP transaccional**, revisada el 16 de octubre de 2026 recorriendo el
flujo real en la aplicación.

| Formato | Archivo | Cuándo usarlo |
| :--- | :--- | :--- |
| Markdown | [manual-usuario.md](manual-usuario.md) | Leerlo directamente en GitHub o en el editor. Las capturas están en [`img/`](img/). |
| HTML autocontenido | [manual-usuario.html](manual-usuario.html) | Abrirlo en el navegador, imprimirlo o enviarlo como un solo archivo (las imágenes van embebidas). |

## Contenido

1. **Antes de empezar** — requisitos, navegación general y cuentas de prueba del entorno local.
2. **Roles y permisos** — qué puede hacer cada rol y cómo obtiene su cuenta.
3. **Turista** — crear cuenta, explorar y filtrar, reservar, pagar, voucher con QR y Mi cuenta.
4. **Gestor de Enlace** — panel de jornada, enrolar anfitriones, entregar el código de activación.
5. **Anfitrión comunitario** — activar la cuenta con el código del gestor.
6. **Administrador** — alcance actual del rol.
7. **Mensajes y problemas frecuentes**.
8. **Registro de la prueba del flujo** — qué se verificó y qué no.

## Mantenimiento

- El HTML es la fuente. `manual-usuario.md` y las imágenes de `img/` se generaron a partir de él; si cambia el
  HTML, regenere ambos para que no diverjan.
- Las capturas usan solo datos ficticios de la semilla de desarrollo. Nunca incluya contraseñas, tokens ni
  datos reales de personas (ver [política de secretos](../security/security_secrets_policy.md)).
- Cuando una funcionalidad nueva llegue a la PWA (por ejemplo, la validación offline de la Etapa 2), añada su
  sección y una fila en el registro de prueba.
