package com.steve1316.masterduel_android_decklist_importer.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.steve1316.masterduel_android_decklist_importer.MainActivity
import com.steve1316.masterduel_android_decklist_importer.bot.Game
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.text.DecimalFormat

/**
 * Utility functions for image processing via CV like OpenCV.
 */
class ImageUtils(context: Context, private val game: Game) {
	private val tag: String = "${MainActivity.loggerTag}ImageUtils"
	private var myContext = context

	private val matchMethod: Int = Imgproc.TM_CCOEFF_NORMED
	private val decimalFormat = DecimalFormat("#.###")

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////
	// SharedPreferences
	private val confidence: Double = 0.85
	private val confidenceAll: Double = game.configData.confidenceAll
	private val debugMode: Boolean = game.configData.debugMode
	private var customScale: Double = game.configData.customScale

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////
	// Device configuration
	private val displayWidth: Int = MediaProjectionService.displayWidth
	private val displayHeight: Int = MediaProjectionService.displayHeight
	private val is1080p: Boolean = (displayWidth == 1080) || (displayHeight == 1080) // 1080p Portrait or Landscape Mode.
	val is720p: Boolean = (displayWidth == 720) || (displayHeight == 720) // 720p
	val isTabletPortrait: Boolean = (displayWidth == 1600 && displayHeight == 2560) || (displayWidth == 2560 && displayHeight == 1600) // Galaxy Tab S7 1600x2560 Portrait Mode
	val isTabletLandscape: Boolean = (displayWidth == 2560 && displayHeight == 1600) // Galaxy Tab S7 1600x2560 Landscape Mode

	// Scales
	private val lowerEndScales: MutableList<Double> = mutableListOf(0.60, 0.61, 0.62, 0.63, 0.64, 0.65, 0.67, 0.68, 0.69, 0.70)
	private val middleEndScales: MutableList<Double> = mutableListOf(
		0.70, 0.71, 0.72, 0.73, 0.74, 0.75, 0.76, 0.77, 0.78, 0.79, 0.80, 0.81, 0.82, 0.83, 0.84, 0.85, 0.87, 0.88, 0.89, 0.90, 0.91, 0.92, 0.93, 0.94, 0.95, 0.96, 0.97, 0.98, 0.99
	)
	private val tabletPortraitScales: MutableList<Double> = mutableListOf(0.70, 0.71, 0.72, 0.73, 0.74, 0.75)
	private val tabletLandscapeScales: MutableList<Double> = mutableListOf(0.55, 0.56, 0.57, 0.58, 0.59, 0.60)

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////

	companion object {
		private var matchFilePath: String = ""
		private lateinit var matchLocation: Point
		private var matchLocations: ArrayList<Point> = arrayListOf()

		/**
		 * Saves the file path to the saved match image file for debugging purposes.
		 *
		 * @param filePath File path to where to store the image containing the location of where the match was found.
		 */
		private fun updateMatchFilePath(filePath: String) {
			matchFilePath = filePath
		}
	}

	init {
		// Set the file path to the /files/temp/ folder.
		val matchFilePath: String = myContext.getExternalFilesDir(null)?.absolutePath + "/temp"
		updateMatchFilePath(matchFilePath)
	}

	/**
	 * Match between the source Bitmap from /files/temp/ and the template Bitmap from the assets folder.
	 *
	 * @param sourceBitmap Bitmap from the /files/temp/ folder.
	 * @param templateBitmap Bitmap from the assets folder.
	 * @param region Specify the region consisting of (x, y, width, height) of the source screenshot to template match. Defaults to (0, 0, 0, 0) which is equivalent to searching the full image.
	 * @param customConfidence Specify a custom confidence. Defaults to the confidence set in the app's settings.
	 * @return True if a match was found. False otherwise.
	 */
	private fun match(sourceBitmap: Bitmap, templateBitmap: Bitmap, region: IntArray = intArrayOf(0, 0, 0, 0), useSingleScale: Boolean = false, customConfidence: Double = 0.0): Boolean {
		// If a custom region was specified, crop the source screenshot.
		val srcBitmap = if (!region.contentEquals(intArrayOf(0, 0, 0, 0))) {
			Bitmap.createBitmap(sourceBitmap, region[0], region[1], region[2], region[3])
		} else {
			sourceBitmap
		}

		val setConfidence: Double = if (customConfidence == 0.0) {
			confidence
		} else {
			customConfidence
		}

		// Scale images if the device is not 1080p which is supported by default.
		val scales: MutableList<Double> = when {
			customScale != 1.0 && !useSingleScale -> {
				mutableListOf(customScale - 0.02, customScale - 0.01, customScale, customScale + 0.01, customScale + 0.02, customScale + 0.03, customScale + 0.04)
			}
			customScale != 1.0 && useSingleScale -> {
				mutableListOf(customScale)
			}
			is720p -> {
				lowerEndScales.toMutableList()
			}
			!is720p && !is1080p && !isTabletPortrait -> {
				middleEndScales.toMutableList()
			}
			isTabletPortrait && isTabletLandscape -> {
				tabletLandscapeScales.toMutableList()
			}
			isTabletPortrait && !isTabletLandscape -> {
				tabletPortraitScales.toMutableList()
			}
			else -> {
				mutableListOf(1.0)
			}
		}

		while (scales.isNotEmpty()) {
			val newScale: Double = decimalFormat.format(scales.removeFirst()).toDouble()

			val tmp: Bitmap = if (newScale != 1.0) {
				Bitmap.createScaledBitmap(templateBitmap, (templateBitmap.width * newScale).toInt(), (templateBitmap.height * newScale).toInt(), true)
			} else {
				templateBitmap
			}

			// Create the Mats of both source and template images.
			val sourceMat = Mat()
			val templateMat = Mat()
			Utils.bitmapToMat(srcBitmap, sourceMat)
			Utils.bitmapToMat(tmp, templateMat)

			// Make the Mats grayscale for the source and the template.
			Imgproc.cvtColor(sourceMat, sourceMat, Imgproc.COLOR_BGR2GRAY)
			Imgproc.cvtColor(templateMat, templateMat, Imgproc.COLOR_BGR2GRAY)

			// Create the result matrix.
			val resultColumns: Int = sourceMat.cols() - templateMat.cols() + 1
			val resultRows: Int = sourceMat.rows() - templateMat.rows() + 1
			val resultMat = Mat(resultRows, resultColumns, CvType.CV_32FC1)

			// Now perform the matching and localize the result.
			Imgproc.matchTemplate(sourceMat, templateMat, resultMat, matchMethod)
			val mmr: Core.MinMaxLocResult = Core.minMaxLoc(resultMat)

			matchLocation = Point()
			var matchCheck = false

			// Format minVal or maxVal.
			val minVal: Double = decimalFormat.format(mmr.minVal).toDouble()
			val maxVal: Double = decimalFormat.format(mmr.maxVal).toDouble()

			// Depending on which matching method was used, the algorithms determine which location was the best.
			if ((matchMethod == Imgproc.TM_SQDIFF || matchMethod == Imgproc.TM_SQDIFF_NORMED) && mmr.minVal <= (1.0 - setConfidence)) {
				matchLocation = mmr.minLoc
				matchCheck = true
				if (debugMode) {
					game.printToLog("[DEBUG] Match found with $minVal <= ${1.0 - setConfidence} at Point $matchLocation using scale: $newScale.", tag = tag)
				}
			} else if ((matchMethod != Imgproc.TM_SQDIFF && matchMethod != Imgproc.TM_SQDIFF_NORMED) && mmr.maxVal >= setConfidence) {
				matchLocation = mmr.maxLoc
				matchCheck = true
				if (debugMode) {
					game.printToLog("[DEBUG] Match found with $maxVal >= $setConfidence at Point $matchLocation using scale: $newScale.", tag = tag)
				}
			} else {
				if (debugMode) {
					if ((matchMethod != Imgproc.TM_SQDIFF && matchMethod != Imgproc.TM_SQDIFF_NORMED)) {
						game.printToLog("[DEBUG] Match not found with $maxVal not >= $setConfidence at Point ${mmr.maxLoc} using scale $newScale.", tag = tag)
					} else {
						game.printToLog("[DEBUG] Match not found with $minVal not <= ${1.0 - setConfidence} at Point ${mmr.minLoc} using scale $newScale.", tag = tag)
					}
				}
			}

			if (matchCheck) {
				if (debugMode) {
					// Draw a rectangle around the supposed best matching location and then save the match into a file in /files/temp/ directory. This is for debugging purposes to see if this
					// algorithm found the match accurately or not.
					if (matchFilePath != "") {
						Imgproc.rectangle(sourceMat, matchLocation, Point(matchLocation.x + templateMat.cols(), matchLocation.y + templateMat.rows()), Scalar(0.0, 128.0, 0.0), 5)
						Imgcodecs.imwrite("$matchFilePath/match.png", sourceMat)
					}
				}

				// Center the coordinates so that any tap gesture would be directed at the center of that match location instead of the default
				// position of the top left corner of the match location.
				matchLocation.x += (templateMat.cols() / 2)
				matchLocation.y += (templateMat.rows() / 2)

				// If a custom region was specified, readjust the coordinates to reflect the fullscreen source screenshot.
				if (!region.contentEquals(intArrayOf(0, 0, 0, 0))) {
					matchLocation.x = sourceBitmap.width - (region[0] + matchLocation.x)
					matchLocation.y = sourceBitmap.height - (region[1] + matchLocation.y)
				}

				return true
			}
		}

		return false
	}

	/**
	 * Search through the whole source screenshot for all matches to the template image.
	 *
	 * @param sourceBitmap Bitmap from the /files/temp/ folder.
	 * @param templateBitmap Bitmap from the assets folder.
	 * @param region Specify the region consisting of (x, y, width, height) of the source screenshot to template match. Defaults to (0, 0, 0, 0) which is equivalent to searching the full image.
	 * @param customConfidence Specify a custom confidence. Defaults to the confidence set in the app's settings.
	 * @return ArrayList of Point objects that represents the matches found on the source screenshot.
	 */
	private fun matchAll(sourceBitmap: Bitmap, templateBitmap: Bitmap, region: IntArray = intArrayOf(0, 0, 0, 0), customConfidence: Double = 0.0): ArrayList<Point> {
		// Scale images if the device is not 1080p which is supported by default.
		val scales: MutableList<Double> = when {
			customScale != 1.0 -> {
				mutableListOf(customScale - 0.02, customScale - 0.01, customScale, customScale + 0.01, customScale + 0.02, customScale + 0.03, customScale + 0.04)
			}
			is720p -> {
				lowerEndScales.toMutableList()
			}
			!is720p && !is1080p && !isTabletPortrait -> {
				middleEndScales.toMutableList()
			}
			isTabletPortrait && isTabletLandscape -> {
				tabletLandscapeScales.toMutableList()
			}
			isTabletPortrait && !isTabletLandscape -> {
				tabletPortraitScales.toMutableList()
			}
			else -> {
				mutableListOf(1.0)
			}
		}

		val setConfidence: Double = if (customConfidence == 0.0) {
			confidenceAll
		} else {
			customConfidence
		}

		var matchCheck = false
		var newScale = 0.0
		val sourceMat = Mat()
		val templateMat = Mat()
		var resultMat = Mat()

		// Set templateMat at whatever scale it found the very first match for the next while loop.
		while (!matchCheck && scales.isNotEmpty()) {
			newScale = decimalFormat.format(scales.removeFirst()).replace(",", ".").toDouble()

			val tmp: Bitmap = if (newScale != 1.0) {
				Bitmap.createScaledBitmap(templateBitmap, (templateBitmap.width * newScale).toInt(), (templateBitmap.height * newScale).toInt(), true)
			} else {
				templateBitmap
			}

			// Create the Mats of both source and template images.
			Utils.bitmapToMat(sourceBitmap, sourceMat)
			Utils.bitmapToMat(tmp, templateMat)

			// Make the Mats grayscale for the source and the template.
			Imgproc.cvtColor(sourceMat, sourceMat, Imgproc.COLOR_BGR2GRAY)
			Imgproc.cvtColor(templateMat, templateMat, Imgproc.COLOR_BGR2GRAY)

			// Create the result matrix.
			val resultColumns: Int = sourceMat.cols() - templateMat.cols() + 1
			val resultRows: Int = sourceMat.rows() - templateMat.rows() + 1
			if (resultColumns < 0 || resultRows < 0) {
				break
			}

			resultMat = Mat(resultRows, resultColumns, CvType.CV_32FC1)

			// Now perform the matching and localize the result.
			Imgproc.matchTemplate(sourceMat, templateMat, resultMat, matchMethod)
			val mmr: Core.MinMaxLocResult = Core.minMaxLoc(resultMat)

			matchLocation = Point()

			// Depending on which matching method was used, the algorithms determine which location was the best.
			if ((matchMethod == Imgproc.TM_SQDIFF || matchMethod == Imgproc.TM_SQDIFF_NORMED) && mmr.minVal <= (1.0 - setConfidence)) {
				matchLocation = mmr.minLoc
				matchCheck = true

				// Draw a rectangle around the match on the source Mat. This will prevent false positives and infinite looping on subsequent matches.
				Imgproc.rectangle(sourceMat, matchLocation, Point(matchLocation.x + templateMat.cols(), matchLocation.y + templateMat.rows()), Scalar(0.0, 0.0, 0.0), 20)

				// Center the location coordinates and then save it.
				matchLocation.x += (templateMat.cols() / 2)
				matchLocation.y += (templateMat.rows() / 2)

				// If a custom region was specified, readjust the coordinates to reflect the fullscreen source screenshot.
				if (!region.contentEquals(intArrayOf(0, 0, 0, 0))) {
					matchLocation.x = sourceBitmap.width - (sourceBitmap.width - (region[0] + matchLocation.x))
					matchLocation.y = sourceBitmap.height - (sourceBitmap.height - (region[1] + matchLocation.y))
				}

				matchLocations.add(matchLocation)
			} else if ((matchMethod != Imgproc.TM_SQDIFF && matchMethod != Imgproc.TM_SQDIFF_NORMED) && mmr.maxVal >= setConfidence) {
				matchLocation = mmr.maxLoc
				matchCheck = true

				// Draw a rectangle around the match on the source Mat. This will prevent false positives and infinite looping on subsequent matches.
				Imgproc.rectangle(sourceMat, matchLocation, Point(matchLocation.x + templateMat.cols(), matchLocation.y + templateMat.rows()), Scalar(0.0, 0.0, 0.0), 20)

				// Center the location coordinates and then save it.
				matchLocation.x += (templateMat.cols() / 2)
				matchLocation.y += (templateMat.rows() / 2)

				// If a custom region was specified, readjust the coordinates to reflect the fullscreen source screenshot.
				if (!region.contentEquals(intArrayOf(0, 0, 0, 0))) {
					matchLocation.x = sourceBitmap.width - (sourceBitmap.width - (region[0] + matchLocation.x))
					matchLocation.y = sourceBitmap.height - (sourceBitmap.height - (region[1] + matchLocation.y))
				}

				matchLocations.add(matchLocation)
			}
		}

		// Loop until all other matches are found and break out when there are no more to be found.
		while (matchCheck) {
			// Now perform the matching and localize the result.
			Imgproc.matchTemplate(sourceMat, templateMat, resultMat, matchMethod)
			val mmr: Core.MinMaxLocResult = Core.minMaxLoc(resultMat)

			// Format minVal or maxVal.
			val minVal: Double = decimalFormat.format(mmr.minVal).replace(",", ".").toDouble()
			val maxVal: Double = decimalFormat.format(mmr.maxVal).replace(",", ".").toDouble()

			if ((matchMethod == Imgproc.TM_SQDIFF || matchMethod == Imgproc.TM_SQDIFF_NORMED) && mmr.minVal <= (1.0 - setConfidence)) {
				val tempMatchLocation: Point = mmr.minLoc

				// Draw a rectangle around the match on the source Mat. This will prevent false positives and infinite looping on subsequent matches.
				Imgproc.rectangle(sourceMat, tempMatchLocation, Point(tempMatchLocation.x + templateMat.cols(), tempMatchLocation.y + templateMat.rows()), Scalar(0.0, 0.0, 0.0), 20)

				if (debugMode) {
					game.printToLog("[DEBUG] Match found with $minVal <= ${1.0 - setConfidence} at Point $matchLocation with scale: $newScale.", tag = tag)
					Imgcodecs.imwrite("$matchFilePath/matchAll.png", sourceMat)
				}

				// Center the location coordinates and then save it.
				tempMatchLocation.x += (templateMat.cols() / 2)
				tempMatchLocation.y += (templateMat.rows() / 2)

				// If a custom region was specified, readjust the coordinates to reflect the fullscreen source screenshot.
				if (!region.contentEquals(intArrayOf(0, 0, 0, 0))) {
					tempMatchLocation.x = sourceBitmap.width - (sourceBitmap.width - (region[0] + tempMatchLocation.x))
					tempMatchLocation.y = sourceBitmap.height - (sourceBitmap.height - (region[1] + tempMatchLocation.y))
				}

				if (!matchLocations.contains(tempMatchLocation) && !matchLocations.contains(Point(tempMatchLocation.x + 1.0, tempMatchLocation.y)) &&
					!matchLocations.contains(Point(tempMatchLocation.x, tempMatchLocation.y + 1.0)) && !matchLocations.contains(Point(tempMatchLocation.x + 1.0, tempMatchLocation.y + 1.0))
				) {
					matchLocations.add(tempMatchLocation)
				} else if (matchLocations.contains(tempMatchLocation)) {
					// Prevent infinite looping if the same location is found over and over again.
					break
				}
			} else if ((matchMethod != Imgproc.TM_SQDIFF && matchMethod != Imgproc.TM_SQDIFF_NORMED) && mmr.maxVal >= setConfidence) {
				val tempMatchLocation: Point = mmr.maxLoc

				// Draw a rectangle around the match on the source Mat. This will prevent false positives and infinite looping on subsequent matches.
				Imgproc.rectangle(sourceMat, tempMatchLocation, Point(tempMatchLocation.x + templateMat.cols(), tempMatchLocation.y + templateMat.rows()), Scalar(0.0, 0.0, 0.0), 20)

				if (debugMode) {
					game.printToLog("[DEBUG] Match found with $maxVal >= $setConfidence at Point $matchLocation with scale: $newScale.", tag = tag)
					Imgcodecs.imwrite("$matchFilePath/matchAll.png", sourceMat)
				}

				// Center the location coordinates and then save it.
				tempMatchLocation.x += (templateMat.cols() / 2)
				tempMatchLocation.y += (templateMat.rows() / 2)

				// If a custom region was specified, readjust the coordinates to reflect the fullscreen source screenshot.
				if (!region.contentEquals(intArrayOf(0, 0, 0, 0))) {
					tempMatchLocation.x = sourceBitmap.width - (sourceBitmap.width - (region[0] + tempMatchLocation.x))
					tempMatchLocation.y = sourceBitmap.height - (sourceBitmap.height - (region[1] + tempMatchLocation.y))
				}

				if (!matchLocations.contains(tempMatchLocation) && !matchLocations.contains(Point(tempMatchLocation.x + 1.0, tempMatchLocation.y)) &&
					!matchLocations.contains(Point(tempMatchLocation.x, tempMatchLocation.y + 1.0)) && !matchLocations.contains(Point(tempMatchLocation.x + 1.0, tempMatchLocation.y + 1.0))
				) {
					matchLocations.add(tempMatchLocation)
				} else if (matchLocations.contains(tempMatchLocation)) {
					// Prevent infinite looping if the same location is found over and over again.
					break
				}
			} else {
				break
			}
		}

		return matchLocations
	}

	/**
	 * Open the source and template image files and return Bitmaps for them.
	 *
	 * @param templateName File name of the template image.
	 * @param templateFolderName Name of the subfolder in /assets/ that the template image is in.
	 * @return A Pair of source and template Bitmaps.
	 */
	private fun getBitmaps(templateName: String, templateFolderName: String): Pair<Bitmap?, Bitmap?> {
		var sourceBitmap: Bitmap? = null

		// Keep swiping a little bit up and down to trigger a new image for ImageReader to grab.
		while (sourceBitmap == null) {
			sourceBitmap = MediaProjectionService.takeScreenshotNow()

			if (sourceBitmap == null) {
				game.gestureUtils.swipe(500f, 500f, 500f, 400f, 100L)
				game.gestureUtils.swipe(500f, 400f, 500f, 500f, 100L)
				game.wait(0.5)
			}
		}

		var templateBitmap: Bitmap?

		// Get the Bitmap from the template image file inside the specified folder.
		myContext.assets?.open("$templateFolderName/$templateName.webp").use { inputStream ->
			// Get the Bitmap from the template image file and then start matching.
			templateBitmap = BitmapFactory.decodeStream(inputStream)
		}

		return if (templateBitmap != null) {
			Pair(sourceBitmap, templateBitmap)
		} else {
			if (debugMode) {
				game.printToLog("[ERROR] One or more of the Bitmaps are null.", tag = tag, isError = true)
			}

			Pair(sourceBitmap, templateBitmap)
		}
	}

	/**
	 * Finds the location of the specified image from the /images/ folder inside assets.
	 *
	 * @param templateName File name of the template image.
	 * @param tries Number of tries before failing. Defaults to 5.
	 * @param region Specify the region consisting of (x, y, width, height) of the source screenshot to template match. Defaults to (0, 0, 0, 0) which is equivalent to searching the full image.
	 * @param suppressError Whether or not to suppress saving error messages to the log. Defaults to false.
	 * @param testMode Flag to test and get a valid scale for device compatibility.
	 * @return Point object containing the location of the match or null if not found.
	 */
	fun findImage(templateName: String, tries: Int = 5, region: IntArray = intArrayOf(0, 0, 0, 0), suppressError: Boolean = false, testMode: Boolean = false): Point? {
		val folderName = "images"
		var numberOfTries = tries

		if (debugMode) {
			game.printToLog("\n[DEBUG] Starting process to find the ${templateName.uppercase()} image...", tag = tag)
		}

		// If Test Mode is enabled, prepare for it by setting initial scale.
		if (testMode) {
			numberOfTries = 80
			customScale = 0.20
		}

		while (numberOfTries > 0) {
			val (sourceBitmap, templateBitmap) = getBitmaps(templateName, folderName)

			if (sourceBitmap != null && templateBitmap != null) {
				val resultFlag: Boolean = match(sourceBitmap, templateBitmap, region, useSingleScale = true)
				if (!resultFlag) {
					if (testMode) {
						// Increment scale by 0.01 until a match is found if Test Mode is enabled.
						customScale += 0.01
						customScale = decimalFormat.format(customScale).toDouble()
					}

					numberOfTries -= 1
					if (numberOfTries <= 0) {
						if (!suppressError) {
							game.printToLog("[WARNING] Failed to find the ${templateName.uppercase()} image.", tag = tag)
						}

						break
					}

					Log.d(tag, "Failed to find the ${templateName.uppercase()} image. Trying again...")

					if (!testMode) {
						game.wait(0.5)
					}
				} else {
					if (testMode) {
						// Create a range of scales for user recommendation.
						val scale0: Double = decimalFormat.format(customScale).toDouble()
						val scale1: Double = decimalFormat.format(scale0 + 0.01).toDouble()
						val scale2: Double = decimalFormat.format(scale0 + 0.02).toDouble()
						val scale3: Double = decimalFormat.format(scale0 + 0.03).toDouble()
						val scale4: Double = decimalFormat.format(scale0 + 0.04).toDouble()

						game.printToLog(
							"[SUCCESS] Found the ${templateName.uppercase()} at $matchLocation with scale $scale0.\n\nRecommended to use scale $scale1, $scale2, $scale3 or $scale4.",
							tag = tag
						)
					} else {
						game.printToLog("[SUCCESS] Found the ${templateName.uppercase()} at $matchLocation.", tag = tag)
					}

					return matchLocation
				}
			}
		}

		return null
	}

	/**
	 * Confirms whether or not the bot is at the specified location from the /headers/ folder inside assets.
	 *
	 * @param templateName File name of the template image.
	 * @param tries Number of tries before failing. Defaults to 5.
	 * @param region Specify the region consisting of (x, y, width, height) of the source screenshot to template match. Defaults to (0, 0, 0, 0) which is equivalent to searching the full image.
	 * @param suppressError Whether or not to suppress saving error messages to the log.
	 * @return True if the current location is at the specified location. False otherwise.
	 */
	fun confirmLocation(templateName: String, tries: Int = 5, region: IntArray = intArrayOf(0, 0, 0, 0), suppressError: Boolean = false): Boolean {
		val folderName = "locations"
		var numberOfTries = tries

		if (debugMode) {
			game.printToLog("\n[DEBUG] Starting process to find the ${templateName.uppercase()} location image...", tag = tag)
		}

		while (numberOfTries > 0) {
			val (sourceBitmap, templateBitmap) = getBitmaps(templateName, folderName)

			if (sourceBitmap != null && templateBitmap != null) {
				val resultFlag: Boolean = match(sourceBitmap, templateBitmap, region)
				if (!resultFlag) {
					numberOfTries -= 1
					if (numberOfTries <= 0) {
						break
					}

					game.wait(0.5)
				} else {
					game.printToLog("[SUCCESS] Current location confirmed to be at ${templateName.uppercase()}.", tag = tag)
					return true
				}
			} else {
				break
			}
		}

		if (!suppressError) {
			game.printToLog("[WARNING] Failed to confirm the bot location at ${templateName.uppercase()}.", tag = tag)
		}

		return false
	}

	/**
	 * Finds all occurrences of the specified image. Has an optional parameter to specify looking in the items folder instead.
	 *
	 * @param templateName File name of the template image.
	 * @param region Specify the region consisting of (x, y, width, height) of the source screenshot to template match. Defaults to (0, 0, 0, 0) which is equivalent to searching the full image.
	 * @param customConfidence Accuracy threshold for matching. Defaults to 0.8.
	 * @return An ArrayList of Point objects containing all the occurrences of the specified image or null if not found.
	 */
	fun findAll(templateName: String, folderName: String, region: IntArray = intArrayOf(0, 0, 0, 0), customConfidence: Double = 0.8): ArrayList<Point> {
		if (debugMode) {
			game.printToLog("\n[DEBUG] Starting process to find all ${templateName.uppercase()} images...", tag = tag)
		}

		val (sourceBitmap, templateBitmap) = getBitmaps(templateName, folderName)

		// Clear the ArrayList first before attempting to find all matches.
		matchLocations.clear()

		if (sourceBitmap != null && templateBitmap != null) {
			matchAll(sourceBitmap, templateBitmap, region = region, customConfidence = customConfidence)
		}

		// Sort the match locations by ascending x and y coordinates.
		matchLocations.sortBy { it.x }
		matchLocations.sortBy { it.y }

		if (debugMode) {
			game.printToLog("[DEBUG] Found match locations for $templateName: $matchLocations.", tag = tag)
		}

		return matchLocations
	}

	/**
	 * Waits for the specified image to vanish from the screen.
	 *
	 * @param templateName File name of the template image.
	 * @param timeout Amount of time to wait before timing out. Default is 5 seconds.
	 * @param region Specify the region consisting of (x, y, width, height) of the source screenshot to template match. Defaults to (0, 0, 0, 0) which is equivalent to searching the full image.
	 * @param suppressError Whether or not to suppress saving error messages to the log.
	 * @return True if the specified image vanished from the screen. False otherwise.
	 */
	fun waitVanish(templateName: String, timeout: Int = 5, region: IntArray = intArrayOf(0, 0, 0, 0), suppressError: Boolean = false): Boolean {
		game.printToLog("[INFO] Now waiting for $templateName to vanish from the screen...", tag = tag)

		var remaining = timeout
		if (findImage(templateName, tries = 1, region = region, suppressError = suppressError) == null) {
			return true
		} else {
			while (findImage(templateName, tries = 1, region = region, suppressError = suppressError) != null) {
				game.wait(1.0)
				remaining -= 1
				if (remaining <= 0) {
					return false
				}
			}

			return true
		}
	}
}