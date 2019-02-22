set -e

# Build with npm
echo "building moar-sugar cli"
cd cli
npm install babel-register babel-preset-env --save
npm run-script build
npm install -g
cd ..
