FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    RETINAVISION_AI_MODEL_PATH=/app/models/model_new.pth \
    RETINAVISION_AI_STORAGE_ROOT=/app/storage

WORKDIR /app

COPY requirements.txt ./
RUN python -m pip install --no-cache-dir --upgrade pip \
    && python -m pip install --no-cache-dir -r requirements.txt \
    && python -m pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu

COPY src ./src

RUN mkdir -p /app/models /app/storage /app/logs

EXPOSE 8000

CMD ["python", "-m", "uvicorn", "retinavision_ai.api:app", "--app-dir", "src", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
