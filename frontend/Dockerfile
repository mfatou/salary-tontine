# ---------------------------------------------------------------------------
# Frontend SalaryTontine - build Vite puis service statique via Nginx
# ---------------------------------------------------------------------------

FROM node:22-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# L'URL de l'API est figee au build : Vite remplace les references
# import.meta.env par leur valeur litterale.
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# Image nginx officielle non privilegiee : le processus tourne sous l'uid 101
# et non sous root. Un port inferieur a 1024 exigerait des privileges, d'ou
# l'ecoute sur 8080 - le port publie vers l'hote reste 5173.
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime

COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# L'image de base applique deja cet utilisateur. La directive est repetee ici
# pour que le Dockerfile porte lui-meme sa garantie : un changement d'image de
# base ne peut plus ramener le conteneur a root sans que cette ligne saute.
USER 101

EXPOSE 8080

# L'adresse IPv4 est explicite : "localhost" resout d'abord en ::1 dans le
# conteneur, alors que nginx n'ecoute que sur IPv4.
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/ >/dev/null 2>&1 || exit 1
