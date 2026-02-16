ARG BASE_VERSION=v7.20.0
FROM openapitools/openapi-generator-cli:${BASE_VERSION}

ARG GENERATOR_DIR=/opt/openapi-generator/modules/openapi-generator-cli/target
ARG GENERATOR_VERSION=1.1.0

COPY --chmod=555 docker/scripts/docker-entrypoint.sh /usr/local/bin/
RUN ln -s /usr/local/bin/docker-entrypoint.sh /usr/local/bin/openapi-generator

COPY target/openapi-generator-di-${GENERATOR_VERSION}.jar ${GENERATOR_DIR}/openapi-generator-di.jar

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]

CMD ["help"]
