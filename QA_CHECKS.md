# QA checks - v0.4.3

Before packaging this revision:

- pure Kotlin cube/scanner model core compiled with `kotlinc` successfully;
- every Kotlin source was passed through the Kotlin parser; no `expecting`, `syntax error`, `unexpected tokens`, or parsing diagnostics were found;
- `averageOrNull` is absent;
- the fixed guide fraction is identical in analyzer and overlay (`0.78`);
- capture samples always come from `sampleGuideSquare`; AR contour coordinates are visual-only;
- AR candidates smaller than 26% of the guide area are rejected, with additional side-length and center-position guards.

A full Android Gradle build still requires Android SDK/AndroidX dependencies and should be run in Android Studio on the target development machine.
