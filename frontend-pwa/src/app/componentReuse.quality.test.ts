import { describe, expect, it } from 'vitest';

import { percent, recordQuality } from '@/test/quality';

/**
 * RNF-30 (Mantenibilidad · reusabilidad): cada componente base de `shared/ui` se reutiliza en al menos
 * 3 módulos distintos sin duplicar su lógica. Se analiza el código fuente real (imports de `@/shared/ui`).
 */

const sources = import.meta.glob<string>(
  ['/src/**/*.tsx', '!/src/**/*.test.tsx', '!/src/test/**'],
  {
    query: '?raw',
    import: 'default',
    eager: true,
  },
);

/** `SkipLink` es único por documento (WCAG 2.4.1): se excluye del umbral de reutilización. */
const SINGLETONS = new Set(['SkipLink']);
const BASE_COMPONENTS = [
  'Button',
  'TextField',
  'PasswordField',
  'Icon',
  'ProblemNotice',
  'Skeleton',
  'BottomSheet',
];

function consumersOf(component: string): string[] {
  return Object.entries(sources)
    .filter(([path]) => !path.startsWith('/src/shared/ui/'))
    .filter(([, code]) =>
      [...code.matchAll(/import\s*\{([^}]*)\}\s*from\s*'@\/shared\/ui'/g)].some((m) =>
        (m[1] ?? '').split(',').some((name) => name.trim().replace(/^type\s+/, '') === component),
      ),
    )
    .map(([path]) => path);
}

describe('[RNF-30] Reusabilidad de componentes UI en la PWA', () => {
  it('[RNF-30] cada componente base se reutiliza en al menos 3 módulos', ({ task }) => {
    const usage = BASE_COMPONENTS.filter((c) => !SINGLETONS.has(c)).map((component) => ({
      component,
      consumers: consumersOf(component).length,
    }));
    const reused = usage.filter((u) => u.consumers >= 3);
    recordQuality(task, {
      rnf: 'RNF-30',
      metric: 'Componentes base reutilizados en 3 o mas modulos',
      value: percent(reused.length, usage.length),
      unit: '%',
      threshold: '= 100 % (>= 3 vistas o módulos cada uno)',
      pass: reused.length === usage.length,
      detail: usage.map((u) => `${u.component}=${String(u.consumers)}`).join(', '),
    });
    expect(Object.keys(sources).length).toBeGreaterThan(20);
    expect(usage.filter((u) => u.consumers < 3)).toEqual([]);
  });
});
