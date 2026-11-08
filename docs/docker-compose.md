# Запуск сервиса в Docker

Frontend и backend запускаются одной командой из корня репозитория:

```bash
cp .env.example .env
# заполните ROUTERAI_API_KEY в .env
docker compose up --build
```

После запуска интерфейс доступен по адресу `http://localhost:8080`. Backend не публикуется наружу: Nginx frontend-контейнера передаёт запросы `/api/*` внутреннему сервису `backend`.

Если порт `8080` занят, задайте в `.env`, например:

```bash
PDF2TZ_FRONTEND_PORT=8088
```

Тогда интерфейс будет доступен по адресу `http://localhost:8088`.

## Локальный embeddings-поиск

По умолчанию embeddings выключены. Для многоязычного семантического поиска один раз установите зафиксированную BGE-M3 в локальный каталог:

```bash
./scripts/install-bge-m3.sh "$(pwd)/.local/models/bge-m3"
```

В `.env` задайте:

```bash
PDF2TZ_EMBEDDINGS_ENABLED=true
PDF2TZ_EMBEDDING_MODEL_HOST_PATH=./.local/models/bge-m3
```

Каталог монтируется в контейнер только для чтения. Модель не загружается при каждом запуске и не входит в Git или Docker image.

Для остановки сервисов:

```bash
docker compose down
```
