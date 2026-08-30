import axios, { AxiosError } from 'axios';
import type { ApiError } from '../types';

/**
 * Instance Axios unique de l'application.
 *
 * `withCredentials` est indispensable : le jeton d'authentification voyage dans
 * un cookie HttpOnly depose par le backend, jamais dans le localStorage.
 */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
});

const UNAUTHORIZED = 401;
const LOGIN_PATH = '/login';
const PUBLIC_PATHS = ['/login', '/register'];

/** Chemins d'API dont un 401 est un resultat normal et ne doit pas rediriger. */
const SILENT_401_ENDPOINTS = ['/api/auth/me', '/api/auth/login'];

const DEFAULT_ERROR_MESSAGE = "Une erreur est survenue. Merci de reessayer.";

/**
 * Extrait un message lisible d'une erreur Axios.
 * Les erreurs de validation champ par champ sont concatenees.
 */
export function extractErrorMessage(error: unknown): string {
  if (!axios.isAxiosError(error)) {
    return DEFAULT_ERROR_MESSAGE;
  }
  const axiosError = error as AxiosError<ApiError>;
  const data = axiosError.response?.data;

  if (data?.validationErrors) {
    const messages = Object.values(data.validationErrors);
    if (messages.length > 0) {
      return messages.join(' ');
    }
  }
  if (data?.message) {
    return data.message;
  }
  if (!axiosError.response) {
    return "Le serveur est injoignable. Verifiez que le backend est demarre.";
  }
  return DEFAULT_ERROR_MESSAGE;
}

/**
 * Une session expiree renvoie l'utilisateur vers la page de connexion.
 * Le controle d'accès réel reste assure par le backend.
 */
apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const requestUrl = error.config?.url ?? '';
    const isSilentEndpoint = SILENT_401_ENDPOINTS.some((path) => requestUrl.includes(path));
    const isOnPublicPage = PUBLIC_PATHS.includes(window.location.pathname);

    if (error.response?.status === UNAUTHORIZED && !isSilentEndpoint && !isOnPublicPage) {
      window.location.assign(LOGIN_PATH);
    }
    return Promise.reject(error);
  },
);
