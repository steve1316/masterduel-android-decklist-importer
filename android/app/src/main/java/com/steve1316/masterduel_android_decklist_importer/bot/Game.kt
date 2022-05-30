package com.steve1316.masterduel_android_decklist_importer.bot

import android.content.Context
import android.util.Log
import com.steve1316.masterduel_android_decklist_importer.MainActivity.loggerTag
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
	 * @param isError Flag to determine whether to display log message in console as debug or error.
	 */
	fun printToLog(message: String, tag: String = this.tag, isError: Boolean = false) {
		if (!isError) {
			Log.d(tag, message)
		} else {
			Log.e(tag, message)
		}

		// Remove the newline prefix if needed and place it where it should be.
		if (message.startsWith("\n")) {
			val newMessage = message.removePrefix("\n")
			MessageLog.messageLog.add("\n" + printTime() + " " + newMessage)
		} else {
			MessageLog.messageLog.add(printTime() + " " + message)
		}
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
		} else {
			CardNameData.name = Deck.extra[index].name
		}

		// Activate the keyboard for the search bar.
		val location = imageUtils.findImage("text_search")

		// The Accessibility service will automatically paste the text into the field. Tapping the search bar again will close the keyboard and submit the search query.
		return if (location != null) {
			gestureUtils.tap(location.x, location.y, "text_search", taps = 2)
			wait(1.0)

			// Submit the search query by tapping the same location again to close the keyboard. This is the same as pressing ENTER.
			gestureUtils.tap(location.x, location.y, "text_search")
			wait(3.0)

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

		Log.d(tag, "Rarity of $cardName is $rarity")

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

		val rarityLocations = imageUtils.findAll("rarity_$rarityImageFileName", "images")
		return if (rarityLocations.size > 3) {
			printToLog("Skipped $cardName as there were too many matches.")
			false
		} else {
			// Now add the card to the decklist.
			// First press on the last location as that is most likely the highest finish of that card.
			if (rarityLocations.size > 1) {
				gestureUtils.tap(rarityLocations[rarityLocations.size - 1].x, rarityLocations[rarityLocations.size - 1].y, "rarity_$rarityImageFileName")
				wait(0.25)
			} else {
				gestureUtils.tap(rarityLocations[0].x, rarityLocations[0].y, "rarity_$rarityImageFileName")
				wait(0.25)
			}

			// Now that the description of the card is on the screen, add however many is required to the decklist.
			val addCardLocation = imageUtils.findImage("add_card", tries = 30)!!
			gestureUtils.tap(addCardLocation.x, addCardLocation.y, "add_card", taps = amount)
			wait(0.25)

			// Close the card description screen.
			val exitCardLocation = imageUtils.findImage("exit_card", tries = 30)!!
			gestureUtils.tap(exitCardLocation.x, exitCardLocation.y, "exit_card")
			wait(0.25)

			// Finally, clear the search bar.
			val trashLocation = imageUtils.findImage("trash", tries = 30)!!
			gestureUtils.tap(trashLocation.x, trashLocation.y, "trash")
			wait(0.25)

			true
		}
	}

	/**
	 * Bot will begin automation here.
	 *
	 * @return True if all automation goals have been met. False otherwise.
	 */
	fun start(): Boolean {
		val startTime: Long = System.currentTimeMillis()

		landscapeCheck()

		if (initializationCheck()) {
			// Clear any leftover search query/filters.
			val clearLocation = imageUtils.findImage("trash", tries = 30)!!
			gestureUtils.tap(clearLocation.x, clearLocation.y, "trash")
			wait(1.0)

			Log.d(tag, "Processing: ${Deck.main}")

			var i = 0
			while (i < Deck.main.size) {
				// Submit the search query first.
				searchCard(i)

				// Check the rarity of the card and compare to the rarities of the cards returned from the search query.
				processSearchResults(i)

				i++
			}
		} else {
			throw Exception("Unable to detect if the bot is at the Create a Deck screen.")
		}

		val endTime: Long = System.currentTimeMillis()
		val runTime: Long = endTime - startTime
		Log.d(tag, "Total Runtime: ${runTime}ms")

		return true
	}
}