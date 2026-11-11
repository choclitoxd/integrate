import { Link } from 'react-router';

import { buttonClassName, Icon } from '@/shared/ui';

import type { ExperienceDetail, PhysicalIntensity } from '../api/types';
import { placeLabel } from '../labels';
import { useCategories } from '../queries';
import { MeetingPointMap } from './MeetingPointMap';

const AUDIENCE_LABELS: Record<PhysicalIntensity, string> = {
  LOW: 'Apta para principiantes.',
  MEDIUM: 'Requiere una condición física moderada.',
  HIGH: 'Requiere buena condición física.',
};

function MapLinks({ latitude, longitude }: { latitude: number; longitude: number }) {
  const coords = `${latitude.toFixed(2)},${longitude.toFixed(2)}`;
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

/** Miga de pan accesible (WCAG 2.4.8, ADR-0050) para orientar territorialmente al visitante. */
export function DetailBreadcrumbs({ detail }: { detail: ExperienceDetail }) {
  const { summary } = detail;
  return (
    <nav aria-label="Migas de pan" className="mb-4">
      <ol className="flex flex-wrap items-center gap-1.5 text-xs text-ink-muted sm:text-sm">
        <li>
          <Link
            to="/"
            className="inline-flex min-h-11 items-center gap-1 font-semibold text-brand hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
          >
            <Icon name="chevronLeft" size={16} />
            Explorar
          </Link>
        </li>
        <li aria-hidden="true" className="text-ink-subtle">
          /
        </li>
        <li>
          <span>{summary.department.name}</span>
        </li>
        <li aria-hidden="true" className="text-ink-subtle">
          /
        </li>
        <li className="font-semibold text-ink" aria-current="page">
          <span>{summary.municipality.name}</span>
        </li>
      </ol>
    </nav>
  );
}

/** Micro-garantías de confianza y transparencia para acompañar la reserva (ADR-0050). */
export function ExperienceGuarantees({ variant = 'full' }: { variant?: 'full' | 'compact' }) {
  if (variant === 'compact') {
    return (
      <div className="mt-3 rounded-lg border border-border-subtle bg-surface px-3 py-2 text-xs text-ink-muted">
        <ul className="grid gap-1.5 sm:grid-cols-2">
          <li className="flex items-center gap-1.5">
            <Icon name="check" size={14} className="shrink-0 text-brand" />
            <span>Confirmación inmediata</span>
          </li>
          <li className="flex items-center gap-1.5">
            <Icon name="refresh" size={14} className="shrink-0 text-brand" />
            <span>Cancelación flexible</span>
          </li>
          <li className="flex items-center gap-1.5">
            <Icon name="leaf" size={14} className="shrink-0 text-brand" />
            <span>Aporte comunitario 85 %</span>
          </li>
          <li className="flex items-center gap-1.5">
            <Icon name="shield" size={14} className="shrink-0 text-brand" />
            <span>Sin cobros ocultos</span>
          </li>
        </ul>
      </div>
    );
  }

  return (
    <div className="mt-6 border-t border-border-subtle pt-5 text-xs text-ink-muted">
      <h3 className="text-xs font-bold uppercase tracking-wider text-ink">
        Garantías de tu reserva
      </h3>
      <ul className="mt-3 space-y-2.5">
        <li className="flex items-start gap-2">
          <Icon name="check" size={16} className="mt-0.5 shrink-0 text-brand" />
          <span>
            <strong className="text-ink">Confirmación inmediata:</strong> Comprobante digital y
            voucher emitidos al instante.
          </span>
        </li>
        <li className="flex items-start gap-2">
          <Icon name="refresh" size={16} className="mt-0.5 shrink-0 text-brand" />
          <span>
            <strong className="text-ink">Cancelación con reembolso:</strong> Si cancelas con más de
            72 horas de anticipación te devolvemos el 85 %; con menos tiempo no hay reembolso.
          </span>
        </li>
        <li className="flex items-start gap-2">
          <Icon name="leaf" size={16} className="mt-0.5 shrink-0 text-brand" />
          <span>
            <strong className="text-ink">Turismo regenerativo:</strong> Aforo controlado para
            proteger el ecosistema andino.
          </span>
        </li>
        <li className="flex items-start gap-2">
          <Icon name="shield" size={16} className="mt-0.5 shrink-0 text-brand" />
          <span>
            <strong className="text-ink">Tarifa transparente:</strong> Sin comisiones sorpresa al
            momento de pagar.
          </span>
        </li>
      </ul>
    </div>
  );
}

/** Título, territorio, asociación y categorías de la ficha (T-1.10a, ADR-0050). */
export function DetailHeader({ detail }: { detail: ExperienceDetail }) {
  const { summary } = detail;
  const categories = useCategories();
  const names = summary.categoryCodes
    .map((code) => categories.data?.find((category) => category.code === code)?.name)
    .filter((name): name is string => Boolean(name));

  const initial = summary.associationName ? summary.associationName.charAt(0).toUpperCase() : 'A';

  return (
    <>
      <title>{`${summary.title} · Intégrate`}</title>
      <DetailBreadcrumbs detail={detail} />
      <h1 className="text-3xl leading-tight font-bold text-balance text-ink">{summary.title}</h1>
      <p className="mt-2 flex items-start gap-1.5 text-lg font-semibold text-accent">
        <Icon name="mapPin" className="mt-1" />
        {placeLabel(summary)}
      </p>

      <div className="mt-3 flex items-center gap-3">
        <div
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-brand-soft text-base font-bold text-brand"
          aria-hidden="true"
        >
          {initial}
        </div>
        <div>
          <p className="text-sm text-ink-muted">
            Organiza <span className="font-semibold text-ink">{summary.associationName}</span>
          </p>
          <span className="inline-flex items-center gap-1 text-xs font-semibold text-brand">
            <Icon name="check" size={14} />
            Organización comunitaria verificada
          </span>
        </div>
      </div>

      {names.length > 0 && (
        <ul className="mt-4 flex flex-wrap gap-2" aria-label="Categorías">
          {names.map((name) => (
            <li
              key={name}
              className="rounded-full bg-surface px-3 py-1 text-xs font-semibold text-ink-muted"
            >
              {name}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

/** Descripción, ubicación pública y recomendaciones de seguridad (ADR-0039, ADR-0050). */
export function DetailSections({ detail }: { detail: ExperienceDetail }) {
  const latitude = detail.latitude === null ? null : Number(detail.latitude.toFixed(2));
  const longitude = detail.longitude === null ? null : Number(detail.longitude.toFixed(2));
  const showMap =
    detail.meetingPointCoordinatesTrusted !== false && latitude !== null && longitude !== null;
  return (
    <>
      <section aria-labelledby="sobre" className="mt-8">
        <h2 id="sobre" className="text-xl font-bold text-ink">
          Sobre la experiencia
        </h2>
        <p className="mt-2 max-w-prose whitespace-pre-line text-ink">{detail.description}</p>
        <p className="mt-3 max-w-prose text-ink-muted">
          {AUDIENCE_LABELS[detail.summary.physicalIntensity]}
        </p>
      </section>

      <section aria-labelledby="que-incluye" className="mt-8">
        <h2 id="que-incluye" className="text-xl font-bold text-ink">
          ¿Qué incluye esta experiencia?
        </h2>
        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          <div className="flex items-start gap-3 rounded-xl border border-border-subtle bg-surface p-3.5">
            <Icon name="user" size={22} className="mt-0.5 shrink-0 text-brand" />
            <div>
              <p className="font-semibold text-ink text-sm">Guía local comunitario</p>
              <p className="mt-0.5 text-xs text-ink-muted">
                Acompañamiento continuo por líder o anfitrión conocedor del territorio y la memoria
                viva.
              </p>
            </div>
          </div>

          <div className="flex items-start gap-3 rounded-xl border border-border-subtle bg-surface p-3.5">
            <Icon name="shield" size={22} className="mt-0.5 shrink-0 text-brand" />
            <div>
              <p className="font-semibold text-ink text-sm">Póliza de asistencia médica</p>
              <p className="mt-0.5 text-xs text-ink-muted">
                Cobertura ante eventualidades y primeros auxilios durante el recorrido en campo.
              </p>
            </div>
          </div>

          <div className="flex items-start gap-3 rounded-xl border border-border-subtle bg-surface p-3.5">
            <Icon name="ticket" size={22} className="mt-0.5 shrink-0 text-brand" />
            <div>
              <p className="font-semibold text-ink text-sm">Refrigerio tradicional</p>
              <p className="mt-0.5 text-xs text-ink-muted">
                Muestra o hidratación tradicional campesina preparada por familias de la zona.
              </p>
            </div>
          </div>

          <div className="flex items-start gap-3 rounded-xl border border-border-subtle bg-surface p-3.5">
            <Icon name="leaf" size={22} className="mt-0.5 shrink-0 text-brand" />
            <div>
              <p className="font-semibold text-ink text-sm">Tarifa de conservación</p>
              <p className="mt-0.5 text-xs text-ink-muted">
                Ingreso al sendero y aporte directo al cuidado del ecosistema y caminos ancestrales.
              </p>
            </div>
          </div>
        </div>
      </section>

      <section
        aria-labelledby="aporte"
        className="mt-8 rounded-xl border-2 border-brand/20 bg-surface p-5"
      >
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-brand-soft px-3 py-1 text-xs font-bold uppercase tracking-wider text-brand">
            <Icon name="leaf" size={14} />
            Economía solidaria y justa
          </span>
          <span className="text-sm font-bold text-brand tabular-nums">
            85 % directo a la comunidad
          </span>
        </div>

        <h2 id="aporte" className="mt-3 text-lg font-bold text-ink">
          Tu aporte a la comunidad
        </h2>
        <p className="mt-1 text-sm text-ink">
          El 85 % del pago llega directamente a la asociación o al anfitrión que organiza la
          experiencia, fortaleciendo el tejido social y la economía campesina.
        </p>

        {/* Barra visual de distribución ética */}
        <div className="mt-4">
          <div
            role="meter"
            aria-label="Distribución del pago: 85 % comunidad, 15 % plataforma y seguros"
            aria-valuenow={85}
            aria-valuemin={0}
            aria-valuemax={100}
            className="flex h-3 w-full overflow-hidden rounded-full bg-border-subtle/30"
          >
            <div className="h-full w-[85%] bg-brand" title="85 % Asociación y anfitriones" />
            <div className="h-full w-[15%] bg-accent" title="15 % Plataforma y seguros" />
          </div>
          <div className="mt-2 flex flex-wrap items-center justify-between gap-2 text-xs text-ink-muted">
            <span className="flex items-center gap-1.5">
              <span className="inline-block h-2 w-2 rounded-full bg-brand" aria-hidden="true" />
              <strong className="text-ink">85 %</strong> Familias anfitrionas y conservación
            </span>
            <span className="flex items-center gap-1.5">
              <span className="inline-block h-2 w-2 rounded-full bg-accent" aria-hidden="true" />
              <strong className="text-ink">15 %</strong> Operación, póliza y plataforma
            </span>
          </div>
        </div>
      </section>

      <section aria-labelledby="encuentro" className="mt-8">
        <h2 id="encuentro" className="text-xl font-bold text-ink">
          Zona de la experiencia
        </h2>
        {/* Misma tarjeta plana y mismo patrón de lista que los datos de la ficha (ExperienceFacts). */}
        <div className="mt-3 rounded-xl bg-surface px-4 py-4">
          <dl className="grid gap-4">
            <div className="grid grid-cols-[auto_1fr] items-center gap-x-3">
              <Icon name="map" size={24} className="row-span-2 text-brand" />
              <dt className="text-sm text-ink-muted">Ubicación</dt>
              <dd className="font-semibold text-ink">{placeLabel(detail.summary)}</dd>
            </div>
            {showMap && (
              <div className="grid grid-cols-[auto_1fr] items-center gap-x-3">
                <Icon name="crosshair" size={24} className="row-span-2 text-brand" />
                <dt className="text-sm text-ink-muted">Coordenadas aproximadas</dt>
                <dd className="font-semibold text-ink tabular-nums">
                  {latitude.toFixed(2)}, {longitude.toFixed(2)}
                </dd>
              </div>
            )}
          </dl>
          <p className="mt-3 text-ink-muted">
            Consulta el punto exacto en el comprobante de tu reserva confirmada.
          </p>
          {showMap && (
            <>
              <MeetingPointMap
                latitude={latitude}
                longitude={longitude}
                title={detail.summary.title}
              />
              <MapLinks latitude={latitude} longitude={longitude} />
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
