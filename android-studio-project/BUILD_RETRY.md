This file records a build retry after aligning Java and Kotlin compilation targets to JVM 17. It does not change the live website or published APK.

A new workflow run checks whether the four Android signing secrets are configured and, if so, attempts a signed release build. This marker does not contain or expose any signing credentials.