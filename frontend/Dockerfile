# The test client, built once and served by nginx, which also proxies /api and /ws to the game server.

FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist/shardbound/browser /usr/share/nginx/html
