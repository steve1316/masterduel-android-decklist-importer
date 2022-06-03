# Master Duel Android Decklist Importer

![GitHub commit activity](https://img.shields.io/github/commit-activity/m/steve1316/masterduel-android-decklist-importer?logo=GitHub) ![GitHub last commit](https://img.shields.io/github/last-commit/steve1316/masterduel-android-decklist-importer?logo=GitHub) ![GitHub issues](https://img.shields.io/github/issues/steve1316/masterduel-android-decklist-importer?logo=GitHub) ![GitHub pull requests](https://img.shields.io/github/issues-pr/steve1316/masterduel-android-decklist-importer?logo=GitHub) ![GitHub](https://img.shields.io/github/license/steve1316/masterduel-android-decklist-importer?logo=GitHub)

This application serves to provide an easy way to import a decklist from masterduelmeta by utilizing the Accessibility Service on Android to perform the required automation necessary to do the importing.

It will first fetch the text version of the decklist from the URL that you provided. Then when the tool starts, it will go through each card and attempt to search them. Finally, it will add the highest finish that you have for that card.

Any failures (like no search results) will be noted at the very end in the message log and will provide the reason as to why they failed.

It should be noted that this is not a "smart" tool. It is dumb so if the query of `Metalfoes Fusion` returns 1 SR card as the first result which is `Fullmetalfoes Alkahest` and the actual `Metalfoes Fusion` is 5 cards to the right of it, it will mark it as a failure and move on to the next card. It relies on the assumption that the correct card and its finishes are one after the other. This may lead to the situation where you only have one finish of the correct card but there is 2 cards right after it with the same rarity. Because they are right after the first one which you only have the one finish of, the bot will erroneously select what it thinks is the highest finish, which will be the 3rd card that is not what it searched for.

Note: Cards like `One for One`, `Metalfoes Fusion`, etc. that have multiple different search results tied to them because their names are shared amongst other cards will be marked as failures. The bot will not handle them so you will need to add them manually afterwards.

# Provided Features

-   Parses a text version of the decklist from the provided masterduelmeta URL.
-   Automatically searches and adds each card from the text decklist into the ingame decklist.
-   Provides a message log showcasing what happened with each card with a summary at the very end if any cards failed to be added.

# Requirements

1. [Android Device or Emulator (Nougat 7.0+)](https://developer.android.com/about/versions)
    1. Tested emulator was Bluestacks 5 with the following settings:
        - P64 (Beta)
        - 1080x1920 (Portrait Mode as emulators do not have a way to tell the bot that it rotated.)
        - 240 DPI
        - 4+ GB of Memory

# Instructions

1. Download the latest .apk from the Releases section on the right side of this page right under the About section and then install the application.
2. Head to the Settings page in the app to paste in the url from masterduelmeta and press the button to fetch the text form of your chosen decklist. You can double check the message log right underneath it to see the text form of the decklist.
3. Now you can head back to the Home page in the app and press the Start button to begin giving the necessary permissions required for the app to run. After that is done, pressing Start again will display a floating overlay button.
4. Go into Master Duel and go to create a deck, preferably empty and brand new. Move the overlay button somewhere out of the way and then press it to start the decklist importing process.
5. When it finishes, a notification will trigger with a message on either success or how many cards failed to be added. You can head on over to the Home page of the app and look at the message log to see what happened.

# Technologies used

1. [MediaProjection - Used to obtain full screenshots](https://developer.android.com/reference/android/media/projection/MediaProjection)
2. [AccessibilityService - Used to dispatch gestures like tapping and scrolling](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
3. [OpenCV Android 4.5.1 - Used to template match](https://opencv.org/releases/)
4. [Tesseract4Android 2.1.1 - For performing OCR on the screen](https://github.com/adaptech-cz/Tesseract4Android)
5. [AppUpdater 2.7 - For automatically checking and notifying the user for new app updates](https://github.com/javiersantos/AppUpdater)
