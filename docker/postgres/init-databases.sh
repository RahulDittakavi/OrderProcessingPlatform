#!/bin/sh
set -eu

createdb --username "$POSTGRES_USER" --owner "$POSTGRES_USER" inventorydb
createdb --username "$POSTGRES_USER" --owner "$POSTGRES_USER" productdb
