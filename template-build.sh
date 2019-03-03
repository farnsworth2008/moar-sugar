set -e

BUILD_VERSION="`git describe --always --tags --long`"
./gradlew "-Dbuild_version=${BUILD_VERSION}" fatJar