#!/usr/bin/env bash

set -uo pipefail

PACKAGE_VERSION="${1:-24.12.0.100206}"

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/codeartifact-bootstrap.sh"
codeartifact_resolve || exit 1

DOMAIN="$CODEARTIFACT_DOMAIN"
OWNER="$CODEARTIFACT_DOMAIN_OWNER"
REPO="$CODEARTIFACT_REPOSITORY"
NAMESPACE="org.sonarsource.sonarqube"

PACKAGES=(
  sonar-core
  sonar-sarif
  sonar-plugin-api-impl
  sonar-scanner-engine
  sonar-ws
  sonar-scanner-protocol
  sonar-markdown
  sonar-xoo-plugin
  sonar-education-plugin
  sonar-db-core
  sonar-testing-harness
  sonar-db-dao
  sonar-db-migration
  sonar-webserver-api
  sonar-auth-ldap
  sonar-server-common
  sonar-webserver-auth
  sonar-ce-task-projectanalysis
  sonar-process
  sonar-telemetry-core
)

echo "Allowing direct publish of ${NAMESPACE}:* (blocking upstream) in ${REPO}"
for pkg in "${PACKAGES[@]}"; do
  aws codeartifact put-package-origin-configuration \
      --domain "$DOMAIN" \
      --domain-owner "$OWNER" \
      --repository "$REPO" \
      --format maven \
      --namespace "$NAMESPACE" \
      --package "$pkg" \
      --restrictions publish=ALLOW,upstream=BLOCK \
    && echo "Origin ALLOW/BLOCK set on ${pkg}" \
    || echo "Failed to set origin on ${pkg}"

  if aws codeartifact delete-package-versions \
      --domain "$DOMAIN" \
      --domain-owner "$OWNER" \
      --repository "$REPO" \
      --format maven \
      --namespace "$NAMESPACE" \
      --package "$pkg" \
      --versions "$PACKAGE_VERSION" \
      --expected-status Published; then
    echo "Deleted ${pkg}:${PACKAGE_VERSION}"
  else
    echo "No local ${pkg}:${PACKAGE_VERSION} in ${REPO} (may only exist upstream)"
  fi
done
