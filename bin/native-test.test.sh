#!/usr/bin/env bash
# What native-test.sh would get wrong silently: passing when the native image printed nothing (it crashed before
# its first line) or something else than the JVM, which is how a record missing from the metadata shows up; and
# trying to run a Linux binary on macOS, or never running the Quarkus one on Linux.
set -uo pipefail

script="$(cd "$(dirname "$0")" && pwd)/native-test.sh"
t=$(mktemp -d)
trap 'rm -rf "$t"' EXIT
rc=0

ok() { echo "ok   $1"; }
bad() {
    echo "FAIL $1"
    rc=1
}
expect() {
    local what=$1
    shift
    if "$@" >/dev/null 2>&1; then ok "$what"; else bad "$what"; fi
}
refuse() {
    local what=$1
    shift
    if "$@" >/dev/null 2>&1; then bad "$what"; else ok "$what"; fi
}

printf 'Account[id=acc-1, iban=CZ0206000000000000001337]\n' >"$t/jvm"
cp "$t/jvm" "$t/same"
printf 'Account[id=acc-1, iban=null]\n' >"$t/other"
: >"$t/empty"

expect "the same output passes" "$script" compare "$t/jvm" "$t/same"
refuse "a component the image could not see fails" "$script" compare "$t/jvm" "$t/other"
refuse "a native run that printed nothing fails" "$script" compare "$t/jvm" "$t/empty"
refuse "a JVM run that printed nothing fails, even against an empty native one" "$script" compare "$t/empty" "$t/empty"

expect "on Linux the Quarkus binary is run by its integration test" \
    test "$("$script" quarkus-task Linux)" = :testing:quarkus:3.40.1:quarkusIntTest
expect "elsewhere the Linux binary is only built, not run" \
    test "$("$script" quarkus-task Darwin)" = :testing:quarkus:3.40.1:quarkusBuild

exit $rc
