import '@testing-library/jest-dom/vitest';

import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// jsdom no implementa showModal()/close() de <dialog>; se emula lo que usa BottomSheet.
if (typeof HTMLDialogElement.prototype.showModal !== 'function') {
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    if (!this.hasAttribute('open')) return;
    this.removeAttribute('open');
    this.dispatchEvent(new Event('close'));
  };
}

// jsdom no implementa el scroll (ScrollRestoration y los avisos de error lo usan): no-op silencioso.
window.scrollTo = () => undefined;

// jsdom no implementa matchMedia (prefers-reduced-motion).
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  }),
});
// jsdom tampoco implementa scrollIntoView (el menú de Select mantiene visible la opción activa).
Element.prototype.scrollIntoView = () => undefined;

afterEach(() => {
  cleanup();
});
