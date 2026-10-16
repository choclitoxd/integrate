import { act } from '@testing-library/react';
import { vi } from 'vitest';

export interface Device {
  readonly width: number;
  readonly hover: boolean;
  readonly finePointer: boolean;
}

export const PHONE: Device = { width: 390, hover: false, finePointer: false };
export const TABLET: Device = { width: 1180, hover: false, finePointer: false };
export const NARROW_WINDOW: Device = { width: 900, hover: true, finePointer: true };
export const LAPTOP: Device = { width: 1280, hover: true, finePointer: true };

/** Evalúa las media features que usa la app (ADR-0020 §2): min-width, hover y pointer. */
function matches(query: string, device: Device): boolean {
  return query
    .split(' and ')
    .map((part) => part.trim().replace(/^\(|\)$/g, ''))
    .every((feature) => {
      const [name = '', value = ''] = feature.split(':').map((s) => s.trim());
      if (name === 'min-width') return device.width >= Number.parseInt(value, 10);
      if (name === 'hover') return (value === 'hover') === device.hover;
      if (name === 'pointer') return (value === 'fine') === device.finePointer;
      throw new Error(`Media feature no simulada: ${feature}`);
    });
}

/**
 * matchMedia simulado (jsdom no lo implementa). `setDevice` cambia el dispositivo en caliente y
 * notifica a los suscriptores, como al redimensionar la ventana o conectar un ratón.
 */
export function stubMatchMedia(initial: Device) {
  let device = initial;
  const listeners = new Set<() => void>();
  vi.stubGlobal('matchMedia', (query: string) => ({
    get matches() {
      return matches(query, device);
    },
    media: query,
    addEventListener: (_type: string, listener: () => void) => listeners.add(listener),
    removeEventListener: (_type: string, listener: () => void) => listeners.delete(listener),
  }));
  return {
    setDevice(next: Device) {
      device = next;
      act(() => {
        listeners.forEach((listener) => {
          listener();
        });
      });
    },
  };
}
