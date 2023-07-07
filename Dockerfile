FROM folioci/alpine-jre-openjdk17:latest

# Install latest patch versions of packages: https://pythonspeed.com/articles/security-updates-in-docker/
USER root
RUN apk upgrade --no-cache
USER folio

ENV VERTICLE_FILE mod-pubsub-server-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

ENV KAFKA_HOST 10.0.2.15

ENV KAFKA_PORT 9092

ENV OKAPI_URL http://10.0.2.15:9130

# Copy your fat jar to the container
COPY mod-pubsub-server/target/${VERTICLE_FILE} ${VERTICLE_HOME}/${VERTICLE_FILE}

# Expose this port locally in the container.
EXPOSE 8081
