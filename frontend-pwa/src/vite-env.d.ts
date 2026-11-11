/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  /** Llave de Maps Embed API (pública, restringida por dominio). Sin ella, el mapa usa OpenStreetMap. */
  readonly VITE_GOOGLE_MAPS_EMBED_KEY?: string;
  /** Llave de navegador de Maps JavaScript API y Places (pública, restringida por referrer). Sin ella, el selector usa captura manual. */
  readonly VITE_GOOGLE_MAPS_BROWSER_KEY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
