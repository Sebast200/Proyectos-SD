# Usamos una imagen ligera de Python
FROM python:3.9-slim

# Actualizamos e instalamos Tkinter y las librerías de X11 necesarias
RUN apt-get update && apt-get install -y \
    python3-tk \
    tk \
    && rm -rf /var/lib/apt/lists/*

# Definimos el directorio de trabajo
WORKDIR /app

# Copiamos los requisitos e instalamos dependencias de Python
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copiamos el código de la aplicación
COPY app.py .

# Comando para ejecutar la app
CMD ["python", "app.py"]