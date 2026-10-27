/**
 * Celulares con indicativo del país. El backend exige E.164 (`^\+[1-9][0-9]{7,14}$`, sin espacios);
 * aquí el número se captura en dos partes —indicativo y número nacional— y el largo permitido
 * depende del país elegido.
 */

export interface PhoneCountry {
  readonly iso: string;
  readonly name: string;
  /** Indicativo sin "+": "57". */
  readonly dial: string;
  /** Dígitos del número nacional, sin indicativo ni "0" de troncal. */
  readonly min: number;
  readonly max: number;
}

/**
 * Países desde los que más se viaja hacia Colombia y los vecinos. Los indicativos de E.164 son
 * libres de prefijo (ninguno empieza con otro), así que un número completo se separa sin ambigüedad.
 */
export const PHONE_COUNTRIES: readonly PhoneCountry[] = [
  { iso: 'CO', name: 'Colombia', dial: '57', min: 10, max: 10 },
  { iso: 'VE', name: 'Venezuela', dial: '58', min: 10, max: 10 },
  { iso: 'EC', name: 'Ecuador', dial: '593', min: 9, max: 9 },
  { iso: 'PE', name: 'Perú', dial: '51', min: 9, max: 9 },
  { iso: 'PA', name: 'Panamá', dial: '507', min: 8, max: 8 },
  { iso: 'CR', name: 'Costa Rica', dial: '506', min: 8, max: 8 },
  { iso: 'BR', name: 'Brasil', dial: '55', min: 10, max: 11 },
  { iso: 'MX', name: 'México', dial: '52', min: 10, max: 10 },
  { iso: 'AR', name: 'Argentina', dial: '54', min: 10, max: 11 },
  { iso: 'CL', name: 'Chile', dial: '56', min: 9, max: 9 },
  { iso: 'BO', name: 'Bolivia', dial: '591', min: 8, max: 8 },
  { iso: 'PY', name: 'Paraguay', dial: '595', min: 9, max: 9 },
  { iso: 'UY', name: 'Uruguay', dial: '598', min: 8, max: 9 },
  { iso: 'US', name: 'Estados Unidos y Canadá', dial: '1', min: 10, max: 10 },
  { iso: 'ES', name: 'España', dial: '34', min: 9, max: 9 },
  { iso: 'FR', name: 'Francia', dial: '33', min: 9, max: 9 },
  { iso: 'DE', name: 'Alemania', dial: '49', min: 10, max: 11 },
  { iso: 'GB', name: 'Reino Unido', dial: '44', min: 10, max: 10 },
  { iso: 'IT', name: 'Italia', dial: '39', min: 9, max: 10 },
];

export const DEFAULT_PHONE_COUNTRY: PhoneCountry = PHONE_COUNTRIES[0] ?? {
  iso: 'CO',
  name: 'Colombia',
  dial: '57',
  min: 10,
  max: 10,
};

const E164 = /^\+[1-9][0-9]{7,14}$/;

export const GENERIC_PHONE_MESSAGE = 'Escribe un celular válido, por ejemplo +57 300 123 4567.';

export function countryByDial(dial: string): PhoneCountry | undefined {
  return PHONE_COUNTRIES.find((country) => country.dial === dial);
}

/** País de un número que empieza por "+" y su indicativo; `undefined` si no está en la lista. */
function countryOfE164(compact: string): PhoneCountry | undefined {
  if (!compact.startsWith('+')) return undefined;
  const digits = compact.slice(1);
  return PHONE_COUNTRIES.find((country) => digits.startsWith(country.dial));
}

/** "10 dígitos" o "10 u 11 dígitos". */
export function lengthText(country: PhoneCountry): string {
  return country.min === country.max
    ? `${String(country.max)} dígitos`
    : `${String(country.min)} u ${String(country.max)} dígitos`;
}

/** Solo dígitos, recortados al máximo del país: lo que pasa de ahí no se admite. */
export function nationalDigits(raw: string, country: PhoneCountry): string {
  return raw.replace(/\D/g, '').slice(0, country.max);
}

export interface PhoneParts {
  readonly country: PhoneCountry;
  readonly national: string;
}

/**
 * Separa lo escrito en país y número nacional. Acepta "+57 300 123 4567", "+573001234567" y el
 * valor inicial "+57 ". Sin indicativo reconocible conserva `fallback` y trata todo como nacional.
 */
export function splitPhone(
  value: string,
  fallback: PhoneCountry = DEFAULT_PHONE_COUNTRY,
): PhoneParts {
  const compact = value.replace(/[\s()-]/g, '');
  const country = countryOfE164(compact);
  if (country) {
    return { country, national: nationalDigits(compact.slice(1 + country.dial.length), country) };
  }
  return { country: fallback, national: nationalDigits(compact, fallback) };
}

/** "+57 3001234567": el espacio separa el indicativo y `normalizePhone` lo quita al validar. */
export function joinPhone(country: PhoneCountry, national: string): string {
  return `+${country.dial} ${national}`;
}

/**
 * Motivo por el que un número E.164 compacto no es válido, o `null` si lo es. Para un país de la
 * lista exige su largo; para cualquier otro indicativo solo el formato E.164 que pide el backend.
 */
export function phoneProblem(compact: string): string | null {
  const country = countryOfE164(compact);
  if (country) {
    const national = compact.slice(1 + country.dial.length);
    const valid =
      /^\d+$/.test(national) && national.length >= country.min && national.length <= country.max;
    return valid
      ? null
      : `Escribe un celular de ${lengthText(country)} para ${country.name} (+${country.dial}).`;
  }
  return E164.test(compact) ? null : GENERIC_PHONE_MESSAGE;
}

/** "+57 300 123 4567" para mostrar; los demás países, "+indicativo número". */
export function formatPhoneDisplay(e164: string): string {
  const country = countryOfE164(e164);
  if (!country) return e164;
  const national = e164.slice(1 + country.dial.length);
  if (country.iso === 'CO') {
    const match = /^(\d{3})(\d{3})(\d{4})$/.exec(national);
    if (match) return `+57 ${match[1] ?? ''} ${match[2] ?? ''} ${match[3] ?? ''}`;
  }
  return `+${country.dial} ${national}`;
}
