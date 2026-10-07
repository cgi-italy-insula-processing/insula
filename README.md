# Insula Processing Core

Insula Processing Core is an Earth Observation (EO) platform 
built from a joint effort of CGI Italy and UK, and now currently led by
CGI Italy only.
Insula Processing Core is a distributed platform to process at scale EO data to produce added-value EO products

The vision of this platform is being as simple as possible. Being a vision,
there should be always something to do to achieve it.

> Simplicity is a great virtue but it requires hard work to achieve it and
> education to appreciate it. And to make matters worse: complexity sells
> better.
*Edsger Wybe Dijkstra*

## Contents

| Module | Image | Role |
| --- | --- | --- |
| `core/processing/server` (`app`, `orchestrator`, `persistence`, `model`) | `server-core` | Processing server: REST API over services, job configurations and jobs; validates and launches jobs; serves the job outputs |
| `core/processing/worker` | `worker-core` | Turns queued jobs into Argo Workflows on Kubernetes |
| `core/processing/k8s-event-collector` | `k8s-event-collector-core` | Watches workflow and pod events and reports them to the worker |
| `core/processing/input-downloader` | `input-downloader-core` | First workflow step: stages the job inputs in (HTTP, S3, STAC) |
| `core/processing/output-uploader` | `output-uploader-core` | Last workflow step: uploads the job outputs to object storage |
| `core/processing/io`, `rpc`, `core/queues`, `core/cwl-model`, `core/testutils` | - | Shared libraries |

## Build

Requirements: a JDK between 8 and 19 to run the Gradle 7.6 wrapper (the sources
target Java 8), and a Docker daemon to build the images.

```sh
./gradlew build                                  # compile and run the tests (in-memory HSQLDB, embedded broker)
./gradlew :core:processing:server:app:buildDockerImage
```

Every application module (`server:app`, `worker`, `k8s-event-collector`,
`input-downloader`, `output-uploader`) has a `buildDockerImage` task. Images are tagged
`<group>/<name>:<version>`, prefixed with `DOCKER_REGISTRY_URL` when that Gradle
property is set (`-PDOCKER_REGISTRY_URL=registry.example.com`).

## Container images

Released images are published on the GitHub Container Registry:

```
ghcr.io/cgi-italy-insula-processing/com.cgi.eoss.platform.core/server-core
ghcr.io/cgi-italy-insula-processing/com.cgi.eoss.platform.core/worker-core
ghcr.io/cgi-italy-insula-processing/com.cgi.eoss.platform.core/k8s-event-collector-core
ghcr.io/cgi-italy-insula-processing/com.cgi.eoss.platform.core/input-downloader-core
ghcr.io/cgi-italy-insula-processing/com.cgi.eoss.platform.core/output-uploader-core
```

## Deployment

The components are deployed on Kubernetes, together with the OGC API Processes facade
([ogc-api](https://github.com/cgi-italy-insula-processing/ogc-api)), Argo Workflows, a
message broker, PostgreSQL and S3-compatible object storage, by the `eoepca` Helm chart:

- [helm-chart](https://github.com/cgi-italy-insula-processing/helm-chart): the chart,
  its configuration and the application properties of each component;
- [deployment](https://github.com/cgi-italy-insula-processing/deployment): scripts that
  check the prerequisites, generate the chart values, deploy, validate the release and run
  an end-to-end test.

The `application.properties` bundled in `core/processing/server/app` hold local
development values only; every deployment overrides them.

## License

Apache License 2.0, see [LICENSE](LICENSE).
