set -e
./build.sh
java -Dmoar.ansi.enabled=true -jar `find . -name \*.fat.jar` script "$@"