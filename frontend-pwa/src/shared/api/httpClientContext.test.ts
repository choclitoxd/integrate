import { describe, expect, it } from 'vitest';

import { ApiError, NetworkError } from './errors';
import { shouldRetryRequest } from './httpClientContext';

const apiError = (status: number) =>
  new ApiError({ type: 'about:blank', title: 'x', status }, null);

describe('shouldRetryRequest', () => {
  it('reintenta fallos de red y 5xx hasta dos veces', () => {
    expect(shouldRetryRequest(0, new NetworkError(new TypeError()))).toBe(true);
    expect(shouldRetryRequest(1, apiError(503))).toBe(true);
    expect(shouldRetryRequest(2, apiError(503))).toBe(false);
  });

  it('no reintenta errores 4xx ni errores desconocidos', () => {
    expect(shouldRetryRequest(0, apiError(404))).toBe(false);
    expect(shouldRetryRequest(0, apiError(422))).toBe(false);
    expect(shouldRetryRequest(0, new Error('x'))).toBe(false);
  });
});
