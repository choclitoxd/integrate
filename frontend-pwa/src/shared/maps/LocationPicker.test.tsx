import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LocationPicker } from './LocationPicker';

describe('LocationPicker sin llave de Google Maps', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_GOOGLE_MAPS_BROWSER_KEY', '');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('avisa que el mapa no está disponible y mantiene el enlace y las coordenadas manuales', () => {
    render(<LocationPicker lat={null} lng={null} onChange={vi.fn()} />);
    expect(screen.getByRole('status')).toHaveTextContent('El mapa no está disponible');
    expect(screen.getByRole('searchbox')).toBeInTheDocument();
  });

  it('convierte un enlace de Google Maps pegado en un punto', () => {
    const onChange = vi.fn();
    render(<LocationPicker lat={null} lng={null} onChange={onChange} />);
    const input = screen.getByRole('searchbox');
    fireEvent.change(input, {
      target: { value: 'https://www.google.com/maps/@4.6097,-74.0817,17z' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Usar este enlace' }));
    expect(onChange).toHaveBeenCalledWith({ lat: 4.6097, lng: -74.0817 });
  });

  it('explica por qué no acepta un enlace corto', () => {
    const onChange = vi.fn();
    render(<LocationPicker lat={null} lng={null} onChange={onChange} />);
    fireEvent.change(screen.getByRole('searchbox'), {
      target: { value: 'https://maps.app.goo.gl/AbCdEf' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Usar este enlace' }));
    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('enlace corto');
  });
});

describe('LocationPicker con llave de Google Maps', () => {
  class FakeMap {
    // Google Maps reemplaza el contenido del contenedor al crear el mapa; React no debe depender de él.
    constructor(element: HTMLElement) {
      element.replaceChildren(document.createElement('div'));
    }
    setCenter = vi.fn();
    setZoom = vi.fn();
    addListener = vi.fn();
  }
  class FakeMarker {
    setPosition = vi.fn();
    addListener = vi.fn();
  }
  class FakeGeocoder {
    geocode = vi.fn(() => Promise.resolve({ results: [] }));
  }
  class FakeAutocomplete {
    addListener = vi.fn();
    getPlace = vi.fn(() => ({}));
  }

  beforeEach(() => {
    vi.stubEnv('VITE_GOOGLE_MAPS_BROWSER_KEY', 'llave-de-prueba');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    delete window.google;
  });

  it('crea el mapa y muestra "Usar mi ubicación" cuando la API está disponible', async () => {
    window.google = {
      maps: {
        Map: FakeMap,
        Marker: FakeMarker,
        Geocoder: FakeGeocoder,
        places: { Autocomplete: FakeAutocomplete },
      },
    };
    render(<LocationPicker lat={null} lng={null} onChange={vi.fn()} />);
    expect(await screen.findByRole('button', { name: 'Usar mi ubicación' })).toBeInTheDocument();
    expect(screen.queryByText('El mapa no está disponible', { exact: false })).toBeNull();
  });

  it('no se rompe si la API cargó sin la librería de lugares: cae al modo manual', async () => {
    window.google = {
      maps: { Map: FakeMap, Marker: FakeMarker },
    } as unknown as NonNullable<Window['google']>;
    render(<LocationPicker lat={4.6} lng={-74.1} onChange={vi.fn()} />);
    expect(
      await screen.findByText('El mapa no está disponible', { exact: false }),
    ).toBeInTheDocument();
  });
});
