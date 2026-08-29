#!/usr/bin/env bash
#
# Deletes a Maven version from CodeArtifact so `artifactPublish` can republish it.
# CodeArtifact release packages are immutable (409 Conflict on a second PUT).
# Artifactory used to overwrite; this is the equivalent.
#
# Usage:
#   ./scripts/codeartifact-delete-version.sh              # defaults to 24.12.0.100206
#   ./scripts/codeartifact-delete-version.sh 24.12.0.100206
#
# Does not delete product zips (those go to S3 bucket codescanng-build).

set -uo pipefail

PACKAGE_VERSION="${1:-24.12.0.100206}"
DOMAIN="${CODEARTIFACT_DOMAIN:-autorabit-artifacts-domain}"
OWNER="${CODEARTIFACT_DOMAIN_OWNER:-261140574810}"
REGION="${CODEARTIFACT_REGION:-us-east-1}"
REPO="${CODEARTIFACT_REPOSITORY:-autorabit-libs-release}"
NAMESPACE="org.sonarsource.sonarqube"

# JARs opted in via publishToCodeArtifact (not codescan-application / sonar-application zips).
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

echo "Deleting CodeArtifact ${NAMESPACE}:*:${PACKAGE_VERSION} from ${REPO}"
for pkg in "${PACKAGES[@]}"; do
  if aws codeartifact delete-package-versions \
      --domain "$DOMAIN" \
      --domain-owner "$OWNER" \
      --repository "$REPO" \
      --format maven \
      --namespace "$NAMESPACE" \
      --package "$pkg" \
      --versions "$PACKAGE_VERSION" \
      --region "$REGION"; then
    echo "Deleted ${pkg}:${PACKAGE_VERSION}"
  else
    echo "No existing ${pkg}:${PACKAGE_VERSION} to delete (first publish)"
  fi
done
