# Master Duel Android Decklist Importer

![GitHub commit activity](https://img.shields.io/github/commit-activity/m/steve1316/masterduel-android-decklist-importer?logo=GitHub) ![GitHub last commit](https://img.shields.io/github/last-commit/steve1316/masterduel-android-decklist-importer?logo=GitHub) ![GitHub issues](https://img.shields.io/github/issues/steve1316/masterduel-android-decklist-importer?logo=GitHub) ![GitHub pull requests](https://img.shields.io/github/issues-pr/steve1316/masterduel-android-decklist-importer?logo=GitHub) ![GitHub](https://img.shields.io/github/license/steve1316/masterduel-android-decklist-importer?logo=GitHub)

This application serves to provide an easy way to import a decklist from masterduelmeta by utilizing the Accessibility Service on Android to perform the required automation necessary to do the importing.

# Provided Features

# Requirements

1. [Android Device or Emulator (Nougat 7.0+)](https://developer.android.com/about/versions)
    1. (Experimental) Tablets supported with minimum 1600 pixel width like the Galaxy Tab S7. If oriented portrait, browsers like Chrome needs to have Desktop Mode turned off and situated on the left half of the tablet. If landscape, browsers like Chrome needs to have Desktop Mode turned on and situated on the left half of the tablet.
    2. Tested emulator was Bluestacks 5 with the following settings:
        - P64 (Beta)
        - 1080x1920 (Portrait Mode as emulators do not have a way to tell the bot that it rotated.)
        - 240 DPI
        - 4+ GB of Memory

# Instructions

# Technologies used

1. [MediaProjection - Used to obtain full screenshots](https://developer.android.com/reference/android/media/projection/MediaProjection)
2. [AccessibilityService - Used to dispatch gestures like tapping and scrolling](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
3. [OpenCV Android 4.5.1 - Used to template match](https://opencv.org/releases/)
4. [Tesseract4Android 2.1.1 - For performing OCR on the screen](https://github.com/adaptech-cz/Tesseract4Android)
5. [AppUpdater 2.7 - For automatically checking and notifying the user for new app updates](https://github.com/javiersantos/AppUpdater)
