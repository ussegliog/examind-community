export CSTL_HOME="$HOME/.constellation-boot";

export DATABASE_URL="postgres://cstl:admin@localhost:5432/cstl-boot"
export EPSG_DATABASE_URL="$DATABASE_URL"

export CSTL_URL="http://localhost:9000"

export CSTL_SERVICE_URL="http://localhost:9000/WS"

java -jar target/cstl-boot-1.0-SNAPSHOT.jar
