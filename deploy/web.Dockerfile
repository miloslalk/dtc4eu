# DTC4EU web tier: builds BOTH SPAs and serves them through Caddy with
# automatic HTTPS. Build from repo root:
#   docker build -f deploy/web.Dockerfile .
FROM node:24-alpine AS platform-build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ .
RUN npm run build

FROM node:24-alpine AS website-build
WORKDIR /app
COPY website/frontend/package.json website/frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY website/frontend/ .
RUN npm run build

FROM caddy:2
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=platform-build /app/dist /srv/platform
COPY --from=website-build /app/dist /srv/website
