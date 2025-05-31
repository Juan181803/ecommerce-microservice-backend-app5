FROM openjdk:11

WORKDIR /app

COPY . /app

#RUN mvn install -DskipTests

# Ahora hacer el build del módulo específico
CMD ["mvn", "verify", "-pl", "e2e-tests", "-am"]