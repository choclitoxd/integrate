import { buttonClassName, Icon } from '@/shared/ui';

import type { ExperienceDetail } from '../api/types';
import { placeLabel } from '../labels';
import { useCategories } from '../queries';
import { MeetingPointMap } from './MeetingPointMap';

function MapLinks({
  latitude,
  longitude,
  addressQuery,
}: {
  latitude: number;
  longitude: number;
  addressQuery: string | undefined;
}) {
  const coords = addressQuery ?? `${String(latitude)},${String(longitude)}`;
  return (
    <div className="mt-4">
      {/*
        Enlace web (no `geo:`, que solo funciona en teléfonos con una app registrada): en
        computador abre Google Maps en el navegador y en celular el sistema lo entrega a la app de
        Google Maps si está instalada.
      */}
      <a
        href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(coords)}`}
        target="_blank"
        rel="noreferrer"
        className={buttonClassName('secondary', 'w-full')}
      >
        <Icon name="mapPin" />
        Abrir en Google Maps
        <Icon name="external" size={18} />
        <span className="sr-only">(se abre en otra pestaña)</span>
      </a>
    </div>
  );
}

/** Título, territorio, asociación y categorías de la ficha (T-1.10a). */
export function DetailHeader({ detail }: { detail: ExperienceDetail }) {
  const { summary } = detail;
  const categories = useCategories();
  const names = summary.categoryCodes
    .map((code) => categories.data?.find((category) => category.code === code)?.name)
    .filter((name): name is string => Boolean(name));

  return (
    <>
      <title>{`${summary.title} · Intégrate`}</title>
      <h1 className="text-3xl leading-tight font-bold text-balance text-ink">{summary.title}</h1>
      <p className="mt-2 flex items-start gap-1.5 text-lg font-semibold text-accent">
        <Icon name="mapPin" className="mt-1" />
        {placeLabel(summary)}
      </p>
      <p className="mt-1 text-ink-muted">
        Organiza <span className="font-semibold text-ink">{summary.associationName}</span>
      </p>

      {names.length > 0 && (
        <ul className="mt-3 flex flex-wrap gap-2" aria-label="Categorías">
          {names.map((name) => (
            <li
              key={name}
              className="rounded-full bg-surface px-2.5 py-0.5 text-sm font-medium text-ink-muted"
            >
              {name}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

/** Descripción, punto de encuentro y recomendaciones de seguridad. */
/**
 * Dirección escrita para ubicar el punto cuando las coordenadas no coinciden con ella (ADR-0030 §5); `undefined`
 * si las coordenadas son de fiar o el servidor no informa la verificación.
 */
function untrustedAddress(detail: ExperienceDetail): string | undefined {
  if (detail.meetingPointCoordinatesTrusted !== false) return undefined;
  const { municipality, department } = detail.summary;
  return [detail.meetingPoint, municipality.name, department.name, 'Colombia'].join(', ');
}

export function DetailSections({ detail }: { detail: ExperienceDetail }) {
  const addressQuery = untrustedAddress(detail);
  return (
    <>
      <section aria-labelledby="sobre" className="mt-8">
        <h2 id="sobre" className="text-xl font-bold text-ink">
          Sobre la experiencia
        </h2>
        <p className="mt-2 max-w-prose whitespace-pre-line text-ink">{detail.description}</p>
      </section>

      {detail.stops.length > 0 && (
        <section aria-labelledby="paradas" className="mt-8">
          <h2 id="paradas" className="text-xl font-bold text-ink">
            Paradas del recorrido
          </h2>
          <ol className="mt-3 grid gap-3 rounded-xl bg-surface px-4 py-4">
            {detail.stops.map((stop, index) => (
              <li key={`${String(index)}-${stop.name}`} className="flex items-start gap-3">
                <span
                  aria-hidden="true"
                  className="flex size-8 shrink-0 items-center justify-center rounded-full bg-brand font-bold text-on-brand"
                >
                  {index + 1}
                </span>
                <div>
                  <p className="font-semibold text-ink">{stop.name}</p>
                  {stop.description && <p className="text-ink-muted">{stop.description}</p>}
                </div>
              </li>
            ))}
          </ol>
        </section>
      )}

      <section aria-labelledby="encuentro" className="mt-8">
        <h2 id="encuentro" className="text-xl font-bold text-ink">
          Punto de encuentro
        </h2>
        {/* Misma tarjeta plana y mismo patrón de lista que los datos de la ficha (ExperienceFacts). */}
        <div className="mt-3 rounded-xl bg-surface px-4 py-4">
          <dl className="grid gap-4">
            <div className="grid grid-cols-[auto_1fr] items-center gap-x-3">
              <Icon name="flag" size={24} className="row-span-2 text-brand" />
              <dt className="text-sm text-ink-muted">Punto de referencia</dt>
              <dd className="font-semibold whitespace-pre-line text-ink">{detail.meetingPoint}</dd>
            </div>
            <div className="grid grid-cols-[auto_1fr] items-center gap-x-3">
              <Icon name="map" size={24} className="row-span-2 text-brand" />
              <dt className="text-sm text-ink-muted">Ubicación</dt>
              <dd className="font-semibold text-ink">
                {detail.vereda ? `Vereda ${detail.vereda}, ` : ''}
                {placeLabel(detail.summary)}
              </dd>
            </div>
            {detail.latitude !== null &&
              detail.longitude !== null &&
              addressQuery === undefined && (
                <div className="grid grid-cols-[auto_1fr] items-center gap-x-3">
                  <Icon name="crosshair" size={24} className="row-span-2 text-brand" />
                  <dt className="text-sm text-ink-muted">Coordenadas</dt>
                  <dd className="font-semibold text-ink tabular-nums">
                    {detail.latitude.toFixed(5)}, {detail.longitude.toFixed(5)}
                  </dd>
                </div>
              )}
          </dl>
          {detail.latitude !== null && detail.longitude !== null && (
            <>
              <MeetingPointMap
                latitude={detail.latitude}
                longitude={detail.longitude}
                title={detail.summary.title}
                {...(addressQuery === undefined ? {} : { addressQuery })}
              />
              <MapLinks
                latitude={detail.latitude}
                longitude={detail.longitude}
                addressQuery={addressQuery}
              />
            </>
          )}
        </div>
      </section>

      <section
        aria-labelledby="que-llevar"
        className="mt-8 rounded-xl border-2 border-info-border bg-info-bg px-4 py-4"
      >
        <h2 id="que-llevar" className="flex items-center gap-2 text-lg font-bold text-info-text">
          <Icon name="backpack" size={22} />
          ¿Qué llevar y recomendaciones?
        </h2>

        <p className="mt-1 text-sm text-ink-muted">
          Recomendaciones sobre tipo de ropa, implementos e hidratación para tu recorrido:
        </p>

        {/* Indicaciones personalizadas de la experiencia */}
        <div className="mt-3 rounded-lg border border-info-border/40 bg-surface/90 p-3">
          <span className="block text-xs font-semibold uppercase tracking-wider text-info-text">
            Indicaciones del anfitrión
          </span>
          <p className="mt-1 whitespace-pre-line font-medium text-ink">{detail.safetyGuidelines}</p>
        </div>

        {/* Guía práctica de preparación */}
        <div className="mt-4 grid gap-3 text-sm text-ink sm:grid-cols-2">
          <div className="flex items-start gap-2.5">
            <Icon name="check" size={18} className="mt-0.5 shrink-0 text-accent" />
            <div>
              <p className="font-semibold text-ink">Ropa recomendada</p>
              <p className="text-ink-muted">
                Prendas cómodas para caminar, preferiblemente pantalón y camisa de manga larga.
              </p>
            </div>
          </div>
          <div className="flex items-start gap-2.5">
            <Icon name="check" size={18} className="mt-0.5 shrink-0 text-accent" />
            <div>
              <p className="font-semibold text-ink">Calzado adecuado</p>
              <p className="text-ink-muted">
                Zapatos cerrados o botas de senderismo con suela antideslizante.
              </p>
            </div>
          </div>
          <div className="flex items-start gap-2.5">
            <Icon name="check" size={18} className="mt-0.5 shrink-0 text-accent" />
            <div>
              <p className="font-semibold text-ink">Hidratación</p>
              <p className="text-ink-muted">
                Llevar termo o botella reutilizable con agua suficiente para toda la jornada.
              </p>
            </div>
          </div>
          <div className="flex items-start gap-2.5">
            <Icon name="check" size={18} className="mt-0.5 shrink-0 text-accent" />
            <div>
              <p className="font-semibold text-ink">Implementos y protección</p>
              <p className="text-ink-muted">
                Gorra o sombrero, bloqueador solar, repelente y capa impermeable por si llueve.
              </p>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
