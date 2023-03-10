# From https://github.com/signalapp/Signal-Android

# This Docker image can be used to build the 'lite' flavor of Cryptomator for Android
# To build it
#  1. Clone this repository
#  2. Checkout the tag you want to build, e.g. 1.8.0
#  3. Build the image using `docker build -t cryptomator-android .` in this directory
#  4. Build Cryptomator using `docker run --rm -u $(id -u):$(id -g) -v $(pwd):/project -w /project cryptomator-android ./gradlew clean assembleLiteRelease` in the root of this project

FROM debian:bullseye

COPY docker/ docker/
COPY docker/apt.conf docker/sources.list /etc/apt/

RUN dpkg --add-architecture i386

RUN apt-get update -y && apt-get install -y apt-utils wget git unzip

RUN apt-get update -y && apt-get install -y $(cat docker/dependencies.txt)
RUN docker/print-versions.sh docker/dependencies.txt

ENV ANDROID_COMMAND_LINE_TOOLS_FILENAME commandlinetools-linux-8512546_latest.zip
ENV ANDROID_API_LEVELS                  android-33
ENV ANDROID_BUILD_TOOLS_VERSION         33.0.0

ENV ANDROID_HOME /usr/local/android-sdk-linux
ENV PATH         ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/cmdline-tools/bin

RUN cd /usr/local/
RUN wget -q "https://dl.google.com/android/repository/${ANDROID_COMMAND_LINE_TOOLS_FILENAME}"
RUN unzip ${ANDROID_COMMAND_LINE_TOOLS_FILENAME} -d /usr/local/android-sdk-linux
RUN rm ${ANDROID_COMMAND_LINE_TOOLS_FILENAME}

RUN yes | sdkmanager --update --sdk_root="${ANDROID_HOME}"
RUN yes | sdkmanager --sdk_root="${ANDROID_HOME}" "platforms;${ANDROID_API_LEVELS}" "build-tools;${ANDROID_BUILD_TOOLS_VERSION}"

RUN yes | sdkmanager --licenses --sdk_root="${ANDROID_HOME}"
RUN update-java-alternatives -s java-1.11.0-openjdk-amd64

RUN rm -rf ${ANDROID_HOME}/tools
