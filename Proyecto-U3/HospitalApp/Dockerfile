# Usamos una imagen que tenga el JDK completo para poder usar 'javac'
FROM openjdk:17-jdk-slim

WORKDIR /app

# 1. Copiamos el código fuente y la librería
COPY src /app/src
COPY lib /app/lib

# 2. Compilamos manualmente. 
# -cp indica el ClassPath (dónde están las librerías). 
# En Linux (Docker) el separador es ":" (a diferencia de Windows que es ";")
RUN javac -cp "src:lib/*" src/*.java

# 3. Instalamos librerías gráficas necesarias para Swing en Linux
RUN apt-get update && apt-get install -y \
    libxext6 \
    libxrender1 \
    libxtst6 \
    libxi6 \
    libfreetype6

# 4. Ejecutamos indicando el Classpath
# src : para tus clases compiladas (.class)
# lib/* : para el driver de postgres
CMD ["java", "-cp", "src:lib/*", "Main"]