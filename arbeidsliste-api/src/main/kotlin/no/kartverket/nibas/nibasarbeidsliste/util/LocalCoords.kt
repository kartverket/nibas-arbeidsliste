package no.kartverket.nibas.nibasarbeidsliste.util

import it.unimi.dsi.fastutil.longs.LongArrayList
import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransform
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate
import kotlin.math.roundToInt

private object Transform {
    @JvmStatic
    val fromUtm32: CoordinateTransform

    @JvmStatic
    val fromUtm35: CoordinateTransform

    @JvmStatic
    val fromGeo: CoordinateTransform

    @JvmStatic
    val toGeo: CoordinateTransform

    @JvmStatic
    val toUtm32: CoordinateTransform

    val toUtm35: CoordinateTransform


    init {
        val crsFactory = CRSFactory()
        val utm32 = crsFactory.createFromName("EPSG:25832")
        val utm33 = crsFactory.createFromName("EPSG:25833")
        val utm35 = crsFactory.createFromName("EPSG:25835")
        val geo = crsFactory.createFromName("EPSG:4326")
        CoordinateTransformFactory().apply {
            fromUtm32 = createTransform(utm32, utm33)
            fromUtm35 = createTransform(utm35, utm33)
            fromGeo = createTransform(geo, utm33)
            toGeo = createTransform(utm33, geo)
            toUtm32 = createTransform(utm33, utm32)
            toUtm35 = createTransform(utm33, utm35)
        }
    }
}

@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION)
annotation class UncheckedParams(val reason: String = "This is a low level API")

@JvmInline
value class LocalCoords @PublishedApi @UncheckedParams internal constructor(val asLong: Long) : Comparable<LocalCoords> {
    companion object {
        const val EXTENT = 536870912
        const val BIT_SIZE = 29
        const val INT_MASK = EXTENT - 1L
        const val UTM33_EXTENT = 2684354.56
        const val UTM33_CENTER_X = 500000.0
        const val UTM33_CENTER_Y = 7714626.0
        const val UTM33_RESOLUTION = UTM33_EXTENT / EXTENT
        const val UTM33_X_MIN_INCLUSIVE = UTM33_CENTER_X - UTM33_EXTENT / 2.0
        const val UTM33_X_MAX_EXCLUSIVE = (UTM33_CENTER_X + UTM33_EXTENT / 2.0)
        const val UTM33_X_MAX_INCLUSIVE = UTM33_X_MAX_EXCLUSIVE - UTM33_RESOLUTION
        const val UTM33_Y_MIN_EXCLUSIVE = UTM33_CENTER_Y - UTM33_EXTENT / 2.0
        const val UTM33_Y_MIN_INCLUSIVE = UTM33_Y_MIN_EXCLUSIVE + UTM33_RESOLUTION
        const val UTM33_Y_MAX_INCLUSIVE = (UTM33_CENTER_Y + UTM33_EXTENT / 2.0)
        val RANGE: IntRange = 0 until EXTENT

        @OptIn(UncheckedParams::class)
        @Suppress("NOTHING_TO_INLINE")
        inline operator fun invoke(x: Int, y: Int): LocalCoords {
            require(x in RANGE) { "Invalid x-value for LocalCoord: $x >= $EXTENT" }
            require(y in RANGE) { "Invalid y-value for LocalCoord: $y >= $EXTENT" }
            return LocalCoords(makeAsLong(x, y))
        }

        inline fun makeAsLong(x: Int, y: Int): Long {
            require(x in RANGE) { "Invalid x-value for LocalCoord: $x >= $EXTENT" }
            require(y in RANGE) { "Invalid y-value for LocalCoord: $y >= $EXTENT" }
            return x.toLong().shl(BIT_SIZE) or (y.toLong() and INT_MASK)
        }


        @Suppress("NOTHING_TO_INLINE")
        inline fun fromUtm33(x: Double, y: Double): LocalCoords {
            val lx = ((x - UTM33_X_MIN_INCLUSIVE) / UTM33_RESOLUTION)
            val ly = EXTENT - ((y - UTM33_Y_MIN_EXCLUSIVE) / UTM33_RESOLUTION)
            return invoke(lx.roundToInt(), ly.roundToInt())
        }


        fun fromUtm32(x: Double, y: Double): LocalCoords {
            val tranformed = ProjCoordinate().run {
                Transform.fromUtm32.transform(ProjCoordinate(x, y), this)
            }
            return fromUtm33(tranformed.x, tranformed.y)
        }

        fun fromUtm35(x: Double, y: Double): LocalCoords {
            val tranformed = ProjCoordinate().run {
                Transform.fromUtm35.transform(ProjCoordinate(x, y), this)
            }
            return fromUtm33(tranformed.x, tranformed.y)
        }
    }

    object LongComparator : Comparator<Long> {
        private const val XY_MASK = EXTENT + 1L
        override fun compare(a: Long, b: Long): Int {
            if (a == b) return 0
            for (i in 31 downTo 0) {
                val mask = XY_MASK shl i
                val cmp = (a and mask).compareTo(b and mask)
                if (cmp != 0) {
                    return cmp
                }
            }
            error("Unreachable")
        }
    }

    val x: Int inline get() = (asLong.ushr(BIT_SIZE)).toInt()
    val y: Int inline get() = (asLong and INT_MASK).toInt()

    override fun compareTo(other: LocalCoords): Int {
        return LongComparator.compare(asLong, other.asLong)
    }

    override fun toString(): String = "LocalCoord($x, $y)"

    @Suppress("NOTHING_TO_INLINE")
    inline operator fun component1(): Int = x

    @Suppress("NOTHING_TO_INLINE")
    inline operator fun component2(): Int = y

    inline operator fun times(other: LocalCoords): Int = x * other.y - other.x * y

    inline fun toUtm33(): Pair<Double, Double> {
        val lx = x.toDouble()
        val ly = EXTENT - y.toDouble()
        return Pair(UTM33_X_MIN_INCLUSIVE + lx * UTM33_RESOLUTION, UTM33_Y_MIN_EXCLUSIVE + ly * UTM33_RESOLUTION)
    }

    fun toUtm32(): Pair<Double, Double> {
        val (x, y) = toUtm33()
        val transformed = ProjCoordinate().run {
            Transform.toUtm32.transform(ProjCoordinate(x, y), this)
        }
        return transformed.x to transformed.y
    }

    fun toUtm35(): Pair<Double, Double> {
        val (x, y) = toUtm33()
        val transformed = ProjCoordinate().run {
            Transform.toUtm35.transform(ProjCoordinate(x, y), this)
        }
        return transformed.x to transformed.y
    }

}

@Suppress("NOTHING_TO_INLINE", "OVERRIDE_BY_INLINE")
@JvmInline
value class LocalCoordArray @PublishedApi @UncheckedParams internal constructor(val backingArray: LongArray) : List<LocalCoords> {

    @OptIn(UncheckedParams::class)
    constructor(size: Int) : this(LongArray(size))

    override inline val size: Int
        get() = backingArray.size

    override inline fun contains(element: LocalCoords): Boolean = backingArray.contains(element.asLong)

    override inline fun containsAll(elements: Collection<LocalCoords>): Boolean = elements.all { contains(it) }

    @OptIn(UncheckedParams::class)
    override inline fun get(index: Int): LocalCoords = LocalCoords(backingArray[index])

    override inline fun indexOf(element: LocalCoords): Int = backingArray.indexOf(element.asLong)

    override inline fun isEmpty(): Boolean = backingArray.isEmpty()

    override inline fun iterator(): Iterator<LocalCoords> = listIterator(0, size)

    override inline fun lastIndexOf(element: LocalCoords): Int = backingArray.lastIndexOf(element.asLong)

    override inline fun listIterator(): ListIterator<LocalCoords> = listIterator(0, size)

    override inline fun listIterator(index: Int): ListIterator<LocalCoords> = listIterator(index, size)
    fun sort() = backingArray.sort()

    @OptIn(UncheckedParams::class)
    fun listIterator(fromIndex: Int, toIndex: Int): ListIterator<LocalCoords> = object : ListIterator<LocalCoords> {
        private var index = fromIndex
        override fun hasNext(): Boolean = index < toIndex
        override fun hasPrevious(): Boolean = index > fromIndex
        override fun next(): LocalCoords = try {
            LocalCoords(backingArray[index++])
        } catch (e: IndexOutOfBoundsException) {
            throw NoSuchElementException(e)
        }

        override fun nextIndex(): Int = index
        override fun previous(): LocalCoords = try {
            LocalCoords(backingArray[--index])
        } catch (e: Exception) {
            throw NoSuchElementException(e)
        }

        override fun previousIndex(): Int = index - 1
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<LocalCoords> = TODO()
    operator fun set(i: Int, value: LocalCoords) = backingArray.set(i, value.asLong)

    fun reverse() = backingArray.reverse()

    @OptIn(UncheckedParams::class)
    fun reversedCopy() = LocalCoordArray(backingArray.copyOf().also { it.reverse() })

    @OptIn(UncheckedParams::class)
    fun subArray(fromIndex: Int, toIndex: Int) = LocalCoordArray(backingArray.copyOfRange(fromIndex, toIndex))

    @OptIn(UncheckedParams::class)
    fun copy(newSize: Int = size) = LocalCoordArray(backingArray.copyOf(newSize))

    @OptIn(UncheckedParams::class)
    fun snap(resolutionBits: Int): LocalCoordArray {
        val resolution = 1 shl resolutionBits
        val snappedCoords = LongArrayList(size)
        var coord = get(0)
        var x0 = coord.x
        var y0 = coord.y
        var minX = x0
        var minY = y0
        var maxX = x0
        var maxY = y0
        snappedCoords.add(LocalCoords(x0 and -resolution, y0 shr -resolution).asLong)

        for (i in 1 until size) {
            coord = get(i)
            val x1 = coord.x and -resolution
            val y1 = coord.y and -resolution
            val dx = x1 - x0
            val dy = y1 - y0
            if (dx > 0 || dy > 0) {
                snappedCoords.add(LocalCoords(x1, y1).asLong)
                if (x1 < minX) minX = x1
                if (y1 < minY) minY = y1
                if (x1 > maxX) maxX = x1
                if (y1 > maxY) maxY = y1
                x0 = x1
                y0 = y1
            }
        }
        return LocalCoordArray(snappedCoords.toLongArray())
    }

    companion object {
        @PublishedApi
        internal val EMPTY_ARRAY = longArrayOf()

        @OptIn(UncheckedParams::class)
        inline fun empty() = LocalCoordArray(EMPTY_ARRAY)

        @OptIn(UncheckedParams::class)
        fun fromLongArray(array: LongArray): LocalCoordArray {
            array.forEach(::requireInRange)
            return LocalCoordArray(array)
        }

        private fun requireInRange(n: Long) {
            val x = n.ushr(Int.SIZE_BITS)
            val y = n and LocalCoords.INT_MASK
            require(x in LocalCoords.RANGE) {
                "$x not in ${LocalCoords.EXTENT}"
                require(y in LocalCoords.RANGE) {
                    "$y not in ${LocalCoords.EXTENT}"
                }
            }
        }
    }
}
