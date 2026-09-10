package com.weixu.ueatsmonitor.domain

/** Data. One shop from the bundled table, with the little we say about it. */
data class Store(val name: String, val kind: String)

/**
 * Calculation. What kind of shop a pickup line names.
 *
 * The pickup arrives as OCR read it - a scrap of the icon in front, the suburb
 * in brackets behind - so the table is searched for a name the line contains
 * rather than matched whole. The longest such name wins, or "Coles" would answer
 * for "Coles Express".
 */
object StoreKinds {

    fun parse(csv: String): List<Store> = csv.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val parts = line.split(',')
            if (parts.size < 4) return@mapNotNull null
            val name = parts[0].trim()
            val kind = parts[3].trim()
            if (name.isEmpty() || kind.isEmpty()) null else Store(name, kind)
        }
        .toList()

    fun of(pickup: String, stores: List<Store>): String? {
        val haystack = pickup.lowercase()
        return stores
            .filter { it.name.length >= MIN_NAME && haystack.contains(it.name.lowercase()) }
            .maxByOrNull { it.name.length }
            ?.kind
    }

    /** A name this short matches half the table by accident. */
    private const val MIN_NAME = 4

    /** OpenStreetMap's word for it, in the driver's language. */
    fun label(kind: String): String = when (kind) {
        "restaurant" -> "餐厅"
        "fast_food" -> "快餐"
        "cafe" -> "咖啡"
        "convenience" -> "便利店"
        "supermarket" -> "超市"
        "bakery" -> "面包"
        "alcohol" -> "酒铺"
        "butcher" -> "肉店"
        "pub", "bar" -> "酒吧"
        "greengrocer" -> "果蔬"
        "ice_cream" -> "冰淇淋"
        "deli" -> "熟食"
        "chemist", "pharmacy" -> "药店"
        "fuel" -> "加油站"
        else -> kind
    }
}
