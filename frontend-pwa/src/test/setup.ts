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

afterEach(() => {
  cleanup();
});
