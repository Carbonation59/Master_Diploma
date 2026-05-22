FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install
COPY . .
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs

# Копируем standalone‑сборку и статические файлы
COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

# Копируем скрипт для патча server.js
COPY patch-server.js ./
# Применяем патч: устанавливаем server.timeout = 0 и т.д.
RUN node patch-server.js

USER nextjs
EXPOSE 3001
ENV PORT=3001
CMD ["node", "server.js"]