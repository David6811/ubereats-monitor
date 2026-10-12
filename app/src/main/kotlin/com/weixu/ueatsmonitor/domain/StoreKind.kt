package com.weixu.ueatsmonitor.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Data. One shop from the bundled table, with the little we say about it. */
data class Store(
    val name: String,
    val kind: String,
    val setting: String,
    val at: GeoPoint,
)

/**
 * Calculation. What kind of shop a pickup line names, and where it stands.
 *
 * The pickup arrives as OCR read it - a scrap of the icon in front, the suburb
 * in brackets behind, and the name itself often a letter or two wrong - so the
 * table is searched for a name the line nearly contains. An exact match always
 * wins; only when there is none does a near one count, and then by no more than
 * a couple of letters.
 */
object StoreKinds {

    fun parse(csv: String): List<Store> = csv.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val parts = line.split(',')
            if (parts.size < 5) return@mapNotNull null
            val name = parts[0].trim()
            val kind = parts[3].trim()
            val latitude = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            val longitude = parts[2].trim().toDoubleOrNull() ?: return@mapNotNull null
            if (name.isEmpty() || kind.isEmpty()) null
            else Store(name, kind, parts[4].trim(), GeoPoint(latitude, longitude))
        }
        .toList()

    /**
     * The shop a pickup line names.
     *
     * [suburbs] decides between branches of a chain. Uber writes the branch in
     * brackets - "McDonald's (Keysborough)" - and the table holds a dozen
     * McDonald's, all with the same name; without the brackets the same one won
     * every time, whichever branch the card named. On 504 jobs that picked the
     * wrong branch for 68 of the 135 shops the table could place at all, and
     * eleven of those wrong branches sat inside a no-go box, so the job was
     * refused for standing somewhere it was not.
     */
    fun find(pickup: String, stores: List<Store>, suburbs: List<Suburb> = emptyList()): Store? {
        val line = fold(pickup)
        if (line.isEmpty()) return null
        val named = stores.filter { it.name.length >= MIN_NAME && line.contains(fold(it.name)) }
        val longest = named.maxOfOrNull { it.name.length }
        val exact = named.filter { it.name.length == longest }
        return when {
            exact.isEmpty() -> nearest(line, stores)
            exact.size == 1 -> exact.first()
            // Several shops of that name and no way to tell them apart. The
            // position is what a no-go box is tested against, so a guess here
            // refuses a job for standing somewhere it is not.
            suburbs.isEmpty() -> exact.first()
            else -> branch(pickup, exact, suburbs)
        }
    }

    /**
     * Which of several same-named shops the line means, by the suburb it names,
     * or none when the line does not say.
     *
     * The brackets first: a shop's own name carries suburbs too, and "Dingley
     * Fish & Chips" is not in Dingley every time it is mentioned. The branch has
     * to be [NEAR_ENOUGH_KM] of what the line said to be believed - a chain with
     * no branch in that suburb otherwise snaps to its nearest one, which on this
     * board was up to 22km away and twice landed inside a no-go box.
     */
    private fun branch(pickup: String, candidates: List<Store>, suburbs: List<Suburb>): Store? {
        val inBrackets = Regex("""\(([^)]*)\)""").findAll(pickup).lastOrNull()?.groupValues?.get(1)
        val said = inBrackets?.let { SuburbIndex.findAll(it, suburbs).firstOrNull() }
            ?: SuburbIndex.findAll(pickup, suburbs).firstOrNull()
            ?: return null
        val nearest = candidates.minByOrNull { Geo.straightLine(it.at, said.at).value } ?: return null
        return nearest.takeIf { Geo.straightLine(it.at, said.at).value * KM_PER_MILE <= NEAR_ENOUGH_KM }
    }

    fun of(pickup: String, stores: List<Store>): String? = find(pickup, stores)?.kind

    /**
     * The closest name within a couple of letters. "Nandos" and "Nandoe" are both
     * one letter from "Nando's", and both are what OCR made of the same shopfront.
     */
    private fun nearest(line: String, stores: List<Store>): Store? {
        var best: Store? = null
        var bestCost = Int.MAX_VALUE
        for (store in stores) {
            val name = fold(store.name)
            if (name.length < MIN_NEAR) continue
            val budget = min(MAX_SLIP, max(1, name.length / 6))
            if (budget >= bestCost) continue
            val cost = windowCost(line, name, budget) ?: continue
            if (cost < bestCost || (cost == bestCost && store.name.length > (best?.name?.length ?: 0))) {
                best = store
                bestCost = cost
            }
        }
        return best
    }

    /** The cheapest edit distance from [name] to any stretch of [line] its length. */
    private fun windowCost(line: String, name: String, budget: Int): Int? {
        if (line.length + budget < name.length) return null
        var best: Int? = null
        val widths = (name.length - budget)..(name.length + budget)
        for (width in widths) {
            if (width < 1) continue
            for (start in 0..(line.length - width).coerceAtLeast(-1)) {
                if (start + width > line.length) break
                val cost = distance(line.substring(start, start + width), name, budget) ?: continue
                if (best == null || cost < best!!) best = cost
                if (best == 0) return 0
            }
        }
        return best
    }

    /** Levenshtein, abandoned as soon as every path already costs more than [budget]. */
    private fun distance(a: String, b: String, budget: Int): Int? {
        if (abs(a.length - b.length) > budget) return null
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowBest = current[0]
            for (j in 1..b.length) {
                val substitute = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(substitute, min(previous[j] + 1, current[j - 1] + 1))
                rowBest = min(rowBest, current[j])
            }
            if (rowBest > budget) return null
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length].takeIf { it <= budget }
    }

    /** Letters and digits only, lowercased: apostrophes and ® are OCR's to lose. */
    private fun fold(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }

    /**
     * How far a branch may be from the suburb the line named and still be taken
     * for that branch. The board's shops sit in two groups: 62 within 2km of
     * the suburb named, and 61 beyond 5km - those are the chains with no branch
     * in the service area at all. Three is the gap between them, and moving it
     * to four lets two false no-go hits back in.
     */
    private const val NEAR_ENOUGH_KM = 3.0

    private const val KM_PER_MILE = 1.609344

    /** A name this short matches half the table by accident. */
    private const val MIN_NAME = 4

    /**
     * A near match needs a longer name than an exact one. At four letters,
     * "Asia" is one edit from the "asta" inside "Mario's Pizza And Pasta" - and
     * that is exactly the answer it gave before this floor existed.
     */
    private const val MIN_NEAR = 6

    /** More wrong letters than this and it is a different shop. */
    private const val MAX_SLIP = 2

    /** OpenStreetMap's word for it, in the driver's language. */
    fun label(kind: String, lang: Lang): String {
        val english = lang == Lang.ENGLISH
        return when (kind) {
            "restaurant" -> if (english) "restaurant" else "餐厅"
            "fast_food" -> if (english) "fast food" else "快餐"
            "cafe" -> if (english) "cafe" else "咖啡"
            "convenience" -> if (english) "convenience" else "便利店"
            "supermarket" -> if (english) "supermarket" else "超市"
            "bakery" -> if (english) "bakery" else "面包"
            "alcohol" -> if (english) "bottle shop" else "酒铺"
            "butcher" -> if (english) "butcher" else "肉店"
            "pub", "bar" -> if (english) "pub" else "酒吧"
            "greengrocer" -> if (english) "greengrocer" else "果蔬"
            "ice_cream" -> if (english) "ice cream" else "冰淇淋"
            "deli" -> if (english) "deli" else "熟食"
            "chemist", "pharmacy" -> if (english) "chemist" else "药店"
            "fuel" -> if (english) "petrol" else "加油站"
            else -> kind
        }
    }

    /** No car park of its own: you will be circling, or walking. */
    fun hardToPark(setting: String): Boolean = setting == "MALL" || setting == "STRIP"

    /** Where the shop stands, which is where you will be parking. */
    fun where(setting: String, lang: Lang): String? {
        val english = lang == Lang.ENGLISH
        return when (setting) {
            "MALL" -> if (english) "mall" else "商场"
            "STRIP" -> if (english) "strip" else "主街"
            "STANDALONE_PARKING" -> if (english) "own car park" else "有停车场"
            else -> null
        }
    }
}
