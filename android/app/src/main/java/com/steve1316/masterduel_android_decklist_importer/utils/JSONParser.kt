package com.steve1316.masterduel_android_decklist_importer.utils

import android.content.Context
import android.util.Log
import com.steve1316.masterduel_android_decklist_importer.MainActivity.loggerTag
import com.steve1316.masterduel_android_decklist_importer.data.Card
import com.steve1316.masterduel_android_decklist_importer.data.Deck
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class JSONParser {
	/**
	 * Initialize settings into SharedPreferences from the JSON file.
	 *
	 * @param myContext The application context.
	 */
	fun initializeSettings(myContext: Context) {
		Log.d(loggerTag, "Loading settings from JSON file to SharedPreferences...")

		// Grab the JSON object from the file.
		val jString = File(myContext.getExternalFilesDir(null), "settings.json").bufferedReader().use { it.readText() }
		val jObj = JSONObject(jString)

		//////////////////////////////////////////////////////////////////////////
		//////////////////////////////////////////////////////////////////////////
		// Manually save all key-value pairs from JSON object to SharedPreferences.
		//
		// Add more try-catch blocks to cover each JSONArray object as you need.

		try {
			val deckObj = jObj.getJSONObject("deck")
			deckObj.keys().forEach { key ->
				val jsonArray = deckObj.get(key) as JSONArray

				// Iterate through the entire JSONArray and start setting data into the Deck data class.
				var i = 0
				while (i < 1) {
					val jsonObj = jsonArray.getJSONObject(i)

					val cardObj = jsonObj.getJSONObject("card")
					val cardName = cardObj.getString("name")
					val amount = jsonObj.getInt("amount")

					val newCard = Card(cardName, amount)

					if (key == "main") {
						Deck.main.add(newCard)
					} else if (key == "extra") {
						Deck.extra.add(newCard)
					}

					i++
				}
			}
		} catch (e: Exception) {
			Log.e(loggerTag, e.toString())
		}
	}

	/**
	 * Convert JSONArray to ArrayList object.
	 *
	 * @param jsonArray The JSONArray object to be converted.
	 * @return The converted ArrayList object.
	 */
	private fun toArrayList(jsonArray: JSONArray): ArrayList<String> {
		val newArrayList: ArrayList<String> = arrayListOf()

		var i = 0
		while (i < jsonArray.length()) {
			newArrayList.add(jsonArray.get(i) as String)
			i++
		}

		return newArrayList
	}
}