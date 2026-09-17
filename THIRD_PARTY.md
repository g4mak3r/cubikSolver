# Third-party components

CUBECRAFT v0.2 is intended as an Android Studio development project.

- OpenCV Android AAR (`org.opencv:opencv:4.13.0`) - Apache-2.0.
- AndroidX / Jetpack Compose / CameraX - Android Open Source Project licenses.
- `org.worldcubeassociation.tnoodle:scrambler-min2phase:0.19.2` - GPLv3, containing Chen Shuang's min2phase engine. If you redistribute an APK publicly, review and comply with its license obligations.

The project does not use Google Play Services and does not require a network connection at app runtime. Gradle needs network access once to resolve build dependencies unless they are already cached.
