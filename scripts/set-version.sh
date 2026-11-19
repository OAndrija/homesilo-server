#!/usr/bin/env sh
# Sets the version in pom.xml, ready for a release. Usage: scripts/set-version.sh 1.2.0
set -eu
version="${1:?usage: scripts/set-version.sh <version>, e.g. 1.2.0}"
case "$version" in
  [0-9]*.[0-9]*.[0-9]*) ;;
  *) echo "Versions look like 1.2.0" >&2; exit 1 ;;
esac
mvn -q versions:set -DnewVersion="$version" -DgenerateBackupPoms=false
echo "pom.xml is now at $version. Add a [$version] section to CHANGELOG.md, commit, then tag v$version."
