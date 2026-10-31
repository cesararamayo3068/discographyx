FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -B -ntp -Pprod -DskipTests -Dskip.installnodenpm -Dskip.npm package

FROM eclipse-temurin:21-jre
WORKDIR /opt/app
COPY --from=build --chown=10001:10001 /build/target/discographyx-*.jar application.jar
ENV SPRING_PROFILES_ACTIVE=prod,render
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:+UseSerialGC -XX:ActiveProcessorCount=2"
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
