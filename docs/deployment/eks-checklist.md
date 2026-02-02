# EKS Deployment Checklist

This checklist is the minimum baseline for production readiness on Kubernetes/EKS.

## Manifests

- Deployment
- Service
- ConfigMap (non-secret config)
- Secret (credentials/tokens)
- HPA (when relevant)

## Health checks

- Readiness probe is wired to a readiness endpoint.
- Liveness probe is wired to a liveness endpoint.

## Configuration

- Config via environment variables.
- Secrets via Kubernetes Secrets.
- No hardcoded secrets in images or repo.

## Scaling

- Reasonable resource requests/limits (documented).
- HPA signal defined (CPU and/or custom metrics).

## Observability

- Logs shipped (JSON structured).
- Metrics available (Micrometer endpoint scraped).
- Traces exported (OpenTelemetry pipeline).

## Safety

- Rolling update strategy defined.
- Timeouts configured (startup/termination/grace).
