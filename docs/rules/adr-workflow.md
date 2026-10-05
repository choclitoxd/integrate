# PROTOCOLO OBLIGATORIO: GENERACIÓN PREVIA DE ADR

## Regla de Ejecución
Siempre que se solicite implementar una nueva funcionalidad, módulo, cambio estructural o integración en el backend, la IA debe detenerse inmediatamente y redactar primero un **Architecture Decision Record (ADR)** antes de emitir cualquier código fuente.

---

## Procedimiento de Ejecución en 2 Pasos

### 1. Paso Previo (ADR)
Redactar el ADR en Markdown siguiendo la plantilla estándar Michael Nygard con la siguiente estructura:

```markdown
# ADR-XXXX: [Verbo/Decisión concisa]

- **Estado:** Propuesto
- **Proyecto / Módulo:** [Nombre del proyecto o módulo]
- **Fecha:** [YYYY-MM-DD]

## Contexto
[Problema técnico o de negocio que motiva la decisión y restricciones aplicables. Máximo 2 párrafos.]

## Decisión
[Tecnología, patrón de diseño o enfoque arquitectónico elegido y su justificación técnica.]

## Alternativas Descartadas
- **Alternativa A:** [Motivo técnico o de negocio por el cual fue descartada].
- **Alternativa B:** [Motivo técnico o de negocio por el cual fue descartada].

## Consecuencias
- **Positivas:** [Beneficios que aporta la solución adoptada].
- **Negativas / Riesgos asumidos:** [Compromisos, complejidad adicional o riesgos asumidos].

## Contrato o Esquema Conceptual (Opcional)
[Diagrama Mermaid o interfaces conceptuales de alto nivel. Prohibido incluir código fuente completo de controladores, repositorios o servicios].
```

### 2. Validación
El código de implementación (clases, funciones, tests) **solo se generará una vez que el contenido de este ADR sea revisado y aceptado expresamente por el desarrollador**.
