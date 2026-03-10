FROM python:3.11-slim

WORKDIR /app

COPY simulator/requirements.txt ./simulator/
RUN pip install --no-cache-dir -r simulator/requirements.txt

COPY simulator/ ./simulator/
COPY config/simulator/ ./config/simulator/

CMD ["python", "-m", "simulator", "--config", "config/simulator/config.yaml"]
