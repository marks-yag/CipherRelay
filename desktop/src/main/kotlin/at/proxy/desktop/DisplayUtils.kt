package at.proxy.desktop

object DisplayUtils {

    enum class Unit(val scale: Long, val displayName: String) {
        GB(1024 * 1024 * 1024L, "GB"),
        MB(1024 * 1024L, "MB"),
        KB(1024L, "KB")
    }

    data class Bytes(val amount: Double, val unit: Unit) {
        override fun toString(): String {
            return "${String.format("%.2f", amount)} ${unit.displayName}"
        }
    }

    fun toBytes(amount: Double) : Bytes {
        return Unit.entries.firstOrNull { it.scale < amount }?.let {
            Bytes(amount / it.scale, it)
        } ?: Bytes(amount, Unit.KB)
    }

}