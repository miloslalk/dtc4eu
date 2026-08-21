#!/bin/sh
# Nightly backup: pg_dump of both databases + tar of the MinIO data volume,
# written to /backups with 14-day rotation. Runs inside the backup container.
set -eu

STAMP=$(date +%Y-%m-%d_%H%M)
DIR=/backups/$STAMP
mkdir -p "$DIR"

export PGPASSWORD="$POSTGRES_PASSWORD"
pg_dump -h db -U postgres -Fc dtc4eu          > "$DIR/dtc4eu.dump"
pg_dump -h db -U postgres -Fc dtc4eu_website  > "$DIR/dtc4eu_website.dump"

tar -czf "$DIR/minio-data.tar.gz" -C /minio-data .

# rotate: keep 14 days
find /backups -mindepth 1 -maxdepth 1 -type d -mtime +14 -exec rm -rf {} +

echo "backup $STAMP done: $(du -sh "$DIR" | cut -f1)"
