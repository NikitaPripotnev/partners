FROM openjdk:8-jdk-alpine
ARG JAR_FILE=build/libs/partners.jar
COPY ${JAR_FILE} partners.jar
ENTRYPOINT ["java","-jar","partners.jar"]
