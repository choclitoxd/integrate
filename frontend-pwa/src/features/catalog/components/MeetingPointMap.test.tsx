import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { MeetingPointMap } from './MeetingPointMap';

describe('MeetingPointMap', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('con llave de Google en el despliegue usa la Embed API', () => {
    vi.stubEnv('VITE_GOOGLE_MAPS_EMBED_KEY', 'AIza-de-prueba');
    render(<MeetingPointMap latitude={6.187} longitude={-74.995} title="Ruta E2E" />);
    const src = new URL(
      screen.getByTitle('Mapa del punto de encuentro de Ruta E2E').getAttribute('src') ?? '',
    );
    expect(src.origin + src.pathname).toBe('https://www.google.com/maps/embed/v1/place');
    expect(src.searchParams.get('key')).toBe('AIza-de-prueba');
    expect(src.searchParams.get('q')).toBe('6.187,-74.995');
    // Google no dibuja dentro de un marco con sandbox.
    expect(screen.getByTitle('Mapa del punto de encuentro de Ruta E2E')).not.toHaveAttribute(
      'sandbox',
    );
  });

  it('sin llave incrusta OpenStreetMap centrado en el punto, con título accesible', () => {
    vi.stubEnv('VITE_GOOGLE_MAPS_EMBED_KEY', '');
    render(<MeetingPointMap latitude={6.187} longitude={-74.995} title="Ruta E2E" />);
    const map = screen.getByTitle('Mapa del punto de encuentro de Ruta E2E');
    const src = new URL(map.getAttribute('src') ?? '');
    expect(src.origin + src.pathname).toBe('https://www.openstreetmap.org/export/embed.html');
    expect(src.searchParams.get('marker')).toBe('6.187,-74.995');
    expect(src.searchParams.get('layer')).toBe('mapnik');
    expect(src.searchParams.get('bbox')).toBe('-75.000000,6.182000,-74.990000,6.192000');
    expect(map).toHaveAttribute('loading', 'lazy');
    expect(map.getAttribute('sandbox')).toContain('allow-scripts');
  });

  it('con coordenadas dudosas y llave de Google busca por la dirección escrita', () => {
    vi.stubEnv('VITE_GOOGLE_MAPS_EMBED_KEY', 'AIza-de-prueba');
    render(
      <MeetingPointMap
        latitude={4.533889}
        longitude={-75.681111}
        title="Caminata"
        addressQuery="Plaza de Bolívar, Armenia, Quindío, Colombia"
      />,
    );
    const src = new URL(
      screen.getByTitle('Mapa del punto de encuentro de Caminata').getAttribute('src') ?? '',
    );
    expect(src.origin + src.pathname).toBe('https://www.google.com/maps/embed/v1/place');
    expect(src.searchParams.get('q')).toBe('Plaza de Bolívar, Armenia, Quindío, Colombia');
  });

  it('con coordenadas dudosas y sin llave no dibuja un marcador equivocado', () => {
    vi.stubEnv('VITE_GOOGLE_MAPS_EMBED_KEY', '');
    const { container } = render(
      <MeetingPointMap
        latitude={4.53}
        longitude={-75.68}
        title="X"
        addressQuery="Plaza, Armenia"
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('no dibuja nada con coordenadas fuera de rango', () => {
    const { container } = render(<MeetingPointMap latitude={91} longitude={0} title="X" />);
    expect(container).toBeEmptyDOMElement();
  });
});
