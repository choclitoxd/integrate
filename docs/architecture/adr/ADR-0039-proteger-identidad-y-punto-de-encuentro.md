# ADR-0039: Proteger documento, celular y punto de encuentro

- **Estado:** Aceptado (2026-11-09)
- **Proyecto / Módulo:** Intégrate · autenticación, catálogo, reservas y voucher
- **Fecha:** 2026-11-09
- **Trazabilidad:** Tejiendo Hogar, H-01 · PGCP, RNF-01

## Contexto

En `auth.users`, el documento y el celular se almacenan en claro. El documento también sostiene la restricción de unicidad por tipo y número. ADR-0038 evita incorporar el documento al correo sintético nuevo y enmascara los correos heredados en la lista del gestor. Los alias heredados que aún contienen el documento siguen siendo identificadores de inicio de sesión.

La proyección pública de una experiencia devuelve dirección y coordenadas exactas. Limitar decimales sin retirar la dirección ni revisar los datos de paradas y enlaces al mapa permite reconstruir la ubicación.

## Decisión

1. Cifrar documento y celular con el puerto existente de AES-256-GCM. Vincular cada valor cifrado a la fila y al campo mediante AAD. Mantener las versiones de clave e instrumentar las lecturas sensibles con el puerto de auditoría existente. El índice ciego HMAC-SHA-256, con clave independiente y tipo de documento incluido en su entrada, conserva la búsqueda y unicidad del documento. Preservar inicialmente las reglas de comparación del sistema para evitar colisiones al migrar.
2. Hacer la transición en dos entregas. Agregar las columnas protegidas, preparar las claves fuera de Git y rellenar los registros existentes en lotes verificables. Validar cobertura y unicidad antes de cortar lecturas y escrituras en claro. Revisar en el mismo corte el aprovisionamiento, las semillas, los permisos del rol de reportes y la rutina de anonimización. Retirar las columnas en claro tras validar el corte. Las copias anteriores al corte quedan bajo la política de retención y acceso de ADR-0041.
3. Inventariar los alias sintéticos heredados que contienen documentos. Asignar alias opacos mediante una transición coordinada con cada titular. Entregar el nuevo usuario por un canal privado y verificar ingreso, activación y recuperación. Retirar el alias derivado una vez verificado el cambio. Mantener esta brecha abierta en la evidencia mientras existan alias con documento.
4. Separar la ubicación pública de la ubicación operativa. El catálogo publica como máximo dos decimales y una referencia general. Retirar de la proyección pública las direcciones exactas, enlaces geográficos y paradas que permitan reconstruir el punto. Mantener la ubicación operativa para quienes administran esa experiencia dentro de su ámbito y para el titular de una reserva confirmada a través de la consulta autenticada del voucher. Autorizar cada consulta contra el recurso y evitar caché compartida para las respuestas precisas.

## Alternativas descartadas

- Cifrado de disco como única medida. Una consulta con acceso a las columnas seguiría devolviendo el documento y el celular en claro.
- Redondear únicamente las coordenadas. La dirección pública y los enlaces de mapa seguirían revelando el punto.

## Consecuencias

- Se conserva la detección de duplicados y el flujo de llegada de las reservas confirmadas.
- El corte exige coordinación de claves, migración y despliegue. La pérdida de una clave necesaria puede impedir la recuperación de los campos protegidos.

## Criterios de aceptación

- Verificar con datos sintéticos que altas, consultas autorizadas y anonimización conservan su comportamiento, que las altas concurrentes duplicadas se rechazan y que ningún campo ni alias heredado conserva los valores en claro en la base activa tras el corte.
- Probar que visitante, reserva pendiente, reserva cancelada y usuario ajeno carecen de acceso a la ubicación precisa. Verificar el contrato del catálogo y la consulta autorizada del voucher desde navegador y celular.
- Regenerar RNF-01 con cobertura de migración y pruebas de exposición. Registrar el alcance de los respaldos anteriores al corte.
