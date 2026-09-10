#!/usr/bin/env bash
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y --no-install-recommends \
  libcairo2 \
  libcairo2-dev \
  pkg-config \
  libfreetype6 \
  libfreetype6-dev
rm -rf /var/lib/apt/lists/*

echo "Cairo $(pkg-config --modversion cairo) installed."
