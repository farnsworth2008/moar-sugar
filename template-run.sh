set -e
./build.sh
java -jar `find . -name \*.fat.jar` "$@"