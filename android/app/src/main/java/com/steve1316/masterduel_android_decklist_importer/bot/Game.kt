package com.steve1316.masterduel_android_decklist_importer.bot

import android.content.Context
import android.util.Log
import com.steve1316.masterduel_android_decklist_importer.MainActivity.loggerTag
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
	 * Bot will begin automation here.
	 *
	 * @return True if all automation goals have been met. False otherwise.
	 */
	fun start(): Boolean {
		val startTime: Long = System.currentTimeMillis()

		// Check rotation of the Virtual Display and if it is stuck in Portrait Mode, destroy and remake it.
		if (MediaProjectionService.displayHeight > MediaProjectionService.displayWidth) {
			Log.d(tag, "Virtual display is not correct. Recreating it now...")
			MediaProjectionService.forceGenerateVirtualDisplay(myContext)
		} else {
			Log.d(tag, "Skipping recreation of Virtual Display as it is correct.")
		}

		CardNameData.name = Deck.main[0].name
		val location = imageUtils.findImage("text_search")!!
		gestureUtils.tap(location.x, location.y, "text_search", taps = 2)

		wait(1.0)

		// Submit the search query by tapping the same location again to close the keyboard. This is the same as pressing ENTER.
		gestureUtils.tap(location.x, location.y, "text_search")

		val endTime: Long = System.currentTimeMillis()
		val runTime: Long = endTime - startTime
		Log.d(tag, "Total Runtime: ${runTime}ms")

		return true
	}
}