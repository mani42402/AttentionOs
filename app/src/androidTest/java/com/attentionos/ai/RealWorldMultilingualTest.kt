package com.attentionos.ai

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.attentionos.domain.AttentionContext
import com.attentionos.domain.AttentionPriority
import com.attentionos.domain.PriorityEngine
import com.attentionos.domain.NotificationSignal
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The notifications people actually receive, in the languages they actually receive them in.
 *
 * Every other test in this project uses tidy English written by someone who knew what the
 * classifier was looking for. This one uses a partner asking what's for dinner, a mother's missed
 * call, a bank OTP, a landlord about rent, and school about a child — in English, Hindi, Urdu,
 * Chinese and Spanish.
 *
 * It is written to *measure* rather than to pass. The encoder is `potion-base-8M`, distilled from
 * an English model: its vocabulary is 94% Latin, with 70 Devanagari tokens, 88 Arabic and 492
 * Han. Hindi, Urdu and Chinese are therefore expected to degrade badly, and the number that
 * explains it is the unknown-token rate reported per language. The assertions cover only what
 * must hold regardless: English quality, and the keyword safety floors that protect a user whose
 * language the model cannot read.
 */
class RealWorldMultilingualTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private enum class Lang { ENGLISH, HINDI, URDU, CHINESE, SPANISH }

    /**
     * @param mustReach a person would be upset to have missed this
     * @param floor covered by a deterministic safety floor (security, finance, calls, alarms)
     */
    private data class Case(
        val lang: Lang,
        val pkg: String,
        val title: String,
        val body: String,
        val mustReach: Boolean,
        val floor: Boolean = false,
        val conversation: Boolean = false,
        val hint: String? = null,
    )

    /**
     * Scores one encoder over the whole corpus and returns per-language rows.
     *
     * Factored out so the shipped encoder and the multilingual candidates run through identical
     * code — a bake-off where the harness differs proves nothing.
     */
    private fun score(
        label: String,
        analyzer: StaticEmbeddingAnalyzer,
        vocabulary: List<String>,
    ): Triple<String, Int, Int> {
        analyzer.warmUp()
        val engine = PriorityEngine(analyzer)
        val tokenizer = WordPieceTokenizer(vocabulary)
        val unknownId = vocabulary.indexOf("[UNK]")
        val report = StringBuilder("\n=== $label ".padEnd(56, '=') + "\n")
        var totalReach = 0
        var totalMust = 0
        val missed = mutableListOf<String>()

        for (lang in Lang.entries) {
            val cases = CASES.filter { it.lang == lang }
            var reached = 0
            var mustReach = 0
            var quietCorrect = 0
            var quietTotal = 0
            var unknown = 0
            var tokens = 0
            for (case in cases) {
                val ids = tokenizer.encodePieces(
                    "${case.title} ${case.body}",
                    StaticEmbeddingAnalyzer.MAX_TOKENS,
                )
                tokens += ids.size
                unknown += ids.count { it == unknownId }
                val decision = engine.decide(
                    signal = NotificationSignal(
                        packageName = case.pkg,
                        title = case.title,
                        text = case.body,
                        postedAt = System.currentTimeMillis(),
                        isConversation = case.conversation,
                        isOngoing = false,
                        categoryHint = case.hint,
                    ),
                    context = AttentionContext(focusModeEnabled = true, hourOfDay = 14),
                    memory = null,
                )
                val prominent = decision.priority.ordinal <= AttentionPriority.HIGH.ordinal
                if (case.mustReach) {
                    mustReach++
                    if (prominent) reached++ else missed += "[$lang] ${case.title}"
                } else {
                    quietTotal++
                    if (!prominent) quietCorrect++
                }
            }
            totalReach += reached
            totalMust += mustReach
            report.append(
                "  %-8s n=%-3d unknown %5.1f%%   must-reach %2d/%-2d   quiet-right %2d/%-2d\n"
                    .format(lang, cases.size, unknown * 100.0 / tokens, reached, mustReach, quietCorrect, quietTotal),
            )
        }
        report.append("  TOTAL must-reach $totalReach/$totalMust\n")
        report.append("=".repeat(56))
        Log.i(TAG, report.toString())
        Log.i(TAG, "$label missed: ${missed.joinToString(", ")}")
        return Triple(label, totalReach, totalMust)
    }

    @Test
    fun multilingualBakeOff() {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val shippedVocab = context.assets
            .open(StaticEmbeddingAnalyzer.VOCAB_ASSET)
            .bufferedReader()
            .use { it.readLines() }
        // The 256d arm exists to justify shipping 128d rather than paying twice the size for it.
        // Regenerate the candidate with tools/build_encoder_asset.py --dims 256 to re-run.
        val mrlVocab = testAssets
            .open("candidates/mrl-vocab.txt")
            .bufferedReader()
            .use { it.readLines() }

        val results = listOf(
            score(
                "shipped: static-mrl-multilingual @128d",
                StaticEmbeddingAnalyzer(context),
                shippedVocab,
            ),
            score(
                "static-mrl-multilingual @256d",
                StaticEmbeddingAnalyzer(
                    context = context,
                    asset = EncoderAsset(
                        tablePath = "candidates/mrl-256d-q8.bin",
                        vocabPath = "candidates/mrl-vocab.txt",
                        dimensions = 256,
                        version = "static-mrl-multilingual-256d",
                        fromTestAssets = true,
                    ),
                ),
                mrlVocab,
            ),
        )
        Log.i(TAG, "\n=== bake-off summary ===")
        results.forEach { (label, reach, must) ->
            Log.i(TAG, "  %-42s %2d/%-2d".format(label, reach, must))
        }
        assertTrue("every candidate should classify something", results.all { it.second > 0 })
    }

    @Test
    fun realNotificationsAcrossLanguages() {
        val analyzer = StaticEmbeddingAnalyzer(context)
        analyzer.warmUp()
        val engine = PriorityEngine(analyzer)
        val vocabulary = context.assets
            .open(StaticEmbeddingAnalyzer.VOCAB_ASSET)
            .bufferedReader()
            .use { it.readLines() }
        val tokenizer = WordPieceTokenizer(vocabulary)
        val unknownId = vocabulary.indexOf("[UNK]")

        val report = StringBuilder("\n=== real-world notifications ===========================\n")
        val floorFailures = mutableListOf<String>()
        val missed = mutableListOf<String>()
        var englishReached = 0
        var englishMustReach = 0

        for (lang in Lang.entries) {
            val cases = CASES.filter { it.lang == lang }
            var reached = 0
            var mustReach = 0
            var quietCorrect = 0
            var quietTotal = 0
            var unknownTokens = 0
            var totalTokens = 0

            for (case in cases) {
                val text = "${case.title} ${case.body}"
                val ids = tokenizer.encodePieces(text, StaticEmbeddingAnalyzer.MAX_TOKENS)
                totalTokens += ids.size
                unknownTokens += ids.count { it == unknownId }

                val decision = engine.decide(
                    signal = NotificationSignal(
                        packageName = case.pkg,
                        title = case.title,
                        text = case.body,
                        postedAt = System.currentTimeMillis(),
                        isConversation = case.conversation,
                        isOngoing = false,
                        categoryHint = case.hint,
                    ),
                    context = AttentionContext(focusModeEnabled = true, hourOfDay = 14),
                    memory = null,
                )
                val prominent = decision.priority.ordinal <= AttentionPriority.HIGH.ordinal

                if (case.mustReach) {
                    mustReach++
                    if (prominent) {
                        reached++
                    } else {
                        missed += "[$lang] ${case.title}: \"${case.body.take(46)}\" -> " +
                            "${decision.priority} (${decision.category})"
                    }
                } else {
                    quietTotal++
                    if (!prominent) quietCorrect++
                }
                if (case.floor && !prominent) {
                    floorFailures += "[$lang] ${case.title} -> ${decision.priority}"
                }
            }

            if (lang == Lang.ENGLISH) {
                englishReached = reached
                englishMustReach = mustReach
            }
            val unknownRate = if (totalTokens == 0) 0.0 else unknownTokens * 100.0 / totalTokens
            report.append(
                "  %-8s n=%-3d unknown tokens %5.1f%%   must-reach %2d/%-2d   quiet-right %2d/%-2d\n"
                    .format(lang, cases.size, unknownRate, reached, mustReach, quietCorrect, quietTotal),
            )
        }
        report.append("========================================================")
        Log.i(TAG, report.toString())
        Log.i(TAG, "missed things a person would care about:\n  " + missed.joinToString("\n  "))

        // Only `categoryHint` floors (calls, alarms) are language-independent — those come from
        // Android, not from text. They must never fail.
        assertTrue("categoryHint floors failed: $floorFailures", floorFailures.isEmpty())

        // Recorded baseline, not a target. Raise it when the classifier improves; never relax it.
        // History: 4/11 with the English encoder and the Attention Mode score penalty in place,
        // 5/11 once the penalty was removed, 6/11 on the multilingual table.
        assertTrue(
            "English must-reach recall fell below the recorded baseline: " +
                "$englishReached/$englishMustReach",
            englishReached >= ENGLISH_RECALL_BASELINE,
        )
    }

    @Test
    fun everyScriptIsReadableByTheShippedEncoder() {
        // The inverse of the test this replaces. The English table left Chinese at 68% unknown
        // tokens and decomposed Hindi and Urdu into characters whose embeddings meant nothing;
        // the multilingual vocabulary reads all of them. A regression here means a language of
        // users silently stopped being understood, which is invisible from an English device.
        val vocabulary = context.assets
            .open(StaticEmbeddingAnalyzer.VOCAB_ASSET)
            .bufferedReader()
            .use { it.readLines() }
        val tokenizer = WordPieceTokenizer(vocabulary)
        val unknownId = vocabulary.indexOf("[UNK]")

        for (lang in Lang.entries) {
            val cases = CASES.filter { it.lang == lang }
            var unknown = 0
            var total = 0
            for (case in cases) {
                val ids = tokenizer.encodePieces(
                    "${case.title} ${case.body}",
                    StaticEmbeddingAnalyzer.MAX_TOKENS,
                )
                total += ids.size
                unknown += ids.count { it == unknownId }
            }
            val rate = unknown * 100.0 / total
            Log.i(TAG, "$lang unknown-token rate ${"%.1f".format(rate)}%")
            assertTrue(
                "$lang is ${"%.1f".format(rate)}% unknown tokens; the encoder cannot read it",
                rate < 2.0,
            )
        }
    }

    private companion object {
        const val TAG = "AttentionReal"

        /**
         * Measured on 2026-07-30, and the number this test exists to protect.
         *
         * 6 of 11. See `docs/MODEL_STRATEGY.md` for what is still missing and why.
         */
        const val ENGLISH_RECALL_BASELINE = 6

        val CASES = listOf(
            // ---------- English ----------
            Case(Lang.ENGLISH, "com.whatsapp", "Mum", "Are you coming for dinner tonight?", mustReach = true, conversation = true),
            Case(Lang.ENGLISH, "com.whatsapp", "Priya ❤️", "I'm outside your office, come down", mustReach = true, conversation = true),
            Case(Lang.ENGLISH, "com.whatsapp", "Family", "Dad is in the hospital, call me now", mustReach = true, conversation = true),
            Case(Lang.ENGLISH, "com.android.dialer", "Missed call", "Mum · 2 missed calls", mustReach = true, floor = true, hint = "call"),
            Case(Lang.ENGLISH, "com.hdfc.bank", "OTP", "123456 is your one time password. Do not share it.", mustReach = true, floor = true),
            Case(Lang.ENGLISH, "com.hdfc.bank", "Fraud alert", "A transaction of 45,000 was attempted on your card", mustReach = true, floor = true),
            Case(Lang.ENGLISH, "com.hdfc.bank", "Salary credited", "Your account has been credited with your salary", mustReach = false),
            Case(Lang.ENGLISH, "com.slack", "Rahul (Manager)", "Can you look at the outage before the client call?", mustReach = true, conversation = true),
            Case(Lang.ENGLISH, "com.gmail", "Landlord", "Rent is overdue, please transfer today", mustReach = true),
            Case(Lang.ENGLISH, "com.gmail", "Dr Sharma's clinic", "Your appointment is confirmed for tomorrow 6pm", mustReach = true),
            Case(Lang.ENGLISH, "com.school.app", "Little Flowers School", "Aarav was marked absent today", mustReach = true),
            Case(Lang.ENGLISH, "com.instagram.android", "instagram", "rohan_92 liked your photo", mustReach = false),
            Case(Lang.ENGLISH, "com.instagram.android", "instagram", "You have 3 new followers", mustReach = false),
            Case(Lang.ENGLISH, "com.zomato", "Zomato", "Your order is 5 minutes away", mustReach = false),
            Case(Lang.ENGLISH, "com.myntra", "Myntra", "FLAT 70% OFF ends tonight! Shop now", mustReach = false),
            Case(Lang.ENGLISH, "com.game.candy", "Candy Blast", "Your lives are full, come back and play!", mustReach = false),
            Case(Lang.ENGLISH, "com.linkedin", "LinkedIn", "You appeared in 9 searches this week", mustReach = false),
            Case(Lang.ENGLISH, "com.clock", "Alarm", "Alarm 6:30 AM", mustReach = true, floor = true, hint = "alarm"),

            // ---------- Hindi (Devanagari) ----------
            Case(Lang.HINDI, "com.whatsapp", "मम्मी", "बेटा आज खाने पर आ रहे हो?", mustReach = true, conversation = true),
            Case(Lang.HINDI, "com.whatsapp", "प्रिया", "मैं तुम्हारे ऑफिस के बाहर हूँ, नीचे आ जाओ", mustReach = true, conversation = true),
            Case(Lang.HINDI, "com.whatsapp", "परिवार", "पापा को अस्पताल ले जाना पड़ा, तुरंत फ़ोन करो", mustReach = true, conversation = true),
            Case(Lang.HINDI, "com.android.dialer", "छूटी कॉल", "मम्मी · 2 मिस्ड कॉल", mustReach = true, floor = true, hint = "call"),
            Case(Lang.HINDI, "com.hdfc.bank", "ओटीपी", "123456 आपका ओटीपी है। किसी के साथ साझा न करें।", mustReach = true),
            Case(Lang.HINDI, "com.hdfc.bank", "खाते से पैसे कटे", "आपके खाते से 45,000 रुपये निकाले गए", mustReach = true),
            Case(Lang.HINDI, "com.gmail", "मकान मालिक", "किराया बाकी है, आज ही भेज दीजिए", mustReach = true),
            Case(Lang.HINDI, "com.school.app", "विद्यालय", "आपका बच्चा आज स्कूल नहीं आया", mustReach = true),
            Case(Lang.HINDI, "com.myntra", "मिंत्रा", "आज रात तक 70% की छूट! अभी खरीदें", mustReach = false),
            Case(Lang.HINDI, "com.instagram.android", "इंस्टाग्राम", "रोहन ने आपकी फ़ोटो पसंद की", mustReach = false),
            Case(Lang.HINDI, "com.zomato", "ज़ोमैटो", "आपका ऑर्डर 5 मिनट में पहुँच जाएगा", mustReach = false),
            Case(Lang.HINDI, "com.game.candy", "कैंडी ब्लास्ट", "आपकी लाइफ़ पूरी हो गई, खेलने आइए!", mustReach = false),

            // ---------- Urdu (Arabic script) ----------
            Case(Lang.URDU, "com.whatsapp", "امی", "بیٹا آج کھانے پر آ رہے ہو؟", mustReach = true, conversation = true),
            Case(Lang.URDU, "com.whatsapp", "عائشہ", "میں تمہارے دفتر کے باہر ہوں، نیچے آ جاؤ", mustReach = true, conversation = true),
            Case(Lang.URDU, "com.whatsapp", "گھر والے", "ابو کو ہسپتال لے جانا پڑا، فوراً فون کرو", mustReach = true, conversation = true),
            Case(Lang.URDU, "com.android.dialer", "مسڈ کال", "امی · 2 مسڈ کالز", mustReach = true, floor = true, hint = "call"),
            Case(Lang.URDU, "com.hbl.bank", "او ٹی پی", "123456 آپ کا او ٹی پی ہے۔ کسی کو نہ بتائیں۔", mustReach = true),
            Case(Lang.URDU, "com.hbl.bank", "رقم منہا", "آپ کے اکاؤنٹ سے 45,000 روپے نکالے گئے", mustReach = true),
            Case(Lang.URDU, "com.gmail", "مالک مکان", "کرایہ باقی ہے، آج ہی بھیج دیں", mustReach = true),
            Case(Lang.URDU, "com.daraz", "دراز", "آج رات تک 70% رعایت! ابھی خریدیں", mustReach = false),
            Case(Lang.URDU, "com.instagram.android", "انسٹاگرام", "روحان نے آپ کی تصویر پسند کی", mustReach = false),
            Case(Lang.URDU, "com.foodpanda", "فوڈ پانڈا", "آپ کا آرڈر 5 منٹ میں پہنچ جائے گا", mustReach = false),

            // ---------- Chinese (Simplified) ----------
            Case(Lang.CHINESE, "com.tencent.mm", "妈妈", "今晚回家吃饭吗？", mustReach = true, conversation = true),
            Case(Lang.CHINESE, "com.tencent.mm", "小雨", "我在你公司楼下，下来吧", mustReach = true, conversation = true),
            Case(Lang.CHINESE, "com.tencent.mm", "家人群", "爸爸住院了，马上给我打电话", mustReach = true, conversation = true),
            Case(Lang.CHINESE, "com.android.dialer", "未接来电", "妈妈 · 2 个未接来电", mustReach = true, floor = true, hint = "call"),
            Case(Lang.CHINESE, "com.icbc.bank", "验证码", "您的验证码是123456，请勿告诉他人。", mustReach = true),
            Case(Lang.CHINESE, "com.icbc.bank", "账户变动", "您的账户支出45,000元", mustReach = true),
            Case(Lang.CHINESE, "com.gmail", "房东", "房租已逾期，请今天转账", mustReach = true),
            Case(Lang.CHINESE, "com.taobao", "淘宝", "今晚截止，全场7折！立即购买", mustReach = false),
            Case(Lang.CHINESE, "com.sina.weibo", "微博", "有人点赞了你的照片", mustReach = false),
            Case(Lang.CHINESE, "com.meituan", "美团", "您的订单5分钟后送达", mustReach = false),

            // ---------- Spanish (Latin script, non-English) ----------
            Case(Lang.SPANISH, "com.whatsapp", "Mamá", "¿Vienes a cenar esta noche?", mustReach = true, conversation = true),
            Case(Lang.SPANISH, "com.whatsapp", "Familia", "Papá está en el hospital, llámame ya", mustReach = true, conversation = true),
            Case(Lang.SPANISH, "com.android.dialer", "Llamada perdida", "Mamá · 2 llamadas perdidas", mustReach = true, floor = true, hint = "call"),
            Case(Lang.SPANISH, "com.bbva", "Código", "123456 es tu código de verificación. No lo compartas.", mustReach = true),
            Case(Lang.SPANISH, "com.bbva", "Cargo en tu cuenta", "Se ha realizado un cargo de 450 euros", mustReach = true),
            Case(Lang.SPANISH, "com.gmail", "Casero", "El alquiler está pendiente, transfiere hoy", mustReach = true),
            Case(Lang.SPANISH, "com.zara", "Zara", "70% de descuento solo hoy, compra ahora", mustReach = false),
            Case(Lang.SPANISH, "com.instagram.android", "instagram", "A rohan le gustó tu foto", mustReach = false),
            Case(Lang.SPANISH, "com.glovo", "Glovo", "Tu pedido llega en 5 minutos", mustReach = false),
        )
    }
}
