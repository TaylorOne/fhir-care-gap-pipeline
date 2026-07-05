# Build args exist so restricted environments (corporate mirrors, CI proxies)
# can substitute a registry mirror, e.g.
#   --build-arg BASE_NODE=mirror.gcr.io/library/node:22-alpine
ARG BASE_NODE=node:22-alpine
ARG BASE_NGINX=nginx:1.29-alpine

FROM ${BASE_NODE} AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM ${BASE_NGINX}
# Cloud Run injects PORT; the template listens on it via nginx's built-in envsubst.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
# Rewrites env.js from API_URL at container start (runtime config, not build config).
COPY docker/40-write-env.sh /docker-entrypoint.d/40-write-env.sh
COPY --from=build /app/dist/dashboard/browser /usr/share/nginx/html
RUN chmod +x /docker-entrypoint.d/40-write-env.sh
ENV PORT=8080
EXPOSE 8080
