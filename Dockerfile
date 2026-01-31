FROM gradle:8.6-jdk11 as builder

WORKDIR /app

COPY . .

RUN gradle assembleRelease -x lint

FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y android-sdk-platform-tools && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/build/outputs/apk/release/*.apk /app/

CMD ["sh", "-c", "echo 'APK built successfully!' && ls -la /app/*.apk"]
