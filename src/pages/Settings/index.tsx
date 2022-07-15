import Ionicons from "react-native-vector-icons/Ionicons"
import React, { useContext, useEffect, useState } from "react"
import SnackBar from "rn-snackbar-component"
import { BotStateContext, Cards, Deck } from "../../context/BotStateContext"
import { ScrollView, StyleSheet, View } from "react-native"
import TitleDivider from "../../components/TitleDivider"
import CustomButton from "../../components/CustomButton"
import DecklistMessageLog from "../../components/DecklistMessageLog"
import { TextInput } from "react-native-paper"
import { Divider, Text } from "react-native-elements"
import cards from "../../data/cards.json"

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
    const [list, setList] = useState<string[]>([])

    const bsc = useContext(BotStateContext)

    useEffect(() => {
        // Manually set this flag to false as the snackbar autohiding does not set this to false automatically.
        setSnackbarOpen(true)
        setTimeout(() => setSnackbarOpen(false), 1500)
    }, [bsc.readyStatus])

    useEffect(() => {
        if (list.length !== 0) {
            interface Temp {
                name: string
            }

            const newDeck: Deck = {
                main: [],
                extra: [],
            }

            let isMain = false
            let isExtra = false
            let isSide = false

            // Iterate through each card in the list from ygoprodeck and compare Konami IDs to determine the rest of the card's data.
            list.forEach((cardID) => {
                if (cardID !== "") {
                    if (cardID === "#main") {
                        isMain = true
                        isExtra = false
                    } else if (cardID === "#extra") {
                        isMain = false
                        isExtra = true
                    } else if (cardID === "!side") {
                        isSide = true
                    } else if (!isSide) {
                        let formatCheck = true
                        try {
                            if (Number.isNaN(Number(cardID))) throw Error
                        } catch {
                            formatCheck = false
                        }

                        if (formatCheck) {
                            // Grab the rest of the card's data from the JSON file.
                            // In addition, account for cases where the decklist uses alternative artwork so the cardID needs to be incremented by 1.
                            var card = cards.find((ele) => Number(ele.konamiID) === Number(cardID) || Number(ele.konamiID) === Number(cardID) + 1) as Temp

                            // Determine if the card already exists in the temp list.
                            var index = -1
                            if (isMain) {
                                index = newDeck.main.findIndex((element) => {
                                    return element.card.name === card.name
                                })
                            } else if (isExtra) {
                                index = newDeck.extra.findIndex((element) => {
                                    return element.card.name === card.name
                                })
                            } else {
                                console.warn("Invalid card element detected.")
                            }

                            if (index !== -1) {
                                // If it already exists, then increment the amount.
                                if (isMain) {
                                    newDeck.main[index].amount += 1
                                } else if (isExtra) {
                                    newDeck.extra[index].amount += 1
                                }
                            } else {
                                // If it does not exist, then push the new card to the temp list.
                                let newCard: Cards = {
                                    card: {
                                        name: card.name,
                                    },
                                    amount: 1,
                                }

                                if (isMain) {
                                    newDeck.main.push(newCard)
                                } else if (isExtra) {
                                    newDeck.extra.push(newCard)
                                }
                            }
                        }
                    }
                }
            })

            bsc.setSettings({ ...bsc.settings, deck: newDeck })
        }
    }, [list])

    // Fetch the decklist from the provided url.
    const fetchData = async () => {
        if (bsc.settings.url.includes("masterduelmeta")) {
            // This is the workflow for the masterduelmeta website.
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

                console.log("Preparing decklist from masterduelmeta...")

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
        } else if (bsc.settings.url.includes("ygoprodeck")) {
            // This is the workflow for the ygoprodeck website.
            try {
                const link = bsc.settings.url
                const res = await fetch(link)

                // Parse the link array from the headers.
                const linkElement: string = res.headers["map"]["link"].split(",").slice(2, 3)[0]

                // Now grab the Deck ID from the URL.
                const idNumber: number = Number(linkElement.replace("<https://ygoprodeck.com/?p=", "").replace(">; rel=shortlink", "").trim())

                console.log("Preparing decklist from ygoprodeck...")

                // Construct the request object and fetch the list of Konami IDs from the .ydk file using the Deck ID.
                const request = new XMLHttpRequest()
                request.onload = (e) => {
                    if (request.status === 200) {
                        // Convert the string into an array of strings split up by the newlines.
                        const tempList = request.responseText.split("\n")
                        tempList.forEach((listItem, index) => {
                            tempList[index] = listItem.replace("\r", "").replace("\n", "")
                        })

                        setList(tempList)
                    } else {
                        console.warn("error")
                    }
                }
                request.open("GET", `https://ygoprodeck.com/YGOPRO_Decks/3/${idNumber}.ydk`)
                request.send()
            } catch (e) {
                console.warn(e)
            }
        } else {
            console.error("Invalid URL entered or website is unsupported.")
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
                    subtitle="Enter the URL and then press the Analyze button to fetch the decklist in text form. If successful, this will enable the Start button on the Home page."
                    hasIcon={true}
                    iconName="bag-personal"
                    iconColor="#000"
                />

                <Divider style={{ marginBottom: 10 }} />
                <Text style={{ marginBottom: 10, color: "black" }}>{`Supported websites are:\n\n• masterduelmeta.com\n• ygoprodeck.com`}</Text>
                <Divider style={{ marginBottom: 10 }} />

                <TextInput
                    label="Decklist URL"
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
