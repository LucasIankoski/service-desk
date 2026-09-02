FROM node:24-alpine AS build
WORKDIR /workspace/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM caddy:2.10-alpine
COPY infra/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /workspace/frontend/dist /srv

RUN addgroup -S -g 10001 service-desk \
    && adduser -S -D -H -u 10001 -G service-desk service-desk \
    && chown -R service-desk:service-desk /srv /data /config

USER service-desk
EXPOSE 8443
