import Ionicons from "react-native-vector-icons/Ionicons"
import React, { useContext, useEffect, useState } from "react"
import SnackBar from "rn-snackbar-component"
import { BotStateContext, Cards, Deck } from "../../context/BotStateContext"
import { ScrollView, StyleSheet, View } from "react-native"
import TitleDivider from "../../components/TitleDivider"
import CustomButton from "../../components/CustomButton"
import DecklistMessageLog from "../../components/DecklistMessageLog"
import { TextInput } from "react-native-paper"
import RNFS from "react-native-fs"

const styles = StyleSheet.create({
    root: {
        flex: 1,
        flexDirection: "column",
        justifyContent: "center",
        margin: 10,
    },
})

const Settings = () => {
    const [snackbarOpen, setSnackbarOpen] = useState<boolean>(false)

    const bsc = useContext(BotStateContext)

    useEffect(() => {
        // Manually set this flag to false as the snackbar autohiding does not set this to false automatically.
        setSnackbarOpen(true)
        setTimeout(() => setSnackbarOpen(false), 1500)
    }, [bsc.readyStatus])

    // Fetch the decklist from the provided url.
    const fetchData = async () => {
        const link = bsc.settings.url
        const newLink = link.split(link.substring(0, link.indexOf("top-decks/")) + "top-decks/")[1]
        const apiUrl = `https://www.masterduelmeta.com/api/v1/top-decks?url=/${newLink}/&amp;limit=1`

        try {
            // Get the JSON object from the API.
            const response = await fetch(apiUrl)
            const jsonString = await response.text()
            const parsedObject = JSON.parse(jsonString)
            const deck = parsedObject[0] as Deck

            const newDeck: Deck = {
                main: [],
                extra: [],
            }

            // Now iterate through main and extra arrays to constuct the deck.
            deck.main.forEach((data) => {
                let newCard: Cards = {
                    card: {
                        name: data.card.name,
                    },
                    amount: data.amount,
                }

                newDeck.main.push(newCard)
            })

            deck.extra.forEach((data) => {
                let newCard: Cards = {
                    card: {
                        name: data.card.name,
                    },
                    amount: data.amount,
                }

                newDeck.extra.push(newCard)
            })

            bsc.setSettings({ ...bsc.settings, deck: newDeck })
        } catch {
            console.warn("URL was not valid or API was changed.")
            bsc.setSettings({
                ...bsc.settings,
                deck: {
                    main: [],
                    extra: [],
                },
            })
        }

        try {
            const response = await fetch("https://www.masterduelmeta.com/api/v1/cards?limit")
            const jsonString = await response.text()
            const parsedObject = JSON.parse(jsonString)

            interface CardList {
                name: String
                rarity: String
            }

            const newCardList: CardList[] = []
            const convertedObject = parsedObject as CardList[]
            convertedObject.forEach((card) => {
                newCardList.push({ name: card.name, rarity: card.rarity })
            })

            // Now save the comprehensive card list as a JSON file in the same place as the settings.json file.
            const path = RNFS.ExternalDirectoryPath + "/cards.json"
            const toSave = JSON.stringify(newCardList, null, 4)
            await RNFS.unlink(path)
                .then(() => {
                    console.log("cards.json file successfully deleted.")
                })
                .catch(() => {
                    console.log("cards.json file does not exist so no need to delete it before saving current card list.")
                })

            await RNFS.writeFile(path, toSave)
                .then(() => {
                    console.log("Card list saved to ", path)
                })
                .catch((e) => {
                    console.error(`Error writing settings to path ${path}: ${e}`)
                })
        } catch {
            console.warn("Cards API failed to respond.")
        }
    }

    //////////////////////////////////////////////////
    //////////////////////////////////////////////////
    // Rendering

    const renderInputURLSettings = () => {
        return (
            <View>
                <TitleDivider
                    title="Enter the Decklist URL"
                    subtitle="Enter the URL from masterduelmeta.com and then press the Analyze button to fetch the decklist in text form. If successful, this will enable the Start button on the Home page."
                    hasIcon={true}
                    iconName="bag-personal"
                    iconColor="#000"
                />

                <TextInput
                    label="Master Duel Meta Decklist URL"
                    right={<TextInput.Icon name="close" onPress={() => bsc.setSettings({ ...bsc.settings, url: "" })} />}
                    mode="outlined"
                    multiline
                    value={bsc.settings.url}
                    onChangeText={(value: string) => bsc.setSettings({ ...bsc.settings, url: value })}
                    autoComplete={false}
                />

                <CustomButton title="Analyze Decklist" width={200} borderRadius={20} onPress={() => fetchData()} />

                <DecklistMessageLog />
            </View>
        )
    }

    return (
        <View style={styles.root}>
            <ScrollView>{renderInputURLSettings()}</ScrollView>

            <SnackBar
                visible={snackbarOpen}
                message={bsc.readyStatus ? "Bot is ready!" : "Bot is not ready!"}
                actionHandler={() => setSnackbarOpen(false)}
                action={<Ionicons name="close" size={30} />}
                autoHidingTime={1500}
                containerStyle={{ backgroundColor: bsc.readyStatus ? "green" : "red", borderRadius: 10 }}
                native={false}
            />
        </View>
    )
}

export default Settings
