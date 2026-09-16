#!/usr/bin/env bash
#
# Fetches an AWS CodeArtifact authorization token and prints the export statement so it can
# be evaluated into the current shell for local Gradle builds. Prefer the ./ca-gradlew
# wrapper which sources this automatically. Requires the AWS CLI and an SSO profile with
# read/publish access to the autorabit-artifacts-domain domain.

set -uo pipefail

CODEARTIFACT_DEFAULT_DOMAIN="autorabit-artifacts-domain"
CODEARTIFACT_DEFAULT_DOMAIN_OWNER="261140574810"
CODEARTIFACT_DEFAULT_REGION="us-east-1"
AWS_PROFILE_DEFAULT="dev-profile"
AWS_SSO_AUTO_LOGIN_DEFAULT="true"

codeartifact_bootstrap() {
  local domain="${CODEARTIFACT_DOMAIN:-$CODEARTIFACT_DEFAULT_DOMAIN}"
  local owner="${CODEARTIFACT_DOMAIN_OWNER:-$CODEARTIFACT_DEFAULT_DOMAIN_OWNER}"
  local region="${CODEARTIFACT_REGION:-$CODEARTIFACT_DEFAULT_REGION}"
  local profile="${AWS_PROFILE:-$AWS_PROFILE_DEFAULT}"
  local auto_login="${AWS_SSO_AUTO_LOGIN:-$AWS_SSO_AUTO_LOGIN_DEFAULT}"
  local aws_bin="${AWS_BIN:-$(command -v aws 2>/dev/null || true)}"
  local token

  if [[ -z "$aws_bin" ]] || [[ ! -x "$aws_bin" ]]; then
    echo "ERROR: aws CLI not found at: ${AWS_BIN:-(none)}" >&2
    echo "Tip: set AWS_BIN to the output of 'which aws'." >&2
    return 1
  fi

  token="$("$aws_bin" --profile "$profile" --region "$region" codeartifact get-authorization-token \
    --domain "$domain" \
    --domain-owner "$owner" \
    --query authorizationToken --output text 2>/dev/null)" || token=""

  if [[ -z "${token:-}" ]] || [[ "$token" == "None" ]]; then
    if [[ "$auto_login" == "true" ]]; then
      echo "INFO: Unable to get CodeArtifact token; running 'aws sso login --profile $profile'..." >&2
      if ! "$aws_bin" sso login --profile "$profile" >&2; then
        echo "ERROR: 'aws sso login' failed." >&2
        return 1
      fi
      token="$("$aws_bin" --profile "$profile" --region "$region" codeartifact get-authorization-token \
        --domain "$domain" \
        --domain-owner "$owner" \
        --query authorizationToken --output text 2>/dev/null)" || token=""
    fi
  fi

  if [[ -z "${token:-}" ]] || [[ "$token" == "None" ]]; then
    echo "ERROR: Failed to obtain CodeArtifact token." >&2
    echo "Tip: run: \"$aws_bin\" sso login --profile \"$profile\"" >&2
    return 1
  fi

  export CODEARTIFACT_AUTH_TOKEN="$token"
  return 0
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  codeartifact_bootstrap "$@" || exit $?
  printf 'export CODEARTIFACT_AUTH_TOKEN=%q\n' "$CODEARTIFACT_AUTH_TOKEN"
fi
