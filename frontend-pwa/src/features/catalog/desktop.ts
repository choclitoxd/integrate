/**
 * Segundo punto de entrada público de la feature (ADR-0020 §5): las vistas de escritorio se importan
 * solo con `import()` diferido, para que no viajen en el bundle móvil del `index.ts`.
 */
export { CatalogDesktopPage } from './pages/desktop/CatalogDesktopPage';
export { ExperienceDetailDesktopPage } from './pages/desktop/ExperienceDetailDesktopPage';
export { SlotPickerDesktopPage } from './pages/desktop/SlotPickerDesktopPage';
