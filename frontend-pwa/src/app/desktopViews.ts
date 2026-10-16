import { lazy } from 'react';

/*
 * Vistas de escritorio (ADR-0020 §5): chunks propios desde @/features/<f>/desktop; un teléfono no
 * los descarga porque AdaptiveView solo los monta con factor de forma "desktop".
 */
export const CatalogDesktop = lazy(() =>
  import('@/features/catalog/desktop').then((m) => ({ default: m.CatalogDesktopPage })),
);
export const ExperienceDetailDesktop = lazy(() =>
  import('@/features/catalog/desktop').then((m) => ({ default: m.ExperienceDetailDesktopPage })),
);
export const SlotPickerDesktop = lazy(() =>
  import('@/features/catalog/desktop').then((m) => ({ default: m.SlotPickerDesktopPage })),
);
