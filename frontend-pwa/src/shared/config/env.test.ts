import { describe, expect, it } from 'vitest';

import { ConfigError, parseConfig } from './env';

describe('parseConfig', () => {
  it.each([undefined, '', '   '])('falla sin VITE_API_BASE_URL (%j)', (value) => {
    expect(() => parseConfig({ VITE_API_BASE_URL: value })).toThrow(ConfigError);
  });

  it.each(['api', 'ftp://x.co', 'https://', '//evil.co', 'https://api.integrate.co/ x'])(
    'rechaza valores que no son ruta ni URL http(s): %s',
    (value) => {
      expect(() => parseConfig({ VITE_API_BASE_URL: value })).toThrow(ConfigError);
    },
  );

  it.each([
    ['/api', '/api'],
    ['/api/', '/api'],
    ['https://api.integrate.co', 'https://api.integrate.co'],
    ['http://localhost:8080/', 'http://localhost:8080'],
  ])('acepta %s y quita la barra final', (value, expected) => {
    expect(parseConfig({ VITE_API_BASE_URL: value }).apiBaseUrl).toBe(expected);
  });
});
