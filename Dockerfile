# CloudStore container image: compiles the Java sources and runs the server.
# Settings come from CLOUDSTORE_* environment variables (see docs/hosting.md).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
ADD https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar lib/
COPY src ./src
RUN javac -encoding UTF-8 -d out -cp "lib/*" src/cloudstore/*.java

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/out ./out
COPY --from=build /app/lib ./lib
COPY public ./public
EXPOSE 8080
CMD ["java", "-cp", "out:lib/*", "cloudstore.Main"]
