package at.proxy.desktop

object DisplayUtils {

    enum class Unit(val scale: Long) {
        GB(1024 * 1024 * 1024L),
        MB(1024 * 1024L),
        KB(1024L),
        Byte(1)
    }

    data class Bytes(val amount: Double, val unit: Unit) {
        override fun toString(): String {
            return "${String.format("%.2f", amount)}$unit"
        }
    }

    fun toBytes(amount: Double) : Bytes {
        return Unit.values().firstOrNull { it.scale < amount }?.let {
            Bytes(amount / it.scale, it)
        } ?: Bytes(amount, Unit.Byte)
    }

    fun toBytesString(amount: Double) = toBytes(amount).toString()
}