# Database

Run all commands from the `backend/` folder.

## Start / stop

```bash
docker compose up -d    # start
docker compose down     # stop
docker compose down -v  # stop and delete all data
```

## Access with psql

```bash
docker compose exec db psql -U studymatch -d studymatch
```

| Command          | Does              |
|------------------|-------------------|
| `\dt`            | list tables       |
| `\d table_name`  | show a table      |
| `\q`             | quit              |


