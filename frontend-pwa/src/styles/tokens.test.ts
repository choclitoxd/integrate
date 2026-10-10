import { describe, expect, it } from 'vitest';

import { calculateContrastRatio, calculateRelativeLuminance, COLOR_PALETTE } from './tokens';

describe('Paleta de Colores de Intégrate - Auditoría de Contraste WCAG 2.1 y NTC 5854', () => {
  describe('Cálculo de Luminancia y Contrastes Base', () => {
    it('debe calcular luminancia de blanco (1.0) y negro puro (0.0)', () => {
      expect(calculateRelativeLuminance('#ffffff')).toBeCloseTo(1.0, 2);
      expect(calculateRelativeLuminance('#000000')).toBeCloseTo(0.0, 2);
    });

    it('debe arrojar ratio máximo 21:1 entre blanco y negro', () => {
      const ratio = calculateContrastRatio('#ffffff', '#000000');
      expect(ratio).toBe(21.0);
    });
  });

  describe('1. Marca Principal (Brand: Verde Bosque de Conservación)', () => {
    it('cumple WCAG AAA (≥ 7:1) para texto blanco sobre botón o cabecera brand', () => {
      const ratio = calculateContrastRatio(
        COLOR_PALETTE.brand.onBrand,
        COLOR_PALETTE.brand.DEFAULT,
      );
      expect(ratio).toBeGreaterThanOrEqual(7.0);
      expect(ratio).toBe(9.11);
    });

    it('cumple WCAG AAA (≥ 7:1) para texto brand sobre canvas blanco', () => {
      const ratio = calculateContrastRatio(COLOR_PALETTE.brand.DEFAULT, COLOR_PALETTE.canvas);
      expect(ratio).toBeGreaterThanOrEqual(7.0);
      expect(ratio).toBe(9.11);
    });

    it('cumple WCAG AAA (≥ 7:1) para texto brand sobre surface (#f4f6f2)', () => {
      const ratio = calculateContrastRatio(
        COLOR_PALETTE.brand.DEFAULT,
        COLOR_PALETTE.surface.DEFAULT,
      );
      expect(ratio).toBeGreaterThanOrEqual(7.0);
      expect(ratio).toBe(8.38);
    });

    it('cumple WCAG AAA (≥ 7:1) para texto brand sobre fondo suave brand.soft (#dcfce7)', () => {
      const ratio = calculateContrastRatio(COLOR_PALETTE.brand.DEFAULT, COLOR_PALETTE.brand.soft);
      expect(ratio).toBeGreaterThanOrEqual(7.0);
      expect(ratio).toBe(8.3);
    });

    it('cumple WCAG AAA (≥ 7:1) para estados interactivos hover y active con texto blanco', () => {
      const hoverRatio = calculateContrastRatio(
        COLOR_PALETTE.brand.onBrand,
        COLOR_PALETTE.brand.hover,
      );
      const activeRatio = calculateContrastRatio(
        COLOR_PALETTE.brand.onBrand,
        COLOR_PALETTE.brand.active,
      );
      expect(hoverRatio).toBeGreaterThanOrEqual(7.0);
      expect(activeRatio).toBeGreaterThanOrEqual(7.0);
    });
  });

  describe('2. Neutros, Superficies y Tipografía (Ink)', () => {
    it('cumple WCAG AAA (≥ 7:1) para texto principal ink sobre canvas y surface', () => {
      const onCanvas = calculateContrastRatio(COLOR_PALETTE.ink.DEFAULT, COLOR_PALETTE.canvas);
      const onSurface = calculateContrastRatio(
        COLOR_PALETTE.ink.DEFAULT,
        COLOR_PALETTE.surface.DEFAULT,
      );
      expect(onCanvas).toBeGreaterThanOrEqual(7.0);
      expect(onSurface).toBeGreaterThanOrEqual(7.0);
      expect(onCanvas).toBe(17.74);
      expect(onSurface).toBe(16.31);
    });

    it('cumple WCAG AAA (≥ 7:1) para texto secundario ink.muted sobre canvas y surface', () => {
      const onCanvas = calculateContrastRatio(COLOR_PALETTE.ink.muted, COLOR_PALETTE.canvas);
      const onSurface = calculateContrastRatio(
        COLOR_PALETTE.ink.muted,
        COLOR_PALETTE.surface.DEFAULT,
      );
      expect(onCanvas).toBeGreaterThanOrEqual(7.0);
      expect(onSurface).toBeGreaterThanOrEqual(7.0);
      expect(onCanvas).toBe(10.31);
      expect(onSurface).toBe(9.48);
    });

    it('cumple WCAG AA y AAA (≥ 7:1) para texto terciario ink.subtle sobre canvas (#4b5563)', () => {
      const onCanvas = calculateContrastRatio(COLOR_PALETTE.ink.subtle, COLOR_PALETTE.canvas);
      expect(onCanvas).toBeGreaterThanOrEqual(7.0);
      expect(onCanvas).toBe(7.56);
    });
  });

  describe('3. Bordes y Controles de Interfaz (WCAG 2.1 SC 1.4.11 ≥ 3:1)', () => {
    it('todos los bordes cumplen el umbral de componentes interactivos (≥ 3:1)', () => {
      const defaultBorder = calculateContrastRatio(
        COLOR_PALETTE.border.DEFAULT,
        COLOR_PALETTE.canvas,
      );
      const subtleBorder = calculateContrastRatio(
        COLOR_PALETTE.border.subtle,
        COLOR_PALETTE.canvas,
      );
      const strongBorder = calculateContrastRatio(
        COLOR_PALETTE.border.strong,
        COLOR_PALETTE.canvas,
      );

      expect(defaultBorder).toBeGreaterThanOrEqual(3.0);
      expect(subtleBorder).toBeGreaterThanOrEqual(3.0);
      expect(strongBorder).toBeGreaterThanOrEqual(3.0);
    });
  });

  describe('4. Acento Territorial (Terracota y Memoria)', () => {
    it('cumple WCAG AAA (≥ 7:1) para acento profundo sobre blanco y sobre fondo acento suave', () => {
      const onWhite = calculateContrastRatio(COLOR_PALETTE.accent.DEFAULT, COLOR_PALETTE.canvas);
      const onSoft = calculateContrastRatio(
        COLOR_PALETTE.accent.DEFAULT,
        COLOR_PALETTE.accent.soft,
      );
      const textOnSolid = calculateContrastRatio(
        COLOR_PALETTE.accent.onAccent,
        COLOR_PALETTE.accent.DEFAULT,
      );

      expect(onWhite).toBeGreaterThanOrEqual(7.0);
      expect(onSoft).toBeGreaterThanOrEqual(7.0);
      expect(textOnSolid).toBeGreaterThanOrEqual(7.0);
    });

    it('cumple WCAG AA (≥ 4.5:1) para acento terracota medio (#9a3412)', () => {
      const ratio = calculateContrastRatio(COLOR_PALETTE.accent.medium, COLOR_PALETTE.canvas);
      expect(ratio).toBeGreaterThanOrEqual(4.5);
      expect(ratio).toBe(7.31); // De hecho también supera 7:1
    });
  });

  describe('5. Estados Semánticos (Éxito, Advertencia, Peligro e Información)', () => {
    describe('Éxito (Success / Canje de QR / Pago)', () => {
      it('cumple WCAG AAA para texto blanco sobre success.solid y texto success sobre background', () => {
        const solidRatio = calculateContrastRatio(
          COLOR_PALETTE.status.success.onSolid,
          COLOR_PALETTE.status.success.solid,
        );
        const textOnBg = calculateContrastRatio(
          COLOR_PALETTE.status.success.text,
          COLOR_PALETTE.status.success.bg,
        );
        const borderRatio = calculateContrastRatio(
          COLOR_PALETTE.status.success.border,
          COLOR_PALETTE.canvas,
        );

        expect(solidRatio).toBeGreaterThanOrEqual(7.0);
        expect(textOnBg).toBeGreaterThanOrEqual(7.0);
        expect(borderRatio).toBeGreaterThanOrEqual(3.0);
      });
    });

    describe('Advertencia (Warning / Offline / Cupos)', () => {
      it('cumple WCAG AAA para texto de advertencia sobre su fondo y AA para botón sólido', () => {
        const textOnBg = calculateContrastRatio(
          COLOR_PALETTE.status.warning.text,
          COLOR_PALETTE.status.warning.bg,
        );
        const textOnCanvas = calculateContrastRatio(
          COLOR_PALETTE.status.warning.text,
          COLOR_PALETTE.canvas,
        );
        const solidRatio = calculateContrastRatio(
          COLOR_PALETTE.status.warning.onSolid,
          COLOR_PALETTE.status.warning.solid,
        );
        const borderRatio = calculateContrastRatio(
          COLOR_PALETTE.status.warning.border,
          COLOR_PALETTE.canvas,
        );

        expect(textOnBg).toBeGreaterThanOrEqual(7.0);
        expect(textOnCanvas).toBeGreaterThanOrEqual(7.0);
        expect(solidRatio).toBeGreaterThanOrEqual(4.5); // AA holgado (6.85:1)
        expect(borderRatio).toBeGreaterThanOrEqual(3.0);
      });
    });

    describe('Peligro (Danger / QR Inválido / Error)', () => {
      it('cumple WCAG AAA para texto blanco sobre danger.solid y texto de peligro sobre su fondo', () => {
        const solidRatio = calculateContrastRatio(
          COLOR_PALETTE.status.danger.onSolid,
          COLOR_PALETTE.status.danger.solid,
        );
        const textOnBg = calculateContrastRatio(
          COLOR_PALETTE.status.danger.text,
          COLOR_PALETTE.status.danger.bg,
        );
        const borderRatio = calculateContrastRatio(
          COLOR_PALETTE.status.danger.border,
          COLOR_PALETTE.canvas,
        );

        expect(solidRatio).toBeGreaterThanOrEqual(7.0);
        expect(textOnBg).toBeGreaterThanOrEqual(7.0);
        expect(borderRatio).toBeGreaterThanOrEqual(3.0);
      });
    });

    describe('Información (Info / EPS / Habeas Data)', () => {
      it('cumple WCAG AAA para texto blanco sobre info.solid y texto info sobre su fondo', () => {
        const solidRatio = calculateContrastRatio(
          COLOR_PALETTE.status.info.onSolid,
          COLOR_PALETTE.status.info.solid,
        );
        const textOnBg = calculateContrastRatio(
          COLOR_PALETTE.status.info.text,
          COLOR_PALETTE.status.info.bg,
        );
        const borderRatio = calculateContrastRatio(
          COLOR_PALETTE.status.info.border,
          COLOR_PALETTE.canvas,
        );

        expect(solidRatio).toBeGreaterThanOrEqual(7.0);
        expect(textOnBg).toBeGreaterThanOrEqual(7.0);
        expect(borderRatio).toBeGreaterThanOrEqual(3.0);
      });
    });
  });

  describe('6. Foco de Alto Contraste (WCAG 2.4.7 y 2.4.11)', () => {
    it('el doble anillo garantiza contraste ≥ 3:1 tanto contra fondos claros como oscuros', () => {
      const haloAgainstFocus = calculateContrastRatio(
        COLOR_PALETTE.focus.halo,
        COLOR_PALETTE.focus.ring,
      );
      const haloAgainstWhite = calculateContrastRatio(
        COLOR_PALETTE.focus.halo,
        COLOR_PALETTE.canvas,
      );
      const focusAgainstBrand = calculateContrastRatio(
        COLOR_PALETTE.focus.ring,
        COLOR_PALETTE.brand.DEFAULT,
      );

      expect(haloAgainstFocus).toBeGreaterThanOrEqual(3.0);
      expect(haloAgainstWhite).toBeGreaterThanOrEqual(3.0);
      expect(focusAgainstBrand).toBeGreaterThanOrEqual(3.0);
    });
  });

  describe('8. Código QR del voucher (ADR-0017 §2)', () => {
    it('usa negro y blanco puros con el contraste máximo (21:1) que esperan los lectores', () => {
      expect(COLOR_PALETTE.qr.ink).toBe('#000000');
      expect(COLOR_PALETTE.qr.paper).toBe('#ffffff');
      expect(calculateContrastRatio(COLOR_PALETTE.qr.ink, COLOR_PALETTE.qr.paper)).toBe(21.0);
    });
  });
});
