import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { MeetingPointMap } from './MeetingPointMap';

describe('MeetingPointMap', () => {
  it('incrusta OpenStreetMap centrado en el punto, con título accesible', () => {
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

  it('no dibuja nada con coordenadas fuera de rango', () => {
    const { container } = render(<MeetingPointMap latitude={91} longitude={0} title="X" />);
    expect(container).toBeEmptyDOMElement();
  });
});
