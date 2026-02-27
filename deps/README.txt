Place the Baritone API jar here for compilation.

Build Baritone from the parent directory:
  cd .. && ./gradlew build

Then copy the API jar:
  cp ../dist/baritone-api-fabric-*.jar .

The build.gradle references this directory as a flat-dir repository.
