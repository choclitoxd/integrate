import type { TaskMeta } from 'vitest';

/**
 * Evidencia cuantitativa de la suite de calidad (ADR-0019 §6-§7). Se guarda en `task.meta`, que el
 * reporter JSON de Vitest publica por prueba; `qa/scripts/build-evidence-report.mjs` la convierte en
 * la sección 5 del Plan de Gestión de la Calidad. Se registra antes de afirmar.
 */
export interface QualityMetric {
  /** Campo "ID" del RNF en el plan, p. ej. `RNF-15`. */
  readonly rnf: string;
  readonly metric: string;
  readonly value: number;
  readonly unit: string;
  /** Criterio de aceptación del plan, legible. */
  readonly threshold: string;
  readonly pass: boolean;
  readonly detail: string;
}

declare module 'vitest' {
  interface TaskMeta {
    quality?: QualityMetric[];
  }
}

export function recordQuality(task: { meta: TaskMeta }, metric: QualityMetric): void {
  (task.meta.quality ??= []).push(metric);
}

export function percent(part: number, total: number): number {
  return total === 0 ? 100 : (100 * part) / total;
}
