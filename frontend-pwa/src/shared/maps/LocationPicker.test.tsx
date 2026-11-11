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
