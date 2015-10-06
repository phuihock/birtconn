#!/bin/bash
if test -f "$1"; then
    for var in $(cat "$1"); do
        export ${var};
    done;
fi

exec java -jar ${JETTY_START_JAR} \
    -Dbirt.resources="${BIRT_RESOURCES}" \
    -Dbirt.reports="${BIRT_REPORTS}" \
    -Dbirt.output="${BIRT_OUTPUT}" \
    -Ddb.host="${DB_HOST}" \
    -Ddb.port="${DB_PORT}" \
    -Ddb.name="${DB_NAME}" \
    -Ddb.user="${DB_USER}" \
    -Ddb.pass="${DB_PASS}" \
    --exec \
    jetty.base=jetty
