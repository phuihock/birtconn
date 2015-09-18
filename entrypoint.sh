java \
    -DJDBC_NAME="${JDBC_NAME}" \
    -DDB_NAME="${DB_NAME}" \
    -DDB_USER="${DB_USER}" \
    -DDB_PASS="${DB_PASS}" \
    -DDB_HOST="${DB_HOST}" \
    -DDB_PORT="${DB_PORT}" \
    -DBIRT_RESOURCES="${BIRT_RESOURCES}" \
    -DBIRT_REPORTS="${BIRT_REPORTS}" \
    -DBIRT_OUTPUT="${BIRT_OUTPUT}" \
    -jar /opt/${JETTY_VER}/start.jar \
    jetty.base=jetty
