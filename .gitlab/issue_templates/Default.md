## Problem description

Describe your problem here. Describe what you want to happen, and what
happens if you try to do it. If you have a stack trace or any logs, please
format them using Markdown triple backquote notation.

### Steps to reproduce

Describe how somebody else could observe the same behavior you do. Don't
share here any logins and passwords!

### Please provide a log!

Many issues are hard to reproduce without a log file. Please enable logging by opening the setting and scrolling to the bottom.
There you will find an option to activate loging and to export the file. Please note that the log might contain personal information,
such as the name of the tracks you listened to. If you don't want to post it publicly, let us know, and we will find a way.

If your device doesn't allow you to access the log file directly, you can use `adb` command to retrieve it:
```
% adb pull ./storage/emulated/0/Android/data/org.moire.ultrasonic/files/ultrasonic.1.log
```

More information about `adb`, the Android Debug Bridge: https://developer.android.com/tools/adb

## System information

### Ultrasonic client

 * **Ultrasonic version**: *version of the app*
 * **Android version**: *Version of Android OS on the device*
 * **Device info**: *Device manufacturer, model*

### Server

 * **Server name**: *Airsonic, Ampache, Supysonic...*
 * **Server version**: *version of server software*
 * **Protocol used**: *http or https (self certificate, letsencrypt...)*

## Additional notes

Include any extra notes here. Otherwise you may remove this section.
