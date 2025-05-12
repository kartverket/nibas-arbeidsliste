package no.kartverket.nibas.arblist.matrikkel.download.convert

import com.google.flatbuffers.FlatBufferBuilder
import it.unimi.dsi.fastutil.ints.IntArrayList
import no.kartverket.nibas.arblist.matrikkel.download.data.LocalCoord
import no.kartverket.nibas.flatbuffer.MatrikkelDB.*
import no.kartverket.nibas.flatbuffer.MatrikkelDB.Kode.KoordinatsystemKode
import no.kartverket.nibas.flatbuffer.Nibas.Common.LocalDate
import no.kartverket.nibas.flatbuffer.Nibas.Common.Timestamp
import no.kartverket.nibas.flatbuffer.Nibas.Koordinat
import no.kartverket.nibas.flatbuffer.Nibas.MatrikkelGrense
import no.kartverket.nibas.flatbuffer.Nibas.MatrikkelGrenseDB
import no.kartverket.nibas.flatbuffer.Nibas.MatrikkelGrenseEntry
import no.kartverket.nibas.flatbuffer.Nibas.MatrikkelGrensepunkt
import no.kartverket.nibas.flatbuffer.Nibas.MatrikkelGrensepunktDB
import no.kartverket.nibas.flatbuffer.Nibas.MatrikkelGrensepunktEntry
//import org.eclipse.collections.api.factory.primitive.IntStacks
//import org.eclipse.collections.api.factory.primitive.LongIntMaps
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.time.Instant
import no.kartverket.nibas.flatbuffer.MatrikkelDB.Koordinat as MatDBKoordinat

private const val DEFAULT_TARGET_BUFFER_SIZE = 256 * 1024 * 1024

fun convertGrensepunkt(
    grensepunktFiles: Iterable<File>,
    targetBufferSize: Int = DEFAULT_TARGET_BUFFER_SIZE
) {
    val fbi = FlatBufferBuilder(targetBufferSize + targetBufferSize / 128)
    val offsets = IntArrayList()
    val grensepunkt = Grensepunkt()
    val koordinat = MatDBKoordinat()
    val posisjonskvalitet = Posisjonskvalitet()
    val localDate = LocalDate()
    val timestamp = Timestamp()
    var fileIndex = 0
    var minId: Long = Long.MAX_VALUE
    readFiles(grensepunktFiles, GrensepunktBuffer::getRootAsGrensepunktBuffer) { original, lastFile ->
        val scale = original.koordinatScale().toDouble()
        val lastIndex = original.grensepunkterLength() - 1
        if (lastIndex >= 0) {
            minId = minOf(minId, original.grensepunkter(lastIndex).id())
        }
        for (i in lastIndex downTo 0) {
            original.grensepunkter(grensepunkt, i)
            grensepunkt.koordinat(koordinat)
            val id = grensepunkt.id()
            val localCoord = when (val kodeValue = grensepunkt.koordinatsystemKode()) {
                KoordinatsystemKode.EUREF_SONE_32 -> LocalCoord.fromUtm32(koordinat.x() / scale, koordinat.y() / scale)
                KoordinatsystemKode.EUREF_SONE_33 -> LocalCoord.fromUtm33(koordinat.x() / scale, koordinat.y() / scale)
                KoordinatsystemKode.EUREF_SONE_35 -> LocalCoord.fromUtm35(koordinat.x() / scale, koordinat.y() / scale)
                else -> error("Unknown coordinate system: $kodeValue")
            }
            val grensepunktnr = grensepunkt.grensepunktnr()?.let {
                fbi.createString(it)
            }
            MatrikkelGrensepunkt.startMatrikkelGrensepunkt(fbi)
            MatrikkelGrensepunkt.addCoordinate(fbi, localCoord.let { (x, y) ->
                Koordinat.createKoordinat(fbi, x, y)
            })
            grensepunkt.posisjonskvalitet(posisjonskvalitet)?.apply {
                MatrikkelGrensepunkt.addMaalemetodeKode(fbi, maalemetodeKode())
                MatrikkelGrensepunkt.addMaalingsnoyaktighet(fbi, noyaktighet())
            }
            grensepunkt.datafangstdato(localDate)?.apply {
                MatrikkelGrensepunkt.addDatafangstdato(fbi, LocalDate.createLocalDate(fbi, year(), month(), day()))
            }
            MatrikkelGrensepunkt.addGrensmerkodeKode(fbi, grensepunkt.grensmerkodeKode())
            grensepunktnr?.let { MatrikkelGrensepunkt.addGrensepunktnr(fbi, it) }
            grensepunkt.oppdateringsdato(timestamp).apply {
                MatrikkelGrensepunkt.addOppdateringsdato(fbi, Timestamp.createTimestamp(fbi, seconds(), nanos()))
            }
            val offset = MatrikkelGrensepunkt.endMatrikkelGrensepunkt(fbi)
                .let { matrikkelGrensepunktOffset ->
                    MatrikkelGrensepunktEntry.startMatrikkelGrensepunktEntry(fbi)
                    MatrikkelGrensepunktEntry.addId(fbi, id)
                    MatrikkelGrensepunktEntry.addGrensepunkt(fbi, matrikkelGrensepunktOffset)
                    MatrikkelGrensepunktEntry.endMatrikkelGrensepunktEntry(fbi)
                }
                .also { offsets.add(it) }

            if (i == 0 && lastFile || offset + offsets.size * Int.SIZE_BYTES >= targetBufferSize) {
                MatrikkelGrensepunktDB.startMatrikkelgrensepunkterVector(fbi, offsets.size)
                offsets.asReversed().forEach { fbi.addOffset(it) }
                val vectorOffset = fbi.endVector()
                MatrikkelGrensepunktDB.startMatrikkelGrensepunktDB(fbi)
                MatrikkelGrensepunktDB.addMatrikkelgrensepunkter(fbi, vectorOffset)
                MatrikkelGrensepunktDB.addFirstKey(fbi, minId)
                MatrikkelGrensepunktDB.addLastKey(fbi, id)
                fbi.finish(MatrikkelGrensepunktDB.endMatrikkelGrensepunktDB(fbi), "GPDB")
                println("Writing file $fileIndex, maxId: $minId, minId: $id")
                val dataBuffer = fbi.dataBuffer()
                val outFilePath = String.format("matrikkel_grensepunkt.%03d.%s", fileIndex++, ConvertedFile.Companion.FILENAME_EXT)
                RandomAccessFile(outFilePath, "rw").use { outFile ->
                    outFile.channel.write(dataBuffer)
                }
                fbi.clear()
                offsets.clear()
                if (i != 0) {
                    minId = original.grensepunkter(grensepunkt, i - 1).id()
                }
            }
        }
    }
}

internal fun convertGrenselinje(
    filename: String,
    grenselinjeFiles: Iterable<File>,
    grensepunkt: ConvertedFile.LongKey<MatrikkelGrensepunktEntry.Vector, MatrikkelGrensepunktEntry>,
    targetBufferSize: Int = DEFAULT_TARGET_BUFFER_SIZE
) {
    check(targetBufferSize > 0) { "Target buffer size must be positive" }
    check(filename.isNotBlank()) { "Filename must not be blank" }
    val fb = FlatBufferBuilder(targetBufferSize + targetBufferSize / 128)
    val offsets = IntArrayList()
    var fileIndex = 0
    var minId = Long.MAX_VALUE
    readFiles(grenselinjeFiles, GrenselinjeBuffer::getRootAsGrenselinjeBuffer) { glBuf, lastFile ->
        val scale = glBuf.koordinatScale().toDouble()
        val lastIndex = glBuf.grenselinjerLength() - 1
        if (lastIndex >= 0) {
            minId = minOf(minId, glBuf.grenselinjer(lastIndex).id())
        }
        for (i in lastIndex downTo 0) {
            val gl = glBuf.grenselinjer(i)
            val coordsOffset = when (val geomType = gl.lineGeometryType()) {
                LineGeometry.Polyline -> fb.createCoordinatesVector(Polyline().also(gl::lineGeometry), grensepunkt, scale)
                LineGeometry.Arc -> fb.createCoordinatesVector(Arc().also(gl::lineGeometry), grensepunkt, scale)
                else -> error("Unknown geometry type: $geomType")
            }

            val offset = fb.createMatrikkelGrenseEntry(
                id = gl.id(),
                coordsOffset = coordsOffset,
                hjelpelinjeKode = gl.hjelpelinjeKode(),
                omtvistet = gl.omtvistet(),
                terrengdetaljKode = gl.terrengdetaljKode(),
                administrativGrenseKode = gl.administrativGrenseKode(),
                maalemetodeKode = gl.posisjonskvalitet()?.maalemetodeKode(),
                maalingsnoyaktighet = gl.posisjonskvalitet()?.noyaktighet() ?: 0,
                datafangstDato = gl.datafangstdato()?.toJavaLocalDate(),
                noyaktighetsklasse = gl.noyaktighetsklasse(),
                oppdateringsdato = gl.oppdateringsdato().toJavaInstant(),
                kommunenrstrengcacheOffset = gl.kommunenrstrengcache()?.let { fb.createString(it) },
            ).also(offsets::add)

            if (i == 0 && lastFile || offset + offsets.size * Int.SIZE_BYTES >= targetBufferSize) {
                MatrikkelGrenseDB.startMatrikkelgrenserVector(fb, offsets.size)
                offsets.asReversed().forEach { fb.addOffset(it) }
                val vectorOffset = fb.endVector()
                MatrikkelGrenseDB.startMatrikkelGrenseDB(fb)
                MatrikkelGrenseDB.addMatrikkelgrenser(fb, vectorOffset)
                MatrikkelGrenseDB.addFirstKey(fb, minId)
                MatrikkelGrenseDB.addLastKey(fb, gl.id())
                fb.finish(MatrikkelGrenseDB.endMatrikkelGrenseDB(fb), "GLDB")
                println("Writing file $fileIndex, maxId: $minId, minId: ${gl.id()}")
                val dataBuffer = fb.dataBuffer()
                val outFilePath = String.format("%s.%03d.%s", filename, fileIndex++, ConvertedFile.Companion.FILENAME_EXT)
                RandomAccessFile(outFilePath, "rw").use { outFile ->
                    outFile.channel.write(dataBuffer)
                }
                fb.clear()
                offsets.clear()
                if (i != 0) {
                    minId = glBuf.grenselinjer(i - 1).id()
                }
            }
        }
    }
}

inline fun LocalDate.toJavaLocalDate(): java.time.LocalDate = java.time.LocalDate.of(year(), month().toInt(), day().toInt())

inline fun Timestamp.toJavaInstant(): Instant = Instant.ofEpochSecond(seconds(), nanos().toLong())

private fun FlatBufferBuilder.createMatrikkelGrenseEntry(
    id: Long,
    coordsOffset: Int,
    hjelpelinjeKode: Byte,
    omtvistet: Boolean,
    terrengdetaljKode: Byte,
    administrativGrenseKode: Byte,
    maalemetodeKode: Byte?,
    datafangstDato: java.time.LocalDate?,
    maalingsnoyaktighet: Int,
    noyaktighetsklasse: Byte,
    oppdateringsdato: Instant,
    kommunenrstrengcacheOffset: Int?
): Int {
    MatrikkelGrense.startMatrikkelGrense(this)
    MatrikkelGrense.addCoordinates(this, coordsOffset)
    MatrikkelGrense.addHjelpelinjeKode(this, hjelpelinjeKode)
    MatrikkelGrense.addOmtvistet(this, omtvistet)
    MatrikkelGrense.addTerrengdetaljKode(this, terrengdetaljKode)
    MatrikkelGrense.addAdministrativGrenseKode(this, administrativGrenseKode)
    maalemetodeKode?.let { MatrikkelGrense.addMaalemetodeKode(this, it) }
    MatrikkelGrense.addMaalingsnoyaktighet(this, maalingsnoyaktighet)
    datafangstDato?.run { MatrikkelGrense.addDatafangstdato(this@createMatrikkelGrenseEntry, LocalDate.createLocalDate(this@createMatrikkelGrenseEntry, year, monthValue.toShort(), dayOfMonth.toShort())) }
    MatrikkelGrense.addNoyaktighetsklasse(this, noyaktighetsklasse)
    oppdateringsdato.run { MatrikkelGrense.addOppdateringsdato(this@createMatrikkelGrenseEntry, Timestamp.createTimestamp(this@createMatrikkelGrenseEntry, this.epochSecond, this.nano)) }
    kommunenrstrengcacheOffset?.let { MatrikkelGrense.addKommunenrstrengcache(this, it) }
    val offset = MatrikkelGrense.endMatrikkelGrense(this)
        .let { dataOffset ->
            MatrikkelGrenseEntry.startMatrikkelGrenseEntry(this)
            MatrikkelGrenseEntry.addId(this, id)
            MatrikkelGrenseEntry.addGrense(this, dataOffset)
            MatrikkelGrenseEntry.endMatrikkelGrenseEntry(this)
        }
    return offset
}

private fun FlatBufferBuilder.createCoordinatesVector(
    arc: Arc,
    grensepunkt: ConvertedFile.LongKey<MatrikkelGrensepunktEntry.Vector, MatrikkelGrensepunktEntry>,
    scale: Double
): Int {
    MatrikkelGrense.startCoordinatesVector(this, 3)
    grensepunkt.getById(arc.lastPointId()).grensepunkt().coordinate().run {
        Koordinat.createKoordinat(this@createCoordinatesVector, x(), y())
    }
    arc.middlePointCoordinate().run {

        when (arc.koordinatsystemKode()) {
            KoordinatsystemKode.EUREF_SONE_32 -> {
                LocalCoord.fromUtm32(x() / scale, y() / scale).run {
                    Koordinat.createKoordinat(this@createCoordinatesVector, x, y)
                }
            }

            KoordinatsystemKode.EUREF_SONE_33 -> {
                LocalCoord.fromUtm33(x() / scale, y() / scale).run {
                    Koordinat.createKoordinat(this@createCoordinatesVector, x, y)
                }
            }

            KoordinatsystemKode.EUREF_SONE_35 -> {
                LocalCoord.fromUtm35(x() / scale, y() / scale).run {
                    Koordinat.createKoordinat(this@createCoordinatesVector, x, y)
                }
            }

            else -> error("Unknown coordinate system: ${arc.koordinatsystemKode()}")
        }
    }
    grensepunkt.getById(arc.firstPointId()).grensepunkt().coordinate().run {
        Koordinat.createKoordinat(this@createCoordinatesVector, x(), y())
    }
    return endVector()
}

private fun FlatBufferBuilder.createCoordinatesVector(
    polyline: Polyline,
    grensepunkt: ConvertedFile.LongKey<MatrikkelGrensepunktEntry.Vector, MatrikkelGrensepunktEntry>,
    scale: Double
): Int {
    val middlePointCoordinatesMaxIndex = polyline.middlePointCoordinatesLength() - 1
    MatrikkelGrense.startCoordinatesVector(this, polyline.middlePointCoordinatesLength() + 2)
    grensepunkt.getById(polyline.lastPointId()).grensepunkt().coordinate().run {
        Koordinat.createKoordinat(this@createCoordinatesVector, x(), y())
    }
    when (polyline.koordinatsystemKode()) {
        KoordinatsystemKode.EUREF_SONE_32 -> {
            for (j in middlePointCoordinatesMaxIndex downTo 0) {
                val koordinat = polyline.middlePointCoordinates(j)
                LocalCoord.fromUtm32(koordinat.x() / scale, koordinat.y() / scale).run {
                    Koordinat.createKoordinat(this@createCoordinatesVector, x, y)
                }
            }
        }

        KoordinatsystemKode.EUREF_SONE_33 -> {
            for (j in middlePointCoordinatesMaxIndex downTo 0) {
                val koordinat = polyline.middlePointCoordinates(j)
                LocalCoord.fromUtm33(koordinat.x() / scale, koordinat.y() / scale).run {
                    Koordinat.createKoordinat(this@createCoordinatesVector, x, y)
                }
            }
        }

        KoordinatsystemKode.EUREF_SONE_35 -> {
            for (j in middlePointCoordinatesMaxIndex downTo 0) {
                val koordinat = polyline.middlePointCoordinates(j)
                LocalCoord.fromUtm35(koordinat.x() / scale, koordinat.y() / scale).run {
                    Koordinat.createKoordinat(this@createCoordinatesVector, x, y)
                }
            }
        }

        else -> error("Unknown coordinate system: ${polyline.koordinatsystemKode()}")
    }
    grensepunkt.getById(polyline.firstPointId()).grensepunkt().coordinate().run {
        Koordinat.createKoordinat(this@createCoordinatesVector, x(), y())
    }


    return endVector()
}

//private fun convertTeiger(teigFiles: Iterable<File>, grenselinjeSstFilePath: String) {
//    LongIntMaps.mutable.empty()
//    val grenselinjeKeyBuf = ByteBuffer.allocate(Long.SIZE_BYTES)
//    val grenselinjeKeyCompareBuf = ByteBuffer.allocate(Long.SIZE_BYTES)
//    val matrikkelGrenseBuffer = ByteBuffer.allocate(262_144).order(ByteOrder.LITTLE_ENDIAN)
//
//    ColumnFamilyDescriptor("".toByteArray(), ColumnFamilyOptions())
//    val mutableListOf = mutableListOf<ColumnFamilyHandle>()
//    RocksDB.open("tmpdb").use { db ->
//        db.ingestExternalFile(listOf("matrikkel_grenselinjer.sst"), IngestExternalFileOptions())
//        println("Ingested file")
//        readFiles(teigFiles, TeigBuffer::getRootAsTeigBuffer) { teigBuffer, _ ->
//            val teig = Teig()
//            val matrikkelgrense = MatrikkelGrense()
//            for (i in (0 until teigBuffer.teigerLength()).reversed()) {
//                teigBuffer.teiger(teig, i)
//                for (j in 0 until teig.grenselinjeIdsLength()) {
//                    val glKey = grenselinjeKeyBuf.clear().putLong(teig.grenselinjeIds(j))
//                    db.get(glKey.array(), matrikkelGrenseBuffer.array()).also { dataLen ->
//                        check(dataLen != RocksDB.NOT_FOUND) { "Invalid grenselinje id: ${teig.grenselinjeIds(j)}" }
//                        check(dataLen <= matrikkelGrenseBuffer.capacity()) { "Value too large" }
//                        matrikkelGrenseBuffer.limit(dataLen).rewind()
//                    }
//                    MatrikkelGrense.getRootAsMatrikkelGrense(matrikkelGrenseBuffer, matrikkelgrense).run {
//
//                    }
//                }
//            }
//        }
//    }
////    SstFileReader(Options()).use { sstReader ->
////        sstReader.open(grenselinjeSstFilePath)
////        sstReader.newIterator(ReadOptions()).use { sst ->
////            readFiles(teigFiles, TeigBuffer::getRootAsTeigBuffer) { teigBuffer ->
////                val teig = Teig()
////                val matrikkelgrense = MatrikkelGrense()
////                for (i in (0 until teigBuffer.teigerLength()).reversed()) {
////                    teigBuffer.teiger(teig, i)
////                    for (j in 0 until teig.grenselinjeIdsLength()) {
////                        sst.seek(grenselinjeKeyBuf.clear().putLong(teig.grenselinjeIds(j)).flip())
////                        check(sst.isValid()) { "Invalid grenselinje id: ${teig.grenselinjeIds(j)}" }
////                        sst.key(grenselinjeKeyCompareBuf.clear()).also {
////                            check(it <= grenselinjeKeyCompareBuf.capacity()) { "Key too large" }
////                        }
////                        check(grenselinjeKeyBuf.rewind() == grenselinjeKeyCompareBuf.rewind()) { "Key mismatch" }
////                        sst.value(matrikkelGrenseBuffer.clear()).also {
////                            check(it <= matrikkelGrenseBuffer.capacity()) { "Value too large" }
////                            matrikkelGrenseBuffer.limit(it)
////                        }
////                        MatrikkelGrense.getRootAsMatrikkelGrense(matrikkelGrenseBuffer.rewind(), MatrikkelGrense()).run {
////
////                        }
////                    }
////                }
////            }
////        }
////    }
//}

private fun <T, R> readFile(file: File, mapFn: (ByteBuffer) -> T, fn: (T) -> R): R =
    RandomAccessFile(file, "r").use { raf ->
        raf.channel.use { ch ->
            fn(mapFn(ch.map(FileChannel.MapMode.READ_ONLY, 0L, ch.size())))
        }
    }

fun <T, R> readFiles(
    files: Iterable<File>,
    mapFn: (ByteBuffer) -> T,
    fn: (T, lastFile: Boolean) -> R
): List<R> = files.toList().let {
    it.mapIndexed { i, file ->
        readFile(file, mapFn) { buf ->
            fn(buf, i == it.size - 1)
        }

    }
}
