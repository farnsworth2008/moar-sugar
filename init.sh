set -e
./gradlew build eclipse
git checkout HEAD -- .settings
cd cli
npm install babel-register babel-preset-env --save
npm run-script build
npm install -g
