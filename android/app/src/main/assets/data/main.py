import json
import requests
import os

class CardScraper:
    def start(self):
        page_number = 1
        card_list = []

        while True:
            # Iterate through each page until it returns an empty json array.
            response = requests.get(f"https://www.masterduelmeta.com/api/v1/cards?page={page_number}&limit=3000")
            data = json.loads(response.text)
            if len(data) == 0:
                # Now save all the accumlated cards into the json file.
                print(f"{len(card_list)} Cards")
                with open("cards.json", "w", encoding = "utf-8") as file:
                    json.dump(card_list, file, indent=4)

                with open(os.path.dirname(__file__) + "../../../../../../src/data/cards.json", "w", encoding = "utf-8") as file:
                    json.dump(card_list, file, indent=4)

                return

            for card in data:
                # Filter out cards that are not in Master Duel yet and then append the rest to the list.
                try:
                    card["rarity"]
                    card_list.append({"name": card["name"], "rarity": card["rarity"], "konamiID": card["konamiID"] })
                except KeyError:
                    pass

            page_number += 1


if __name__ == '__main__':
    scraper = CardScraper()
    scraper.start()