package com.attentionos.domain

/**
 * What makes a notification worth interrupting someone for, written as sentences.
 *
 * These replace a hand-weighted sum. The engine used to add up nine tuned constants — a 0.28
 * base, urgency at 0.34, sender defaults worth 0.30, 0.08 for being a conversation, category
 * bonuses — and compare the total to four chosen thresholds. The model supplied one number of
 * that, so tuning a constant moved accuracy further than replacing the entire encoder did.
 *
 * Now the nearest description decides, and nothing is added up.
 *
 * **Why sentences and not keywords.** These are matched in a multilingual embedding space, so each
 * one is written once and covers every language the encoder reads — including Roman Urdu and
 * Hinglish, where the words are Latin but the language is not English. A keyword list would need
 * every phrasing in every language; this needs one description per *reason to care*.
 *
 * **Why this list is bounded.** It enumerates reasons, not phrasings. New apps appear constantly;
 * new reasons for a human to need something almost never. That is the claim this design rests on,
 * and it is falsifiable: if real users keep needing reasons that are not here, the claim is wrong
 * and a model that can be instructed in prose is the better answer.
 *
 * **Editing.** Add a sentence, run `CorpusEvaluationTest`, and read what moved. A description that
 * fixes one case and breaks three shows up immediately, which is the property a tuned prompt does
 * not have.
 */
object AttentionDescriptions {

    /**
     * The band a description argues for.
     *
     * [AttentionPriority.CRITICAL] is deliberately not describable. It is reserved for the
     * deterministic floors — calls, alarms, security — because those are promises the product
     * makes rather than judgements a model is allowed to make.
     */
    enum class Band(val priority: AttentionPriority) {
        REACH_NOW(AttentionPriority.HIGH),
        WORTH_KNOWING(AttentionPriority.MEDIUM),
        CAN_WAIT(AttentionPriority.LOW),
        NOISE(AttentionPriority.SILENT),
    }

    data class Description(val band: Band, val text: String)

    val all: List<Description> = listOf(
        // ---- REACH_NOW: someone would be upset to have missed this -----------------------
        Description(Band.REACH_NOW, "A family member has been hurt or taken to hospital and needs you"),
        Description(Band.REACH_NOW, "Someone close to me says it is an emergency and to call immediately"),
        Description(Band.REACH_NOW, "A person is waiting for me right now at an agreed place"),
        Description(Band.REACH_NOW, "Someone is asking me to come outside or come downstairs now"),
        Description(Band.REACH_NOW, "Money has left my account or a payment was taken without my permission"),
        Description(Band.REACH_NOW, "A suspicious sign-in or an attempt to access my account"),
        Description(Band.REACH_NOW, "A one-time code or password needed to confirm something now"),
        Description(Band.REACH_NOW, "A bill, rent or loan payment is overdue and must be paid today"),
        Description(Band.REACH_NOW, "My landlord or the bank is asking me to transfer money today"),
        Description(Band.REACH_NOW, "My child did not arrive at school or the school needs a parent"),
        Description(Band.REACH_NOW, "A school or nursery is asking a parent to confirm or collect their child"),
        Description(Band.REACH_NOW, "An elderly parent or dependent needs help right away"),
        Description(Band.REACH_NOW, "Something I am responsible for at work has broken and users are affected"),
        Description(Band.REACH_NOW, "My manager is asking me to look at an urgent problem right now"),
        Description(Band.REACH_NOW, "A deadline is today and something is still needed from me"),
        Description(Band.REACH_NOW, "A flight, train or booking has been cancelled or changed"),
        Description(Band.REACH_NOW, "A medical appointment, test result or prescription needs my attention"),
        Description(Band.REACH_NOW, "An official, legal, tax or immigration matter needs a response"),
        Description(Band.REACH_NOW, "A courier could not deliver and needs me to answer the door or re-arrange"),
        Description(Band.REACH_NOW, "A safety, weather or emergency warning for where I am"),
        Description(Band.REACH_NOW, "Someone needs an answer from me before they can act, and is waiting"),

        // ---- WORTH_KNOWING: real, but it can wait an hour --------------------------------
        Description(Band.WORTH_KNOWING, "A friend or family member is chatting or asking how I am"),
        Description(Band.WORTH_KNOWING, "Someone is suggesting plans for later or for the weekend"),
        Description(Band.WORTH_KNOWING, "A family member is asking whether I am coming home to eat"),
        Description(Band.WORTH_KNOWING, "A colleague has replied about ordinary work"),
        Description(Band.WORTH_KNOWING, "A meeting has been moved or notes have been shared"),
        Description(Band.WORTH_KNOWING, "Money has arrived in my account, such as a salary or refund"),
        Description(Band.WORTH_KNOWING, "A parcel has been delivered or left somewhere for me"),
        Description(Band.WORTH_KNOWING, "An appointment I already knew about has been confirmed"),

        // ---- CAN_WAIT: informational, no action ------------------------------------------
        Description(Band.CAN_WAIT, "A delivery is on its way and will arrive in a few minutes"),
        Description(Band.CAN_WAIT, "A driver is nearby with my food order"),
        Description(Band.CAN_WAIT, "An order has been confirmed and is being prepared"),
        Description(Band.CAN_WAIT, "A routine update about a device, app or software version"),
        Description(Band.CAN_WAIT, "A news headline or a daily briefing"),
        Description(Band.CAN_WAIT, "A summary of activity, statistics or a weekly report"),
        Description(Band.CAN_WAIT, "A reminder about something happening in a few days"),

        // ---- NOISE: marketing and engagement bait ----------------------------------------
        Description(Band.NOISE, "A shop is advertising a discount, sale or special offer"),
        Description(Band.NOISE, "An offer or coupon that expires soon, urging me to buy now"),
        Description(Band.NOISE, "Recommendations or new arrivals picked for me to shop"),
        Description(Band.NOISE, "Someone liked, followed or reacted to something I posted"),
        Description(Band.NOISE, "A suggestion to follow someone or to add a friend"),
        Description(Band.NOISE, "A game or app asking me to come back and play"),
        Description(Band.NOISE, "A reward, bonus or streak waiting for me in an app"),
        Description(Band.NOISE, "A newsletter, promotion or marketing email"),
    )

    /** Descriptions grouped by band, in the order the classifier compares them. */
    val byBand: Map<Band, List<String>> =
        all.groupBy(Description::band) { it.text }
}
