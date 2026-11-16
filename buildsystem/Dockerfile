# From https://github.com/signalapp/Signal-Android

# This Docker image can be used to build the 'lite' flavor of Cryptomator for Android
# To build it
#  1. Clone this repository
#  2. Checkout the tag you want to build, e.g. 1.8.0
#  3. Build the image using `docker build -t cryptomator-android .` in this directory
#  4. Build Cryptomator using `docker run --rm -u $(id -u):$(id -g) -v $(pwd):/project -w /project cryptomator-android ./gradlew clean assembleLiteRelease` in the root of this project

FROM ubuntu:noble-20260917@sha256:534baea6a22c03a63003dbc8dbe78fe34bc0d7e595d9a9dc9834884ff530eb55

COPY docker/ docker/
COPY docker/apt.conf docker/sources.list /etc/apt/

# The image's own sources point to the live archive, only use the snapshot from sources.list
RUN rm -f /etc/apt/sources.list.d/ubuntu.sources

# Bit of a chicken-and-egg problem.
# Ubuntu needs the ca-certificates package before it'll trust the snapshot mirror.
# But we can't install it because it doesn't trust the snapshot mirror!
# To resolve, we temporarily disable verification.
RUN apt update -oAcquire::https::Verify-Peer=false
RUN apt install -oAcquire::https::Verify-Peer=false -y ca-certificates

# Back to normal, verification back on
RUN apt update
RUN apt install -y git openjdk-21-jdk-headless unzip wget

ENV ANDROID_COMMAND_LINE_TOOLS_FILENAME=commandlinetools-linux-14742923_latest.zip
ENV ANDROID_COMMAND_LINE_TOOLS_SHA256=04453066b540409d975c676d781da1477479dde3761310f1a7eb92a1dfb15af7
ENV ANDROID_API_LEVELS=android-37.0
ENV ANDROID_BUILD_TOOLS_VERSION=37.0.0

ENV ANDROID_HOME=/usr/local/android-sdk-linux
ENV PATH=${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/cmdline-tools/bin

RUN cd /usr/local/
RUN wget -q "https://dl.google.com/android/repository/${ANDROID_COMMAND_LINE_TOOLS_FILENAME}"
RUN echo "${ANDROID_COMMAND_LINE_TOOLS_SHA256}  ${ANDROID_COMMAND_LINE_TOOLS_FILENAME}" | sha256sum -c -
RUN unzip ${ANDROID_COMMAND_LINE_TOOLS_FILENAME} -d /usr/local/android-sdk-linux
RUN rm ${ANDROID_COMMAND_LINE_TOOLS_FILENAME}

RUN yes | sdkmanager --update --sdk_root="${ANDROID_HOME}"
RUN yes | sdkmanager --sdk_root="${ANDROID_HOME}" "platforms;${ANDROID_API_LEVELS}" "build-tools;${ANDROID_BUILD_TOOLS_VERSION}"

RUN yes | sdkmanager --licenses --sdk_root="${ANDROID_HOME}"

RUN rm -rf ${ANDROID_HOME}/tools

# Create a non-root user and group
RUN groupadd -r appuser && useradd --no-log-init -r -g appuser appuser

# RUN mkdir -p /home/appuser/.gradle && chown -R appuser:appuser /home/appuser/.gradle

# Switch to the non-root user
USER appuser
