/// <référence types="vite/client" />

interface ImportMetaEnv {
  /** URL de base de l'API backend, injectee au build par Vite. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
