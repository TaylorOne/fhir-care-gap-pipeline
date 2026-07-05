#!/bin/sh
# Writes the runtime configuration the SPA loads before bootstrapping.
# API_URL is set by the deployment (Terraform / docker run); empty means the
# app falls back to http://localhost:8080.
set -eu
cat > /usr/share/nginx/html/env.js << EOF
window.__env = { apiUrl: '${API_URL:-}' };
EOF
