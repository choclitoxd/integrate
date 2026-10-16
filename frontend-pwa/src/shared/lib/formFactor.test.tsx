import { renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { LAPTOP, NARROW_WINDOW, PHONE, stubMatchMedia, TABLET } from '@/test/matchMedia';

import { useFormFactor } from './formFactor';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('useFormFactor (ADR-0020 §2)', () => {
  it.each([
    ['un teléfono', PHONE, 'mobile'],
    ['una tablet táctil de pantalla ancha', TABLET, 'mobile'],
    ['un computador con la ventana angosta', NARROW_WINDOW, 'mobile'],
    ['un computador con ratón', LAPTOP, 'desktop'],
  ] as const)('clasifica %s como %s', (_name, device, expected) => {
    stubMatchMedia(device);
    const { result } = renderHook(() => useFormFactor());
    expect(result.current).toBe(expected);
  });

  it('es móvil donde matchMedia no existe', () => {
    vi.stubGlobal('matchMedia', undefined);
    const { result } = renderHook(() => useFormFactor());
    expect(result.current).toBe('mobile');
  });

  it('cambia en caliente al redimensionar la ventana', () => {
    const media = stubMatchMedia(LAPTOP);
    const { result } = renderHook(() => useFormFactor());
    expect(result.current).toBe('desktop');
    media.setDevice(NARROW_WINDOW);
    expect(result.current).toBe('mobile');
  });
});
