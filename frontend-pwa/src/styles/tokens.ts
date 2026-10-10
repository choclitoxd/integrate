/**
 * Tokens de diseño y colores para el Sistema Intégrate (ADR-0004, RNF-07, NTC 5854).
 *
 * Diseñados para una PWA Mobile-First con operación en campo bajo luz solar directa.
 * Cada par de color/fondo cuenta con certificación matemática de contraste WCAG 2.1 AA (≥ 4.5:1)
 * o AAA (≥ 7.0:1 para texto normal, garantizando legibilidad en exteriores).
 */

export const COLOR_PALETTE = {
  // 1. Marca Principal (Identidad comunitaria, selva y conservación andina)
  brand: {
    DEFAULT: '#14532d', // Verde bosque profundo (AAA sobre canvas y surface)
    hover: '#0f3d21', // Estado hover / interacción primaria
    active: '#052e16', // Estado presionado / active
    soft: '#dcfce7', // Fondo suave para tags, badges y selección
    border: '#166534', // Borde temático accesible
    onBrand: '#ffffff', // Texto de alto contraste sobre marca (9.11:1 AAA)
  },

  // 2. Acento Territorial (Caminos de herradura, arcilla, café y memoria histórica)
  accent: {
    DEFAULT: '#7c2d12', // Terracota profundo (9.37:1 AAA sobre canvas)
    medium: '#9a3412', // Terracota medio (7.31:1 AA holgado sobre canvas)
    hover: '#5c200c', // Hover de acento
    soft: '#ffedd5', // Fondo suave de etiqueta territorial
    onAccent: '#ffffff', // Texto sobre acento sólido (9.37:1 AAA)
  },

  // 3. Neutros y Superficies
  canvas: '#ffffff', // Fondo base de la aplicación (100% blanco)
  surface: {
    DEFAULT: '#f4f6f2', // Fondo de tarjetas y secciones con sutil matiz orgánico
    elevated: '#ffffff', // Superficies elevadas (modales, bottom sheets, popovers)
    subtle: '#f3f4f6', // Fondo de campos de formulario inactivos y chips neutros
    inverse: '#111827', // Fondo oscuro de contraste extremo
  },

  // 4. Tipografía y Tinta (Ink)
  ink: {
    DEFAULT: '#111827', // Texto principal (17.74:1 AAA sobre blanco)
    muted: '#374151', // Texto secundario / metadatos (10.31:1 AAA)
    subtle: '#4b5563', // Texto terciario / placeholders (7.56:1 AAA)
    inverse: '#ffffff', // Texto invertido para fondos oscuros o marca
  },

  // 5. Bordes y Divisores (WCAG 2.1 SC 1.4.11 ≥ 3.0:1)
  border: {
    DEFAULT: '#4b5563', // Borde estándar de campos e inputs (7.56:1)
    subtle: '#6b7280', // Divisores secundarios y separadores (4.83:1)
    strong: '#1f2937', // Bordes de alto contraste para luz solar (14.68:1)
  },

  // 6. Estados Semánticos (Validación transaccional y operativa en campo)
  status: {
    // Éxito: Canje de QR válido, pago confirmado, sincronización completada
    success: {
      solid: '#166534', // Botón o píldora sólida (7.13:1 con blanco AAA)
      bg: '#f0fdf4', // Fondo suave de alerta
      border: '#15803d', // Borde accesible de alerta (5.02:1)
      text: '#14532d', // Texto informativo sobre bg (8.70:1 AAA)
      onSolid: '#ffffff', // Texto sobre solid
    },
    // Advertencia: Conexión intermitente, padrón por expirar, cupos limitados
    warning: {
      solid: '#854d0e', // Botón o píldora sólida (6.85:1 con blanco AA)
      bg: '#fefce8', // Fondo suave de alerta
      border: '#a16207', // Borde accesible de alerta (4.92:1)
      text: '#713f12', // Texto de alerta sobre bg (8.38:1 AAA)
      onSolid: '#ffffff', // Texto sobre solid
    },
    // Peligro / Error: QR ya canjeado / inválido, cupo agotado, error de pasarela
    danger: {
      solid: '#991b1b', // Botón o píldora sólida (8.31:1 con blanco AAA)
      bg: '#fef2f2', // Fondo suave de alerta
      border: '#b91c1c', // Borde accesible de alerta (6.47:1)
      text: '#991b1b', // Texto de error sobre bg (7.60:1 AAA)
      onSolid: '#ffffff', // Texto sobre solid
    },
    // Información: Guía de viaje, EPS y contacto de emergencia, Habeas Data
    info: {
      solid: '#1e40af', // Botón o píldora sólida (8.72:1 con blanco AAA)
      bg: '#eff6ff', // Fondo suave de alerta
      border: '#2563eb', // Borde accesible de alerta (5.17:1)
      text: '#1e40af', // Texto de info sobre bg (8.01:1 AAA)
      onSolid: '#ffffff', // Texto sobre solid
    },
  },

  // 7. Foco Accesible (WCAG 2.4.7 y 2.4.11)
  focus: {
    ring: '#ffdd00', // Anillo amarillo de alta saturación
    halo: '#111827', // Halo exterior oscuro que garantiza contraste en cualquier fondo
  },

  // 8. Código QR del voucher (ADR-0017 §2). Uso exclusivo: los lectores esperan negro y blanco puros
  // (ADR-0010 §6). No usar como colores de interfaz.
  qr: {
    ink: '#000000', // Módulos oscuros
    paper: '#ffffff', // Fondo y margen de 4 módulos (21:1 con ink)
  },
} as const;

/**
 * Convierte un código hexadecimal (#RRGGBB) a una tupla de valores numéricos RGB [0-255].
 */
export function hexToRgb(hex: string): readonly [number, number, number] {
  const sanitized = hex.replace('#', '');
  const r = parseInt(sanitized.substring(0, 2), 16);
  const g = parseInt(sanitized.substring(2, 4), 16);
  const b = parseInt(sanitized.substring(4, 6), 16);
  return [r, g, b] as const;
}

/**
 * Calcula la luminancia relativa según la fórmula oficial de WCAG 2.1 (sRGB).
 * @see https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
 */
export function calculateRelativeLuminance(hex: string): number {
  const [rRaw, gRaw, bRaw] = hexToRgb(hex);
  const channels = [rRaw, gRaw, bRaw].map((channel) => {
    const sRGB = channel / 255;
    return sRGB <= 0.04045 ? sRGB / 12.92 : Math.pow((sRGB + 0.055) / 1.055, 2.4);
  });

  const [r, g, b] = channels;
  if (r === undefined || g === undefined || b === undefined) {
    throw new Error(`Color hexadecimal inválido: ${hex}`);
  }
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/**
 * Calcula el ratio de contraste entre dos colores según la fórmula WCAG 2.1:
 * (L1 + 0.05) / (L2 + 0.05), donde L1 es la luminancia del color más claro.
 * @returns Ratio numérico con 2 decimales (ej: 7.13 para 7.13:1).
 */
export function calculateContrastRatio(hex1: string, hex2: string): number {
  const lum1 = calculateRelativeLuminance(hex1);
  const lum2 = calculateRelativeLuminance(hex2);
  const lighter = Math.max(lum1, lum2);
  const darker = Math.min(lum1, lum2);
  return Number(((lighter + 0.05) / (darker + 0.05)).toFixed(2));
}
