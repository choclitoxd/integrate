# Manual de usuario

Guía paso a paso para usar Intégrate según el rol: Turista, Gestor de Enlace, Anfitrión comunitario y
Administrador. La revisión del 11 de noviembre de 2026 se cotejó con las pantallas y rutas de la PWA.

| Formato | Archivo | Cuándo usarlo |
| :--- | :--- | :--- |
| Markdown | [manual-usuario.md](manual-usuario.md) | Leerlo directamente en GitHub o en el editor. Las capturas están en [`img/`](img/). |
| HTML autocontenido | [manual-usuario.html](manual-usuario.html) | Abrirlo en el navegador, imprimirlo o enviarlo como un solo archivo (las imágenes van embebidas). Es el mismo que publica la PWA en `/manual/` (ADR-0028). |

## Contenido

1. **Antes de empezar** — requisitos, navegación general y cuentas de prueba del entorno local.
2. **Roles y permisos** — qué puede hacer cada rol y cómo obtiene su cuenta.
3. **Turista** — crear cuenta, recuperar la contraseña, explorar, reservar, pagar y volver a abrir el voucher desde Mis experiencias.
4. **Gestor de Enlace** — enrolar anfitriones, emitir códigos de activación, descargar el padrón, validar vouchers en campo y sincronizar asistencias.
5. **Anfitrión comunitario** — activar la cuenta, gestionar experiencias y horarios, consultar reservas y métricas.
6. **Administrador** — moderar experiencias, gestionar asociaciones y personas, revisar incidentes, liquidaciones y auditoría.
7. **Mensajes y problemas frecuentes**.
8. **Registro histórico de la prueba del flujo** — resultados del recorrido local de octubre.

## Mantenimiento

- El HTML es la fuente. `manual-usuario.md` y las imágenes de `img/` se generaron a partir de él; si cambia el
  HTML, regenere ambos para que no diverjan. La PWA lo copia en su build; no hay otra copia que actualizar.
- Las capturas usan solo datos ficticios de la semilla de desarrollo. Nunca incluya contraseñas, tokens ni
  datos reales de personas (ver [política de secretos](../security/security_secrets_policy.md)).
- Cuando cambie un flujo, actualice su sección y compruebe los nombres de los controles y los permisos de
  cada rol. Si realiza una prueba en la aplicación, registre su fecha, entorno y resultado.
