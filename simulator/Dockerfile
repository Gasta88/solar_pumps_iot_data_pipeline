FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

CMD ["python", "-c", "import time; print('Simulator placeholder - waiting...'); [time.sleep(60) for _ in iter(int, 1)]"]
