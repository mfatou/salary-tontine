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

FROM nginx:1.27-alpine AS runtime

COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# L'adresse IPv4 est explicite : "localhost" resout d'abord en ::1 dans le
# conteneur, alors que nginx n'ecoute que sur IPv4.
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- http://127.0.0.1/ >/dev/null 2>&1 || exit 1
