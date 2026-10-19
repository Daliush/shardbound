# The test client, built once and served by nginx, which also proxies /api and /ws to the game server.
# For docker compose and Google Cloud Run (frontend/README.md).

FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
# The nginx image turns templates/*.template into conf.d/* at startup, filling in the environment variables.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist/shardbound/browser /usr/share/nginx/html
ENV PORT=8080
ENV API_URL=http://api:8080
# Makes the image's entrypoint export NGINX_LOCAL_RESOLVERS, the DNS servers the template's resolver uses.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1
EXPOSE 8080
