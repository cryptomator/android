#!/usr/bin/env bash

cd ..

mv gradle/verification-metadata_bkup.xml

./gradlew clean test assembleLiteRelease --write-verification-metadata sha256 help

sed -i 's/<\/configuration>/   <trusted-artifacts>\n         <trust file=\".*-javadoc[.]jar\" regex=\"true\"\/>\n         <trust file=\".*-sources[.]jar\" regex=\"true\"\/>\n         <trust file=\".*[.]pom\" regex=\"true\"\/>\n      <\/trusted-artifacts>\n   <\/configuration>/g' gradle/verification-metadata.xml