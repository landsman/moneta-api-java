#!/usr/bin/env bash
# Builds testing/native as a native image with Mandrel, in a container, runs it and the same caller on the JVM
# against the client's fixtures, and fails when the two print anything different. Then builds testing/quarkus
# native the same way and runs its integration test against it; that binary is Linux, so elsewhere it is only built.
#
#   native-test.sh                        build, run both, compare; then the Quarkus application
#   native-test.sh compare <jvm> <native> fail, with the diff, when the two outputs differ or either is empty
#   native-test.sh quarkus-task <os>      the Gradle task for the Quarkus application on that uname -s
set -euo pipefail

# The JDK 25 builder Quarkus itself uses for native builds; MANDREL_IMAGE picks another.
image=${MANDREL_IMAGE:-quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25}
fixtures=modules/moneta-api/src/test/resources/moneta
out=testing/native/build/native

compare() {
    local jvm=$1 native=$2
    [ -s "$jvm" ] || fail "the JVM run printed nothing"
    [ -s "$native" ] || fail "the native run printed nothing"
    diff -u --label jvm --label native "$jvm" "$native" >&2 || fail "the native image read MONETA differently"
    echo "native and JVM agree"
}

quarkus-task() {
    if [ "$1" = Linux ]; then
        echo :testing:quarkus:3.40.1:quarkusIntTest
    else
        echo :testing:quarkus:3.40.1:quarkusBuild
    fi
}

fail() {
    echo "native-test: $*" >&2
    exit 1
}

main() {
    cd "$(dirname "$0")/.."
    ./gradlew -q :testing:native:nativeClasspath
    local classpath
    classpath=$(find "$out/lib" -name '*.jar' | sort | paste -sd: -)

    java -cp "$classpath" example.nativeimage.NativeCaller "$fixtures" >"$out/jvm.txt"

    # As the caller's own user, so the build directory stays removable by Gradle.
    local run=(docker run --rm --user "$(id -u):$(id -g)" -v "$PWD:/project" -w /project)
    "${run[@]}" "$image" --no-fallback -cp "$classpath" -o "$out/caller" example.nativeimage.NativeCaller
    "${run[@]}" --entrypoint "$out/caller" "$image" "$fixtures" >"$out/native.txt"

    compare "$out/jvm.txt" "$out/native.txt"

    local task
    task=$(quarkus-task "$(uname -s)")
    ./gradlew -q "$task" -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true \
        -Dquarkus.native.builder-image="$image" -Dquarkus.package.jar.enabled=false
    case $task in
    *quarkusBuild) echo "the Quarkus application builds native; its test runs on Linux only" ;;
    *) echo "the Quarkus application reads MONETA as a native image" ;;
    esac
}

if [ $# -eq 0 ]; then
    main
else
    "$@"
fi
