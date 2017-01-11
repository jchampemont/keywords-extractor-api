FROM openjdk:8

WORKDIR /code

ADD . /code/
RUN ["./gradlew", "shadowJar"]

EXPOSE 4567
CMD ["java", "-jar", "build/libs/keywords-extractor-api-0.1.0-SNAPSHOT-all.jar"]