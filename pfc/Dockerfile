# syntax=docker/dockerfile:1

# ---- Build stage --------------------------------------------------------
# Usa a imagem oficial do Maven (Temurin 21) para evitar depender do mvnw
# e de bit de execução / fim de linha (CRLF no Windows) do wrapper.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Primeiro só o pom.xml: camada de dependências fica em cache enquanto o
# pom não muda, acelerando rebuilds.
COPY pom.xml ./
RUN mvn -B dependency:go-offline

# Código-fonte e empacotamento. Testes ficam fora da imagem de build do
# Render (rodam no CI / localmente); aqui priorizamos um deploy rápido.
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Runtime stage ------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Executa como usuário sem privilégios (defesa em profundidade).
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /app/target/*.jar app.jar
USER app

EXPOSE 8080

# O perfil "local" NÃO é ativado aqui — em produção a config vem das
# variáveis de ambiente lidas por application.yaml (DB_URL, JWT_SECRET, etc).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
