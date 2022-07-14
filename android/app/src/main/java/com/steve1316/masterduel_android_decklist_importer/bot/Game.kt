package com.steve1316.masterduel_android_decklist_importer.bot

import android.content.Context
import android.util.Log
import com.steve1316.masterduel_android_decklist_importer.MainActivity.loggerTag
import com.steve1316.masterduel_android_decklist_importer.StartModule
import com.steve1316.masterduel_android_decklist_importer.data.CardList
import com.steve1316.masterduel_android_decklist_importer.data.CardNameData
import com.steve1316.masterduel_android_decklist_importer.data.ConfigData
import com.steve1316.masterduel_android_decklist_importer.data.Deck
import com.steve1316.masterduel_android_decklist_importer.utils.ImageUtils
import com.steve1316.masterduel_android_decklist_importer.utils.MediaProjectionService
import com.steve1316.masterduel_android_decklist_importer.utils.MessageLog
import com.steve1316.masterduel_android_decklist_importer.utils.MyAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.opencv.core.Point
import java.util.concurrent.TimeUnit

/**
 * Main driver for bot activity and navigation.
 */
class Game(private val myContext: Context) {
	private val tag: String = "${loggerTag}Game"

	private val startTime: Long = System.currentTimeMillis()

	val configData: ConfigData = ConfigData(myContext)
	val imageUtils: ImageUtils = ImageUtils(myContext, this)
	val gestureUtils: MyAccessibilityService = MyAccessibilityService.getInstance()

	private val failedCards: MutableMap<String, String> = mutableMapOf()

	/**
	 * Returns a formatted string of the elapsed time since the bot started as HH:MM:SS format.
	 *
	 * Source is from https://stackoverflow.com/questions/9027317/how-to-convert-milliseconds-to-hhmmss-format/9027379
	 *
	 * @return String of HH:MM:SS format of the elapsed time.
	 */
	private fun printTime(): String {
		val elapsedMillis: Long = System.currentTimeMillis() - startTime

		return String.format(
			"%02d:%02d:%02d",
			TimeUnit.MILLISECONDS.toHours(elapsedMillis),
			TimeUnit.MILLISECONDS.toMinutes(elapsedMillis) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(elapsedMillis)),
			TimeUnit.MILLISECONDS.toSeconds(elapsedMillis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(elapsedMillis))
		)
	}

	/**
	 * Print the specified message to debug console and then saves the message to the log.
	 *
	 * @param message Message to be saved.
	 * @param tag Distinguish between messages for where they came from. Defaults to Game's tag.
	 * @param isWarning Flag to determine whether to display log message in console as debug or warning.
	 * @param isError Flag to determine whether to display log message in console as debug or error.
	 */
	fun printToLog(message: String, tag: String = this.tag, isWarning: Boolean = false, isError: Boolean = false) {
		if (!isError && isWarning) {
			Log.w(tag, message)
		} else if (isError && !isWarning) {
			Log.e(tag, message)
		} else {
			Log.d(tag, message)
		}

		// Remove the newline prefix if needed and place it where it should be.
		val newMessage = if (message.startsWith("\n")) {
			"\n" + printTime() + " " + message.removePrefix("\n")
		} else {
			printTime() + " " + message
		}

		MessageLog.messageLog.add(newMessage)

		// Send the message to the frontend.
		StartModule.sendEvent("MessageLog", newMessage)
	}

	/**
	 * Wait the specified seconds to account for ping or loading.
	 *
	 * @param seconds Number of seconds to pause execution.
	 */
	fun wait(seconds: Double) {
		runBlocking {
			delay((seconds * 1000).toLong())
		}
	}

	/**
	 * Check rotation of the Virtual Display and if it is stuck in Portrait Mode, destroy and remake it.
	 *
	 */
	private fun landscapeCheck() {
		if (MediaProjectionService.displayHeight > MediaProjectionService.displayWidth) {
			Log.d(tag, "Virtual display is not correct. Recreating it now...")
			MediaProjectionService.forceGenerateVirtualDisplay(myContext)
		} else {
			Log.d(tag, "Skipping recreation of Virtual Display as it is correct.")
		}
	}

	/**
	 * Perform an initialization check at the start.
	 *
	 * @return True if the required image asset is found on the screen.
	 */
	private fun initializationCheck(): Boolean {
		printToLog("[INIT] Performing an initialization check...")

		val ownedCardsLocation = imageUtils.findImage("owned_cards", tries = 2)
		if (ownedCardsLocation != null) {
			printToLog("[INIT] Detected the card list is set to owned cards only. Switching to comprehensive card list...")
			gestureUtils.tap(ownedCardsLocation.x, ownedCardsLocation.y, "owned_cards")
			wait(0.1)
		}

		return (imageUtils.findImage("trash") != null)
	}

	/**
	 * Search for the card in the main/extra deck depending on where the iteration is at right now.
	 *
	 * @param index The index in the main/extra deck arraylists.
	 * @param isMainDeck Indicates which deck to iterate through. Defaults to true for the Main Deck.
	 * @return True if the search query for the card's name was successfully submitted.
	 */
	private fun searchCard(index: Int, isMainDeck: Boolean = true): Boolean {
		// Grab the card's name.
		if (isMainDeck) {
			CardNameData.name = Deck.main[index].name
			printToLog("\n[INFO] Searching ${CardNameData.name} from the main deck.")
		} else {
			CardNameData.name = Deck.extra[index].name
			printToLog("\n[INFO] Searching ${CardNameData.name} from the extra deck.")
		}

		// Activate the keyboard for the search bar.
		val location = imageUtils.findImage("text_search")

		// The Accessibility service will automatically paste the text into the field. Tapping the search bar again will close the keyboard and submit the search query.
		return if (location != null) {
			gestureUtils.tap(location.x, location.y, "text_search")
			wait(1.0)

			// Submit the search query by tapping elsewhere on the screen to close the keyboard. This is the same as pressing ENTER.
			gestureUtils.tap(MediaProjectionService.displayWidth / 2.0, 100.0, "text_search")
			wait(2.0)

			printToLog("[INFO] Successfully submitted search query.")

			true
		} else {
			false
		}
	}

	/**
	 * Process search results and if valid, adds them to the decklist.
	 *
	 * @param index The index in the main/extra deck arraylists.
	 * @param isMainDeck Indicates which deck to iterate through. Defaults to true for the Main Deck.
	 * @return True if the available search results are valid and added to the decklist.
	 */
	private fun processSearchResults(index: Int, isMainDeck: Boolean = true): Boolean {
		// Check rarity of the searched card.
		val cardName: String
		val amount: Int
		val rarity = if (isMainDeck) {
			cardName = Deck.main[index].name
			amount = Deck.main[index].amount
			CardList.cardList[cardName]!!.rarity
		} else {
			cardName = Deck.extra[index].name
			amount = Deck.extra[index].amount
			CardList.cardList[cardName]!!.rarity
		}

		val rarityImageFileName = when (rarity) {
			"N" -> {
				"normal"
			}
			"R" -> {
				"rare"
			}
			"SR" -> {
				"superrare"
			}
			"UR" -> {
				"ultrarare"
			}
			else -> {
				throw Exception("Invalid rarity name of $rarity.")
			}
		}

		printToLog("[INFO] Detected rarity of $cardName is $rarity.")

		val rarityLocations = imageUtils.findAll(
			"rarity_$rarityImageFileName", "images", region = intArrayOf(
				MediaProjectionService.displayWidth / 2, 0, MediaProjectionService.displayWidth / 2,
				MediaProjectionService.displayHeight
			)
		)

		Log.d(tag, "Rarity locations found: $rarityLocations")

		return if (rarityLocations.size > 3) {
			// Add the card to the failed list along with the reason of failure.
			failedCards[cardName] = "Too many matches"

			printToLog("[WARN] Skipped $cardName as there were too many matches.", isWarning = true)
			val trashLocation = imageUtils.findImage("trash", tries = 30)!!
			gestureUtils.tap(trashLocation.x, trashLocation.y, "trash")
			wait(0.10)
			false
		} else {
			// Now add the card to the decklist.
			if (rarityLocations.size > 1) {
				// Check the validity of the search results by their x-coordinate.
				if (!checkSearchValidity(rarityLocations)) {
					// Add the card to the failed list along with the reason of failure.
					failedCards[cardName] = "Matches are likely a false positive via the x-coordinate."

					printToLog("[WARN] Skipped $cardName as the matches are likely a false positive via the x-coordinate.", isWarning = true)
					val trashLocation = imageUtils.findImage("trash", tries = 30)!!
					gestureUtils.tap(trashLocation.x, trashLocation.y, "trash")
					return false
				}

				// Check the validity of the search results by their y-coordinate.
				var i = 1
				var lastRarityY = rarityLocations[0].y
				while (i < rarityLocations.size) {
					if (rarityLocations[i].y != lastRarityY) {
						// Add the card to the failed list along with the reason of failure.
						failedCards[cardName] = "Matches are likely a false positive via the y-coordinate."

						printToLog("[WARN] Skipped $cardName as the matches are likely a false positive via the y-coordinate.", isWarning = true)
						val trashLocation = imageUtils.findImage("trash", tries = 30)!!
						gestureUtils.tap(trashLocation.x, trashLocation.y, "trash")
						wait(0.10)
						return false
					} else {
						lastRarityY = rarityLocations[i].y
						i++
					}
				}

				// Press on the last location as that is most likely the highest finish of that card to open up their description screen.
				gestureUtils.tap(rarityLocations[rarityLocations.size - 1].x, rarityLocations[rarityLocations.size - 1].y, "rarity_$rarityImageFileName")
				wait(0.10)
			} else if (rarityLocations.size == 0) {
				// Add the card to the failed list along with the reason of failure.
				failedCards[cardName] = "No matches from the search query."

				printToLog("[WARN] Skipped $cardName as there were no matches from the search query.", isWarning = true)
				exitCardDescriptionScreen()
				return false
			} else {
				gestureUtils.tap(rarityLocations[0].x, rarityLocations[0].y, "rarity_$rarityImageFileName")
				wait(0.10)
			}

			printToLog("[INFO] Selected the card and brought up its description screen.")

			// Now that the description of the card is on the screen, add however many is required to the decklist.
			val addCardLocation = imageUtils.findImage("add_card", tries = 30)!!
			var i = 0
			while (i < amount) {
				gestureUtils.tap(addCardLocation.x, addCardLocation.y, "add_card")
				wait(0.10)
				i++
			}

			printToLog("[INFO] Added x$amount $cardName.")

			exitCardDescriptionScreen()

			true
		}
	}

	/**
	 * Check the search result validity in terms of their x-coordinate.
	 *
	 * @param rarityLocations Image locations to be compared.
	 * @return True if they are all valid in terms of their x-coordinate.
	 */
	private fun checkSearchValidity(rarityLocations: ArrayList<Point>): Boolean {
		var lastLocation = rarityLocations[0]
		var difference = rarityLocations[1].x - rarityLocations[0].x
		var i = 1
		while (i < rarityLocations.size) {
			val newDifference = rarityLocations[i].x - lastLocation.x
			if (newDifference != difference) {
				return false
			}

			lastLocation = rarityLocations[i]
			difference = newDifference

			i++
		}

		return true
	}

	/**
	 * Exits the card description screen.
	 *
	 */
	private fun exitCardDescriptionScreen() {
		// Close the card description screen.
		val exitCardLocation = imageUtils.findImage("exit_card", tries = 30)!!
		gestureUtils.tap(exitCardLocation.x, exitCardLocation.y, "exit_card")
		wait(0.10)

		// Finally, clear the search bar.
		val trashLocation = imageUtils.findImage("trash", tries = 30)!!
		gestureUtils.tap(trashLocation.x, trashLocation.y, "trash")
		wait(0.10)

		printToLog("[INFO] Exited the card description screen.")
	}

	/**
	 * Bot will begin automation here.
	 *
	 * @return Number of cards that failed to update the notification with.
	 */
	fun start(): Int {
		val startTime: Long = System.currentTimeMillis()

		landscapeCheck()

		if (initializationCheck()) {
			// Clear any leftover search query/filters.
			val clearLocation = imageUtils.findImage("trash", tries = 30)!!
			gestureUtils.tap(clearLocation.x, clearLocation.y, "trash")
			wait(1.0)

			// Process the main deck.
			printToLog("\n====================")
			printToLog("[INFO] Starting processing of the Main deck...")
			var i = 0
			while (i < Deck.main.size) {
				// Submit the search query first.
				searchCard(i)

				// Check the rarity of the card and compare to the rarities of the cards returned from the search query.
				processSearchResults(i)

				i++
			}

			// Process the extra deck.
			printToLog("\n====================")
			printToLog("[INFO] Starting processing of the Extra deck...")
			i = 0
			while (i < Deck.extra.size) {
				// Submit the search query first.
				searchCard(i, isMainDeck = false)

				// Check the rarity of the card and compare to the rarities of the cards returned from the search query.
				processSearchResults(i, isMainDeck = false)

				i++
			}

			printToLog("\n====================")
			printToLog("[SUCCESS] Finished added the decklist.")

			// Print out any cards that failed.
			if (failedCards.isNotEmpty()) {
				printToLog("\n====================")
				printToLog("${failedCards.size} card(s) failed:\n")
				failedCards.keys.forEach { cardName ->
					printToLog("$cardName - ${failedCards[cardName]}\n")
				}
			}
		} else {
			throw Exception("Unable to detect if the bot is at the Create a Deck screen.")
		}

		val endTime: Long = System.currentTimeMillis()
		val runTime: Long = endTime - startTime
		printToLog("Total Runtime: ${runTime}ms")

		return failedCards.size
	}
}