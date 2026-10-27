/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  /** Llave de Maps Embed API (pública, restringida por dominio). Sin ella, el mapa usa OpenStreetMap. */
  readonly VITE_GOOGLE_MAPS_EMBED_KEY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
