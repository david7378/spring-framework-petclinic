#!/bin/bash
# Nightly owners report for the legacy VM. Installed as /opt/petclinic/bin/nightly-report.sh,
# scheduled by /etc/cron.d/petclinic-report. Uses the classes and JDBC settings of the deployed WAR.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/tomcat/webapps/ROOT}"
JAVA="${JAVA:-/usr/lib/jvm/java-17-openjdk-amd64/bin/java}"

exec "$JAVA" -cp "$APP_DIR/WEB-INF/classes:$APP_DIR/WEB-INF/lib/*" \
  org.springframework.samples.petclinic.report.ReportGenerator "$@"
