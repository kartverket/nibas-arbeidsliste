@file:Suppress("NOTHING_TO_INLINE")

package no.kartverket.nibas.sandbox

import arrow.core.nonFatalOrThrow
import com.google.flatbuffers.BaseVector
import com.google.flatbuffers.Table
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

sealed class ConvertedFile<VS : BaseVector, K, V>(
    dir: File,
    filename: String
) : AutoCloseable, Iterable<V> {
    companion object {
        const val FILENAME_EXT = "bin"
    }

    @JvmInline
    value class Index(val asLong: Long) {
        constructor(page: Int, offset: Int) : this((page.also { check(it >= 0) }.toLong() shl 32) or offset.also { check(it >= 0) }.toLong())
        val page: Int inline get() = (asLong ushr 32).toInt()
        val offset: Int inline get() = (asLong and 0xFFFFFFFF).toInt()

        @JvmInline
        value class Array @PublishedApi internal constructor (@PublishedApi internal val backingArray: LongArray) : List<Index> {
            override val size: Int
                get() = backingArray.size

            operator fun plus(element: Index) = Array(backingArray + element.asLong)

            override fun isEmpty(): Boolean = backingArray.isEmpty()

            override fun iterator(): Iterator<Index> = object : Iterator<Index> {
                private var index = 0
                override fun hasNext(): Boolean = index < backingArray.size
                override fun next(): Index = Index(backingArray[index++])
            }

            override fun containsAll(elements: Collection<Index>): Boolean = elements.all { backingArray.contains(it.asLong) }

            override fun contains(element: Index): Boolean = backingArray.contains(element.asLong)

            override fun get(index: Int): Index = Index(backingArray[index])

            override fun indexOf(element: Index): Int = backingArray.indexOf(element.asLong)

            override fun lastIndexOf(element: Index): Int = backingArray.lastIndexOf(element.asLong)

            override fun listIterator(): ListIterator<Index> = listIterator(0)

            override fun listIterator(index: Int): ListIterator<Index> = listIterator(index, size)

            fun listIterator(index: Int, toIndex: Int): ListIterator<Index> = object : ListIterator<Index> {
                private var cursor = index
                private val end = toIndex

                override fun hasNext(): Boolean = cursor < end

                override fun next(): Index {
                    if (!hasNext()) throw NoSuchElementException()
                    return Index(backingArray[cursor++])
                }

                override fun hasPrevious(): Boolean = cursor > 0

                override fun previous(): Index {
                    if (!hasPrevious()) throw NoSuchElementException()
                    return Index(backingArray[--cursor])
                }

                override fun nextIndex(): Int = cursor

                override fun previousIndex(): Int = cursor - 1
            }

            override fun subList(fromIndex: Int, toIndex: Int): List<Index> {
                val outerFromIndex = fromIndex
                val outerToIndex = toIndex
                require(outerFromIndex >= 0) { "fromIndex: $fromIndex" }
                require(outerToIndex <= size) { "toIndex: $outerToIndex" }
                return object : List<Index> {
                    override val size: Int
                        get() = toIndex - fromIndex

                    override fun get(index: Int): Index = Index(backingArray[index + outerFromIndex])


                    override fun isEmpty(): Boolean = outerToIndex == outerFromIndex

                    override fun iterator(): Iterator<Index> = listIterator(outerFromIndex)

                    override fun listIterator(): ListIterator<Index> = listIterator(0)

                    override fun listIterator(index: Int): ListIterator<Index> = this@Array.listIterator(fromIndex + index, fromIndex + size)

                    override fun subList(fromIndex: Int, toIndex: Int): List<Index> {
                        return this@Array.subList(outerFromIndex + fromIndex, outerFromIndex + toIndex)
                    }

                    override fun lastIndexOf(element: Index): Int {
                        for (i in outerToIndex - 1 downTo outerFromIndex) {
                            if (backingArray[i] == element.asLong) {
                                return i - outerFromIndex
                            }
                        }
                        return -1
                    }

                    override fun indexOf(element: Index): Int {
                        for (i in outerFromIndex until outerToIndex) {
                            if (backingArray[i] == element.asLong) {
                                return i - outerFromIndex
                            }
                        }
                        return -1
                    }

                    override fun containsAll(elements: Collection<Index>): Boolean {
                        return elements.all { indexOf(it) >= 0 }
                    }

                    override fun contains(element: Index): Boolean {
                        return indexOf(element) >= 0
                    }

                }
            }
        }
    }

    sealed class Page<VV : BaseVector> {
        abstract val data: VV

        class LongKey<VV : BaseVector>(override val data: VV, val firstKey: Long, val lastKey: Long) : Page<VV>(),
            Comparable<LongKey<VV>> {
            override fun compareTo(other: LongKey<VV>): Int {
                var cmp = firstKey.compareTo(other.firstKey)
                if (cmp == 0) {
                    cmp = lastKey.compareTo(other.lastKey)
                }
                return cmp
            }
        }
    }

    abstract class LongKey<VS : BaseVector, V : Table>(
        dir: File,
        filename: String,
        f: (ByteBuffer) -> Page.LongKey<VS>
    ) : ConvertedFile<VS, Long, V>(dir, filename) {
        private val pages: Array<Page.LongKey<VS>> = mapByteBuffers(f).toList().sorted().toTypedArray()
        val size = pages.sumOf { it.data.length() }

        fun indices(): Sequence<Index> = sequence {
            for (i in pages.indices) {
                val page = pages[i]
                val data = page.data
                for (j in 0 until data.length()) {
                    yield(Index(i, j))
                }
            }
        }

        fun indicesArray() : Index.Array {
            val result = LongArray(size)
            var arrIndex = 0
            for (i in pages.indices) {
                val page = pages[i]
                val data = page.data
                for (j in 0 until data.length()) {
                    result[arrIndex++] = Index(i, j).asLong
                }
            }
            return Index.Array(result)
        }

        operator fun get(index: Index): V {
            return pages[index.page].data[index.offset]
        }

        protected abstract val V.key: Long
        protected abstract operator fun VS.get(index: Int): V

        protected abstract fun VS.get(target: V, index: Int): V

        final override fun iterator(): Iterator<V> = iterator {
            for (page in pages) {
                val data = page.data
                for (i in 0 until data.length()) {
                    yield(data[i])
                }
            }
        }

        final override fun findIndex(key: Long): Index? {
            val pageIndex = findPageIndex(key)
            return if (pageIndex >= 0) {
                val offset = pages[pageIndex].data.findIndexOfKey(key)
                if (offset >= 0) {
                    Index(pageIndex, offset)
                } else {
                    null
                }
            } else {
                null
            }
        }

        final override fun find(key: Long): V? {
            val vs = findValueVector(key) ?: return null
            val index = vs.findIndexOfKey(key)
            return if (index >= 0) {
                vs[index]
            } else {
                null
            }
        }

        override fun find(target: V, key: Long): V? {
            val vs = findValueVector(key) ?: return null
            val index = vs.findIndexOfKey(key)
            return if (index >= 0) {
                vs.get(target, index)
            } else {
                null
            }
        }

        private fun findValueVector(key: Long): VS? {
            var low = 0
            var high: Int = pages.size

            while (low < high) {
                val mid = (low + high) ushr 1
                val entry = pages[mid]

                if (key >= entry.firstKey) {
                    if (key <= entry.lastKey) {
                        return entry.data
                    } else {
                        low = mid + 1
                    }
                } else {
                    high = mid
                }
            }

            return null
        }

        private fun findPageIndex(key: Long): Int {
            var low = 0
            var high: Int = pages.size

            while (low < high) {
                val mid = (low + high) ushr 1
                val entry = pages[mid]

                if (key >= entry.firstKey) {
                    if (key <= entry.lastKey) {
                        return mid
                    } else {
                        low = mid + 1
                    }
                } else {
                    high = mid
                }
            }

            return -1
        }


        private fun VS.findIndexOfKey(key: Long): Int {
            var low = 0
            var high = length()
            while (low < high) {
                val midIndex = (low + high) ushr 1
                val midEntry = get(midIndex) // GC ser ut til å være raskere her
                val midKey = midEntry.key
                if (midKey >= key) {
                    if (midKey == key) {
                        return midIndex
                    }
                    high = midIndex
                } else {
                    low = midIndex + 1
                }
            }
            return -1
        }


    }

    private val files: ArrayList<RandomAccessFile> = ArrayList(0)
    private val fileBufs: ArrayList<ByteBuffer> = ArrayList(0)

    init {
        val filenamePrefix = "$filename."
        val files = ArrayList<RandomAccessFile>()
        try {
            dir.listFiles { file -> file.name.startsWith(filenamePrefix) && file.name.endsWith(".$FILENAME_EXT") }!!
                .mapNotNull { file ->
                    file.name.substring(filenamePrefix.length, file.name.length - (FILENAME_EXT.length + 1)).toIntOrNull()
                        ?.let { it to file }
                }
                .sortedBy { (index, _) -> index }
                .also { files.ensureCapacity(it.size); check(it.isNotEmpty()) { "No files" } }
                .forEach { (_, file) ->
                    files.add(RandomAccessFile(file, "r"))
                }
        } catch (e: Throwable) {
            closeFiles(e.nonFatalOrThrow())
            throw e
        }

        fileBufs.ensureCapacity(files.size)
        for (file in files) {
            fileBufs.add(
                file.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length()).load().order(ByteOrder.LITTLE_ENDIAN)
            )
        }
    }

    inline fun getById(key: K): V =
        find(key) ?: throw NoSuchElementException("Key not found: $key")
    abstract fun find(key: K): V?
    abstract fun find(target: V, key: K): V?

    abstract fun findIndex(key: K): Index?

    final override fun close() {
        closeFiles()
    }

    protected fun <R> mapByteBuffers(transform: (ByteBuffer) -> R) = fileBufs.asSequence().map(transform)

    private fun closeFiles(e: Throwable? = null) {
        for (file in files) {
            runCatching { file.close() }.onFailure { e?.addSuppressed(it) }
        }
        runCatching { files.clear() }.onFailure { e?.addSuppressed(it) }
        runCatching { fileBufs.clear() }.onFailure { e?.addSuppressed(it) }
    }
}

