import React, { useContext, useEffect, useState } from "react"
import { ScrollView, StyleSheet, Text, View } from "react-native"
import { BotStateContext } from "../../context/BotStateContext"

const styles = StyleSheet.create({
    logInnerContainer: {
        width: "100%",
        backgroundColor: "#2f2f2f",
        borderStyle: "solid",
        borderRadius: 25,
        marginBottom: 25,
        elevation: 10,
    },
    logText: {
        color: "white",
        marginLeft: 20,
        fontSize: 12,
    },
    logTitle: {
        color: "white",
        margin: 20,
        fontSize: 24,
    },
})

const DecklistMessageLog = () => {
    const bsc = useContext(BotStateContext)

    const [mainCards, setMainCards] = useState("")
    const [amountOfMainCards, setAmountOfMainCards] = useState(0)
    const [extraCards, setExtraCards] = useState("")
    const [amountOfExtraCards, setAmountOfExtraCards] = useState(0)

    useEffect(() => {
        let tempMainCards = ""
        let tempAmountOfMainCards = 0
        bsc.settings.deck.main.forEach((data) => {
            tempMainCards += `x${data.amount} ${data.card.name}\n`
            tempAmountOfMainCards += data.amount
        })
        setMainCards(tempMainCards)
        setAmountOfMainCards(tempAmountOfMainCards)

        let tempExtraCards = ""
        let tempAmountOfExtraCards = 0
        bsc.settings.deck.extra.forEach((data) => {
            tempExtraCards += `x${data.amount} ${data.card.name}\n`
            tempAmountOfExtraCards += data.amount
        })
        setExtraCards(tempExtraCards)
        setAmountOfExtraCards(tempAmountOfExtraCards)
    }, [bsc.settings.deck])

    return (
        <View style={styles.logInnerContainer}>
            <ScrollView>
                <Text style={styles.logTitle}>{`Main - ${amountOfMainCards} Cards`}</Text>
                <Text style={styles.logText}>{mainCards}</Text>
                <Text style={styles.logText}>{`\n===================\n`}</Text>
                <Text style={styles.logTitle}>{`Extra - ${amountOfExtraCards} Cards`}</Text>
                <Text style={styles.logText}>{extraCards}</Text>
            </ScrollView>
        </View>
    )
}

export default DecklistMessageLog
