#!/usr/bin/env bash
# Plain-Java tests. TuiTest reads src/main/java/greed from the repo root.
set -uo pipefail
cd "$(dirname "$0")"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
javac -d "$tmp" $(find src/main/java src/test/java -name '*.java') || { echo "COMPILE FAILED"; exit 1; }
rc=0
for t in greed.rules.GreedTest greed.view.TuiTest greed.view.HotDiceTest; do
  echo "== $t"
  java -cp "$tmp" "$t" || { echo "$t FAILED"; rc=1; }
done
if (( rc == 0 )); then
  echo "greed: all 3 test classes passed"
else
  echo "greed: TEST FAILURES"
fi
exit $rc
