# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

A Java/Maven MCP server for managing and troubleshooting [Strimzi](https://strimzi.io/) Kafka resources on Kubernetes. Exposes ~62 tools for KafkaTopics, KafkaMirrorMaker2, KafkaConnect, and other Strimzi CRDs via `kubectl`. Used as a local Claude Code plugin.

## Build and Test

```bash
./mvnw -B package -DskipTests   # build JAR
./mvnw package                  # build + test
```

## Deployment

Releases are triggered by pushing a version tag:

```bash
git tag v1.2.3
git push origin v1.2.3
```

`release.yml` then:
1. Builds the JAR and attaches it to a GitHub Release
2. Builds and pushes a Docker image to ECR: `677637302876.dkr.ecr.us-east-1.amazonaws.com/strimzi-mcp-server:<VERSION>` (also tagged `latest`)

The plugin is consumed directly by Claude Code from ECR — there is no K8s service deployment.
