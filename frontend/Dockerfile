FROM node:22-alpine AS build
WORKDIR /app
# Optional extra trusted CA certificates (certs/*.crt at the repository root); see certs/README.md.
COPY --from=certs . /extra-certs/
RUN touch /extra-ca.pem \
 && for c in /extra-certs/*.crt; do if [ -f "$c" ]; then cat "$c" >> /extra-ca.pem; fi; done
ENV NODE_EXTRA_CA_CERTS=/extra-ca.pem
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/templates/default.conf.template
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
