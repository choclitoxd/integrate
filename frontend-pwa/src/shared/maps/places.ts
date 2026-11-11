/**
 * Comparación de nombres de lugares entre Google Maps y DIVIPOLA (ADR-0056, enmienda de la Fase 3).
 * Funciones puras: Google escribe "Bogotá" y el DANE "BOGOTÁ, D.C."; "Medellín" y "MEDELLÍN".
 */

export interface NamedPlace {
  readonly code: string;
  readonly name: string;
}

/** Minúsculas, sin tildes ni signos, sin "D.C." ni "Distrito Capital", con espacios simples. */
export function normalizePlaceName(name: string): string {
  return name
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/\bdistrito capital\b/g, ' ')
    .replace(/\bd\.?\s?c\.?(?=\s|$|,)/g, ' ')
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/** El único lugar cuyo nombre normalizado coincide; `undefined` si no hay o hay más de uno. */
export function findPlaceByName<T extends NamedPlace>(
  places: readonly T[],
  name: string | undefined,
): T | undefined {
  if (!name) return undefined;
  const wanted = normalizePlaceName(name);
  if (wanted === '') return undefined;
  const matches = places.filter((place) => normalizePlaceName(place.name) === wanted);
  return matches.length === 1 ? matches[0] : undefined;
}

function mentions(text: string, placeName: string): boolean {
  const wanted = normalizePlaceName(placeName);
  if (wanted.length < 4) return false;
  return new RegExp(`(^|\\s)${wanted.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(\\s|$)`).test(text);
}

/**
 * Nombre de otro municipio del mismo departamento que la dirección escrita menciona, o `undefined`.
 * Si la dirección ya nombra el municipio detectado, no hay aviso. Es solo una advertencia (ADR-0030).
 */
export function otherMunicipalityMentioned(
  address: string,
  detected: NamedPlace,
  municipalities: readonly NamedPlace[],
): string | undefined {
  const text = normalizePlaceName(address);
  if (text === '' || mentions(text, detected.name)) return undefined;
  return municipalities.find((place) => place.code !== detected.code && mentions(text, place.name))
    ?.name;
}
