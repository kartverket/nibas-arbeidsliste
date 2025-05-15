package no.kartverket.nibas.arblist.matrikkel.download

import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.resourceScope
import com.google.flatbuffers.FlatBufferBuilder
import com.google.flatbuffers.FlatBufferBuilder.HeapByteBufferFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import no.kartverket.nibas.flatbuffer.MatrikkelDB.*
import no.kartverket.nibas.flatbuffer.MatrikkelDB.Kode.GrensemerkeKode
import no.kartverket.nibas.flatbuffer.MatrikkelDB.Kode.GrensemerkeNedsattIKode
import no.kartverket.nibas.flatbuffer.MatrikkelDB.Kode.MaalemetodeKode
import no.kartverket.nibas.flatbuffer.MatrikkelDB.Kode.NoyaktighetsklasseKode
import no.kartverket.nibas.flatbuffer.Nibas.Common.Matrikkelnummer
import no.kartverket.nibas.flatbuffer.Nibas.Common.Timestamp
import oracle.jdbc.OracleConnection
import java.io.File
import java.io.RandomAccessFile
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Struct
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.log10
import no.kartverket.nibas.flatbuffer.Nibas.Common.LocalDate as FBLocalDate

data class ProgressEvent(val current: Int, val total: Int) {
    val percent: Double by lazy { (current.toDouble() / total.toDouble() * 100.0) }
}


private const val DEFAULT_FETCH_SIZE = 32768
const val COORDINATE_SCALE = 100
private const val ORACLE_PRECISION = 38
private val MC = MathContext(ORACLE_PRECISION + ceil(log10(COORDINATE_SCALE.toDouble())).toInt(), RoundingMode.HALF_UP)
private val COORDINATE_SCALE_BD = BigDecimal(COORDINATE_SCALE, MC)

private val GRENSEPUNKT_SQL = """
    SELECT 
      gp.id,                       --  1
      gp.positionx, gp.positiony,  --  2-3
      gp.koordinatsystemkodeid,    --  4
      gp.grensemerkenedsattiid,    --  5
      gp.grensepunkttypeid,        --  6
      gp.malemetodeid,             --  7
      gp.noyaktighet,              --  8
      gp.datafangstdato,           --  9
      gp.grensepunktnr,            -- 10
      gp.oppdateringsdato          -- 11
    FROM grensepunkt gp
    ORDER BY gp.id
""".trimIndent()

private val GRENSELINJE_SQL = """
    SELECT gl.id,                          -- 01
           gl.hjelpelinjetypeid,           -- 02
           gl.omtvistet,                   -- 03
           gl.folgerterrengdetaljid,       -- 04
           gl.administrativgrensekodeid,   -- 05
           gl.malemetodeid,                -- 06
           gl.noyaktighet,                 -- 07
           gl.datafangstdato,              -- 08
           gl.lagretnoyaktighetsklasse,    -- 09
           gl.kurvesegmenttype,            -- 10
           gl.kurvekoordinatsystemkode,    -- 11
           gl.kurvestartpunktid,           -- 12
           gl.kurveendpunktid,             -- 13
           gl.kurvebuepunktx,              -- 14
           gl.kurvebuepunkty,              -- 15
           gl.kurvepositions,              -- 16
           gl.oppdateringsdato,            -- 17
           gl.kommunenrstrengcache,        -- 18
           gl.informasjon,                 -- 19 (VARCHAR2)
           gl.versjon,                     -- 20 (NUMBER)
           gl.versjonid,                   -- 21 (NUMBER)
           gl.oppdatertav                  -- 22 (VARCHAR2)
    FROM grenselinje gl
    ORDER BY gl.id
""".trimIndent()

val EXTERIOR_SQL = """
    SELECT t.id AS teigid, cd.grenselinjeid, cd.signed
    FROM teig t
      JOIN boundary b ON t.exteriorid = b.id
      JOIN curvedirection cd ON b.id = cd.boundaryid
    ORDER BY t.id, cd.indeks""".trimIndent()

val MATRIKKELNR_SQL = """
    SELECT tfm.teigid, k.kommunenr, m.gardsnr, m.bruksnr, m.festenr, m.seksjonsnr
    FROM teigformatrikkelenhet tfm
      JOIN matrikkelenhet m ON tfm.matrikkelenhetid = m.id
      JOIN kommune k ON m.kommuneid = k.id
    GROUP BY tfm.teigid, k.kommunenr, m.gardsnr, m.bruksnr, m.festenr, m.seksjonsnr
    ORDER BY tfm.teigid""".trimIndent()

val INTERIOR_SQL = """
    SELECT b.teigid, b.id, cd.grenselinjeid, cd.signed
    FROM boundary b JOIN curvedirection cd ON b.id = cd.boundaryid
    WHERE b.teigid IS NOT NULL
    ORDER BY b.teigid, b.id, cd.indeks""".trimIndent()

fun fetchGrenslinjer(
    matrikkeldb: Connection,
    targetBufferSize: Int,
    fetchSize: Int = DEFAULT_FETCH_SIZE
): Pair<Flow<ProgressEvent>, Flow<GrenselinjeBuffer>> {
    val progressChannel = Channel<ProgressEvent>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val totalCount = runBlocking(Dispatchers.IO) {
        resourceScope {
            val st = autoCloseable { matrikkeldb.createStatement() }
            val rs = autoCloseable { st.executeQuery("SELECT COUNT(*) * 1000 FROM grenselinje SAMPLE (0.1)") }
            if (rs.next()) rs.getInt(1) else 0
        }
    }
    val updateProgressCount = maxOf(1, totalCount / 100)

    return progressChannel.receiveAsFlow() to flow<GrenselinjeBuffer> {
        resourceScope {
            onRelease { progressChannel.close() }
            val st = matrikkeldb.prepareStatement(GRENSELINJE_SQL).apply {
                this.fetchSize = fetchSize
            }
            val rs = autoCloseable { st.executeQuery() }
            progressChannel.send(ProgressEvent(0, totalCount))
            val bufHeadroom = targetBufferSize / 128
            val fb = FlatBufferBuilder(
                HeapByteBufferFactory.INSTANCE.newByteBuffer(targetBufferSize),
                HeapByteBufferFactory.INSTANCE
            )
            val offsets = ArrayList<Int>()
            val polylineCoords = ArrayList<Long>()
            var count = 0
            if (rs.next()) while (true) {
                val (lineGeomtryType, lineGeomOffset) = when (val value = rs.getString(10)) {
                    "polyline" -> {
                        val structAttrs = rs
                            .getObject("kurvepositions", Struct::class.java)
                            .attributes
                        val array =
                            (structAttrs[1] as java.sql.Array).getArray(mapOf("NUMBER" to BigDecimal::class.java)) as Array<BigDecimal>
                        polylineCoords.clear()
                        for (i in array.indices step 3) {
                            polylineCoords.add(
                                array[i + 1].multiply(COORDINATE_SCALE_BD, MC).setScale(0, MC.roundingMode).longValueExact()
                            )
                            polylineCoords.add(
                                array[i].multiply(COORDINATE_SCALE_BD, MC).setScale(0, MC.roundingMode)
                                    .longValueExact()
                            )
                        }
                        val polylineMidCoordsOffset = if (polylineCoords.isNotEmpty()) {
                            polylineCoords.reverse()
                            Polyline.startMiddlePointCoordinatesVector(fb, polylineCoords.size / 2)
                            for (i in polylineCoords.indices step 2) {
                                Koordinat.createKoordinat(fb, polylineCoords[i], polylineCoords[i + 1])
                            }
                            fb.endVector()
                        } else {
                            null
                        }
                        Polyline.startPolyline(fb)
                        Polyline.addKoordinatsystemKode(fb, rs.getByte(11))
                        Polyline.addFirstPointId(fb, rs.getLong(12))
                        if (polylineMidCoordsOffset != null) {
                            Polyline.addMiddlePointCoordinates(fb, polylineMidCoordsOffset)
                        }
                        Polyline.addLastPointId(fb, rs.getLong(13))
                        LineGeometry.Polyline to Polyline.endPolyline(fb)
                    }

                    "arc" -> {
                        Arc.startArc(fb)
                        Arc.addKoordinatsystemKode(fb, rs.getByte(11))
                        Arc.addFirstPointId(fb, rs.getLong(12))
                        val x = rs
                            .getBigDecimal(14)
                            .multiply(COORDINATE_SCALE_BD, MC)
                            .setScale(0, MC.roundingMode)
                            .longValueExact()
                        val y = rs
                            .getBigDecimal(15)
                            .multiply(COORDINATE_SCALE_BD, MC)
                            .setScale(0, MC.roundingMode)
                            .longValueExact()
                        Arc.addMiddlePointCoordinate(fb, Koordinat.createKoordinat(fb, x, y))
                        Arc.addLastPointId(fb, rs.getLong(13))
                        LineGeometry.Arc to Arc.endArc(fb)
                    }

                    else -> error("Unknown geometry type $value")
                }

                val kommunenrstrengcacheDbValue: String? = rs.getString(18)
                val kommunenrstrengcacheOffset: Int? = kommunenrstrengcacheDbValue?.let { fb.createString(it) }
                val informasjonDbValue: String? = rs.getString(19)
                val informasjonOffset: Int? = informasjonDbValue?.let { fb.createString(it) }
                val oppdatertavDbValue: String? = rs.getString(22)
                val oppdatertavOffset: Int? = oppdatertavDbValue?.let { fb.createString(it) }

                Grenselinje.startGrenselinje(fb)
                Grenselinje.addId(fb, rs.getLong(1))
                Grenselinje.addHjelpelinjeKode(fb, rs.getByte(2))
                Grenselinje.addOmtvistet(fb, rs.getBoolean(3))
                Grenselinje.addTerrengdetaljKode(fb, rs.getByte(4))
                Grenselinje.addAdministrativGrenseKode(fb, rs.getByte(5))
                val maalemetodeKode = validateMaalemetodeKode(rs.getObject(6), rs.wasNull())

                Grenselinje.addPosisjonskvalitet(
                    fb,
                    Posisjonskvalitet.createPosisjonskvalitet(
                        fb,
                        /* maalemetodeKode = */ maalemetodeKode,
                        /* noyaktighet = */ rs.getInt(7)
                    )
                )
                rs.getDate(8)?.toLocalDate()?.run {
                    Grenselinje.addDatafangstdato(
                        fb,
                        FBLocalDate.createLocalDate(fb, year, monthValue.toShort(), dayOfMonth.toShort())
                    )
                }

                val noyaktighetsklasseValue = validateNoyaktighetsklasse(rs.getObject(9), rs.wasNull())
                Grenselinje.addNoyaktighetsklasse(fb, noyaktighetsklasseValue)
                Grenselinje.addLineGeometryType(fb, lineGeomtryType)
                Grenselinje.addLineGeometry(fb, lineGeomOffset)
                rs.getTimestamp(17).toInstant().run {
                    Grenselinje.addOppdateringsdato(fb, Timestamp.createTimestamp(fb, epochSecond, nano))
                }

                kommunenrstrengcacheOffset?.let { Grenselinje.addKommunenrstrengcache(fb, it) }

                informasjonOffset?.let { Grenselinje.addInformasjon(fb, it) }

                val versjon: Long = rs.getLong(20)
                if (!rs.wasNull()) {
                    Grenselinje.addVersjon(fb, versjon)
                }

                val versjonid: Int = rs.getInt(21)
                if (!rs.wasNull()) {
                    Grenselinje.addVersjonid(fb, versjonid)
                }

                oppdatertavOffset?.let { Grenselinje.addOppdatertav(fb, it) }

                offsets.add(Grenselinje.endGrenselinje(fb))
                count++

                if (count % updateProgressCount == 0) {
                    progressChannel.send(ProgressEvent(count, maxOf(count, totalCount)))
                }

                val hasNext = rs.next()
                if (fb.offset() + bufHeadroom >= targetBufferSize || !hasNext) {
                    val grenselinjeVectorOffset = offsets.run {
                        GrenselinjeBuffer.startGrenselinjerVector(fb, size)
                        forEach(fb::addOffset)
                        fb.endVector()

                    }
                    GrenselinjeBuffer.startGrenselinjeBuffer(fb)
                    GrenselinjeBuffer.addGrenselinjer(fb, grenselinjeVectorOffset)
                    GrenselinjeBuffer.addKoordinatScale(fb, COORDINATE_SCALE.toLong())
                    fb.finish(GrenselinjeBuffer.endGrenselinjeBuffer(fb))
                    emit(GrenselinjeBuffer.getRootAsGrenselinjeBuffer(fb.dataBuffer().slice()))
                    if (hasNext) {
                        fb.init(
                            HeapByteBufferFactory.INSTANCE.newByteBuffer(targetBufferSize),
                            HeapByteBufferFactory.INSTANCE
                        )
                        offsets.clear()
                    } else {
                        break
                    }
                }

            }
        }
    }.buffer(1)
}

fun fetchGrenspunkt(
    matrikkeldb: Connection,
    targetBufferSize: Int,
    fetchSize: Int = DEFAULT_FETCH_SIZE
): Pair<Flow<ProgressEvent>, Flow<GrensepunktBuffer>> {
    val progressChannel = Channel<ProgressEvent>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val totalCount = runBlocking(Dispatchers.IO) {
        resourceScope {
            val st = autoCloseable { matrikkeldb.createStatement() }
            val rs = autoCloseable { st.executeQuery("SELECT COUNT(*) * 1000 FROM grensepunkt SAMPLE (0.1)") }
            if (rs.next()) rs.getInt(1) else 0
        }
    }
    val updateProgressCount = maxOf(1, totalCount / 100)

    return progressChannel.receiveAsFlow() to flow {
        resourceScope {
            onRelease { progressChannel.close() }
            val st = matrikkeldb.prepareStatement(GRENSEPUNKT_SQL).apply {
                this.fetchSize = fetchSize
            }
            val rs = autoCloseable { st.executeQuery() }
            progressChannel.send(ProgressEvent(0, totalCount))
            val bufHeadroom = targetBufferSize / 128
            val fb = FlatBufferBuilder(
                HeapByteBufferFactory.INSTANCE.newByteBuffer(targetBufferSize),
                HeapByteBufferFactory.INSTANCE
            )
            val offsets = ArrayList<Int>()
            var count = 0
            if (rs.next()) while (true) {
                val grenspunktnrOffset = rs
                    .getString(10)?.let {
                        fb.createString(it)
                    }
                val offset = fb.createGrensepunkt(
                    id = rs.getLong(1),
                    x = checkNotNull(rs.getBigDecimal(2)) { "positionx should not be null" }
                        .multiply(COORDINATE_SCALE_BD, MC)
                        .setScale(0, MC.roundingMode)
                        .longValueExact(),
                    y = checkNotNull(rs.getBigDecimal(3)) { "positionx should not be null" }
                        .multiply(COORDINATE_SCALE_BD, MC)
                        .setScale(0, MC.roundingMode)
                        .longValueExact(),
                    koordinatsystemKode = rs.getByte(4).also {
                        if (rs.wasNull()) error("Koordinatsystemkode is null")
                    },
                    grensemerkeNedsattIKode = rs.getByte(5).let {
                        if (rs.wasNull()) GrensemerkeNedsattIKode.NEDSATT_I_IKKE_SPESIFISERT else it
                    },
                    grensemerkeKode = rs.getByte(6).let {
                        if (rs.wasNull()) GrensemerkeKode.UKJENT else it
                    },
                    malemetodeKode = validateMaalemetodeKode(rs.getObject(7), rs.wasNull()),
                    noyaktighet = rs.getInt(8),
                    datafangstdato = rs.getDate(9)?.toLocalDate(),
                    oppdateringsdato = rs.getTimestamp(11).toInstant(),
                    grenspunktnrOffset = grenspunktnrOffset
                )
                offsets.add(offset)
                count++

                if (count % updateProgressCount == 0) {
                    progressChannel.send(ProgressEvent(count, maxOf(count, totalCount)))
                }

                val hasNext = rs.next()
                if (fb.offset() + bufHeadroom >= targetBufferSize || !hasNext) {
                    GrensepunktBuffer.startGrensepunkterVector(fb, offsets.size)
                    offsets.forEach(fb::addOffset)
                    val grensepunktVectorOffset = fb.endVector()
                    GrensepunktBuffer.startGrensepunktBuffer(fb)
                    GrensepunktBuffer.addGrensepunkter(fb, grensepunktVectorOffset)
                    GrensepunktBuffer.addKoordinatScale(fb, COORDINATE_SCALE.toLong())
                    fb.finish(GrensepunktBuffer.endGrensepunktBuffer(fb))
                    emit(GrensepunktBuffer.getRootAsGrensepunktBuffer(fb.dataBuffer().slice()))
                    if (hasNext) {
                        fb.init(
                            HeapByteBufferFactory.INSTANCE.newByteBuffer(targetBufferSize),
                            HeapByteBufferFactory.INSTANCE
                        )
                        offsets.clear()
                    } else {
                        break
                    }
                }
            }
        }
    }.buffer(1)
}


fun fetchTeiger(
    matrikkeldb: Connection,
    targetBufferSize: Int,
    fetchSize: Int = DEFAULT_FETCH_SIZE
): Pair<Flow<ProgressEvent>, Flow<TeigBuffer>> {
    data class TempMnr(val knr: Short, val gnr: Int, val bnr: Short, val fnr: Short, val snr: Short)

    val teigCountEstimate = runBlocking(Dispatchers.IO) {
        resourceScope {
            val st = autoCloseable { matrikkeldb.createStatement() }
            val rs = autoCloseable { st.executeQuery("SELECT COUNT(*) * 1000 FROM teig SAMPLE(0.1)") }
            if (rs.next()) rs.getInt(1) else 0
        }
    }

    val progressChannel = Channel<ProgressEvent>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    return progressChannel.receiveAsFlow() to flow {
        resourceScope {
            onRelease { progressChannel.close() }
            val exteriorsStatement = autoCloseable { matrikkeldb.prepareStatement(EXTERIOR_SQL) }.apply {
                this.fetchSize = fetchSize
            }
            val matrikkelnummerStatement = autoCloseable { matrikkeldb.prepareStatement(MATRIKKELNR_SQL) }.apply {
                this.fetchSize = fetchSize
            }
            val interiorsStatement = autoCloseable { matrikkeldb.prepareStatement(INTERIOR_SQL) }.apply {
                this.fetchSize = fetchSize
            }
            val (extRs, mnrRs, intRs) = withContext(Dispatchers.IO) {
                val extRsDeferred = async {
                    autoCloseable { exteriorsStatement.executeQuery() }
                }
                val mnrRsDeferred = async {
                    autoCloseable { matrikkelnummerStatement.executeQuery() }
                }
                val intRsDeferred = async {
                    autoCloseable { interiorsStatement.executeQuery() }
                }
                Triple(extRsDeferred.await(), mnrRsDeferred.await(), intRsDeferred.await())
            }
            progressChannel.send(ProgressEvent(0, teigCountEstimate))

            var extRsTeigId = if (extRs.next()) extRs.getLong(1) else Long.MAX_VALUE
            var mnrRsTeigId = if (mnrRs.next()) mnrRs.getLong(1) else Long.MAX_VALUE
            var intRsTeigId = if (intRs.next()) intRs.getLong(1) else Long.MAX_VALUE
            var currentTeigId = minOf(extRsTeigId, mnrRsTeigId, intRsTeigId)
            val extGrenselinjeIds = ArrayList<Pair<Long, Boolean>>()
            val intGrenselinjeIds = ArrayList<Pair<Long, Boolean>>()
            val interiorsOffsets = ArrayList<Int>()
            val mnrs = ArrayList<TempMnr>()
            val teigOffsets = ArrayList<Int>()
            val teigCountUpdateInterval = maxOf(1, teigCountEstimate / 100)
            var teigCount = 0
            val bufHeadroom = targetBufferSize / 128
            val fb = FlatBufferBuilder(
                HeapByteBufferFactory.INSTANCE.newByteBuffer(targetBufferSize),
                HeapByteBufferFactory.INSTANCE
            )

            while (true) {

                // Få alle resultsettene til å peke på første rad for teigen som skal skrives til flatbuffer
                while (extRsTeigId < currentTeigId) {
                    extRsTeigId = if (extRs.next()) extRs.getLong(1) else Long.MAX_VALUE
                }
                while (mnrRsTeigId < currentTeigId) {
                    mnrRsTeigId = if (mnrRs.next()) mnrRs.getLong(1) else Long.MAX_VALUE
                }
                while (intRsTeigId < currentTeigId) {
                    if (intRs.next()) intRs.getLong(1) else Long.MAX_VALUE
                }

                // Skriv matrikkelnummere for teigen
                val mnrVectorOffset: Int? = if (currentTeigId == mnrRsTeigId) {
                    mnrs.clear()
                    while (currentTeigId == mnrRsTeigId) {
                        mnrs.add(TempMnr(
                            mnrRs.getShort(2),
                            mnrRs.getInt(3),
                            mnrRs.getShort(4),
                            mnrRs.getShort(5),
                            mnrRs.getShort(6)))
                        mnrRsTeigId = if (mnrRs.next()) mnrRs.getLong(1) else Long.MAX_VALUE
                    }
                    if (mnrs.isNotEmpty()) {
                        mnrs.reverse()
                        Teig.startMatrikkelnummerVector(fb, mnrs.size)
                        mnrs.forEach { mnr ->
                            Matrikkelnummer.createMatrikkelnummer(
                                fb,
                                mnr.knr,
                                mnr.gnr,
                                mnr.bnr,
                                mnr.fnr,
                                mnr.snr
                            )
                        }
                        fb.endVector()
                    } else {
                        null
                    }
                } else {
                    null
                }

                // Skriv grenselinjer for teigen
                extGrenselinjeIds.clear()
                while (currentTeigId == extRsTeigId) {
                    extGrenselinjeIds.add(extRs.getLong(2) to extRs.getBoolean(3))
                    extRsTeigId = if (extRs.next()) extRs.getLong(1) else Long.MAX_VALUE
                }
                val extGrenselinjeVectorOffset: Int? = if (extGrenselinjeIds.isNotEmpty()) {
                    extGrenselinjeIds.reverse()
                    Teig.startGrenselinjeDirectionsVector(fb, extGrenselinjeIds.size)
                    extGrenselinjeIds.forEach { (id, signed) ->
                        GrenselinjeDirection.createGrenselinjeDirection(fb, id, !signed)
                    }
                    fb.endVector()
                } else {
                    null
                }

                // Skriv innvendige grenselinjer for teigen
                interiorsOffsets.clear()
                while (currentTeigId == intRsTeigId) {
                    intGrenselinjeIds.clear()
                    val currentBoundaryId = intRs.getLong(2)
                    do {
                        intGrenselinjeIds.add(intRs.getLong(3) to intRs.getBoolean(4))
                        intRsTeigId = if (intRs.next()) intRs.getLong(1) else Long.MAX_VALUE
                    } while (currentTeigId == intRsTeigId && currentBoundaryId == intRs.getLong(2))

                    if (intGrenselinjeIds.isNotEmpty()) {
                        intGrenselinjeIds.reverse()
                        TeigInterior.startGrenselinjeDirectionsVector(fb, intGrenselinjeIds.size)
                        intGrenselinjeIds.forEach { (id, signed) ->
                            GrenselinjeDirection.createGrenselinjeDirection(fb, id, !signed)
                        }
                        val intGrenslinjeOffsets = fb.endVector()
                        TeigInterior.startTeigInterior(fb)
                        TeigInterior.addGrenselinjeDirections(fb, intGrenslinjeOffsets)
                        interiorsOffsets.add(TeigInterior.endTeigInterior(fb))
                    }
                }
                val intGrenselinjeVectorOffset: Int? = if (interiorsOffsets.isNotEmpty()) {
                    Teig.createInteriorsVector(fb, interiorsOffsets.toIntArray())
                } else {
                    null
                }

                // Skriv teigen
                Teig.startTeig(fb)
                Teig.addTeigId(fb, currentTeigId)
                if (mnrVectorOffset != null) {
                    Teig.addMatrikkelnummer(fb, mnrVectorOffset)
                }
                if (extGrenselinjeVectorOffset != null) {
                    Teig.addGrenselinjeDirections(fb, extGrenselinjeVectorOffset)
                }

                if (intGrenselinjeVectorOffset != null) {
                    Teig.addInteriors(fb, intGrenselinjeVectorOffset)
                }
                teigOffsets.add(Teig.endTeig(fb))

                // Oppdater progressevent, for hver prosent
                teigCount++
                if (teigCount % teigCountUpdateInterval == 0) {
                    progressChannel.send(ProgressEvent(teigCount, teigCountEstimate))
                }

                // Gå til neste teig
                currentTeigId = minOf(extRsTeigId, mnrRsTeigId, intRsTeigId)
                val done = currentTeigId == Long.MAX_VALUE
                if (done || fb.offset() + bufHeadroom >= targetBufferSize) {
                    TeigBuffer.startTeigerVector(fb, teigOffsets.size)
                    teigOffsets.forEach(fb::addOffset)
                    val teigerOffset = fb.endVector()
                    TeigBuffer.startTeigBuffer(fb)
                    TeigBuffer.addTeiger(fb, teigerOffset)
                    fb.finish(TeigBuffer.endTeigBuffer(fb))
                    emit(TeigBuffer.getRootAsTeigBuffer(fb.dataBuffer().slice()))
                    fb.init(
                        HeapByteBufferFactory.INSTANCE.newByteBuffer(targetBufferSize),
                        HeapByteBufferFactory.INSTANCE
                    )
                    teigOffsets.clear()
                    if (done) {
                        // Send siste progressevent
                        progressChannel.send(ProgressEvent(teigCount, teigCountEstimate))
                        break
                    }
                }
            }
        }
    }.buffer(1)
}

/**
 * Validates and converts a maalemetodeKode value from the database.
 * Maalemetode is stored as an integer in the Oracle database with values
 * between 0 and 62 (inclusive), and can be null.
 *
 * For null values, MaalemetodeKode.UKJENT (62) is returned.
 * For any other invalid values, an exception is thrown to ensure data integrity.
 *
 * @param rawValue The raw value from the database
 * @param wasNull Indicates if the database field was null
 * @return A valid byte value for MaalemetodeKode
 * @throws IllegalArgumentException if the value is not within the valid range (0-69)
 * @throws ClassCastException if the value cannot be converted to an integer
 */
private fun validateMaalemetodeKode(rawValue: Any?, wasNull: Boolean): Byte {
    // Handle null values by returning UKJENT (62)
    if (rawValue == null || wasNull) {
        return MaalemetodeKode.UKJENT
    }

    if (rawValue !is Number) {
        throw ClassCastException("Expected a Number for maalemetodeKode but got ${rawValue.javaClass.name}")
    }

    val intValue = rawValue.toInt()
    if (intValue !in 0..69) {
        throw IllegalArgumentException("MaalemetodeKode value $intValue is outside the valid range (0-69)")
    }

    return intValue.toByte()
}

/**
 * Validates and converts a noyaktighetsklasse value from the database.
 * Noyaktighetsklasse is stored as an integer in the Oracle database with values
 * between 0 and 6 (inclusive), and can be null.
 *
 * For null values, NoyaktighetsklasseKode.INGEN_NOYAKTIGHET (6) is returned.
 * For any other invalid values, an exception is thrown to ensure data integrity.
 *
 * @param rawValue The raw value from the database
 * @param wasNull Indicates if the database field was null
 * @return A valid byte value for NoyaktighetsklasseKode
 * @throws IllegalArgumentException if the value is not within the valid range (0-6)
 * @throws ClassCastException if the value cannot be converted to an integer
 */
private fun validateNoyaktighetsklasse(rawValue: Any?, wasNull: Boolean): Byte {
    // Handle null values by returning INGEN_NOYAKTIGHET (6)
    if (rawValue == null || wasNull) {
        return NoyaktighetsklasseKode.INGEN_NOYAKTIGHET
    }

    if (rawValue !is Number) {
        throw ClassCastException("Expected a Number for noyaktighetsklasse but got ${rawValue.javaClass.name}")
    }

    val intValue = rawValue.toInt()
    if (intValue !in 0..6) {
        throw IllegalArgumentException("NoyaktighetsklasseKode value $intValue is outside the valid range (0-6)")
    }

    return intValue.toByte()
}

internal inline fun FlatBufferBuilder.createGrensepunkt(
    id: Long,
    x: Long,
    y: Long,
    koordinatsystemKode: Byte,
    grensemerkeNedsattIKode: Byte,
    grensemerkeKode: Byte,
    malemetodeKode: Byte,
    noyaktighet: Int,
    datafangstdato: java.time.LocalDate?,
    oppdateringsdato: Instant,
    grenspunktnrOffset: Int?
): Int {
    Grensepunkt.startGrensepunkt(this)
    Grensepunkt.addId(this, id)
    Grensepunkt.addKoordinat(this, Koordinat.createKoordinat(this, x, y))
    Grensepunkt.addKoordinatsystemKode(this, koordinatsystemKode)
    Grensepunkt.addGrensemerkeNedsattIKode(this, grensemerkeNedsattIKode)
    Grensepunkt.addGrensmerkodeKode(this, grensemerkeKode)
    Grensepunkt.addPosisjonskvalitet(
        this, Posisjonskvalitet.createPosisjonskvalitet(
        this, malemetodeKode, noyaktighet
    )
    )
    datafangstdato?.let {
        Grensepunkt.addDatafangstdato(
            this,
            FBLocalDate.createLocalDate(
                this,
                it.year,
                it.monthValue.toShort(),
                it.dayOfMonth.toShort()
            )
        )
    }
    if (grenspunktnrOffset != null) {
        Grensepunkt.addGrensepunktnr(this, grenspunktnrOffset)
    }
    Grensepunkt.addOppdateringsdato(
        this,
        Timestamp.createTimestamp(this, oppdateringsdato.epochSecond, oppdateringsdato.nano)
    )
    return Grensepunkt.endGrensepunkt(this)
}

@OptIn(DelicateCoroutinesApi::class)
@ExperimentalCoroutinesApi
fun downloadMatrikkelGeometry(url: String, username: String, password: String?, dirName: String?): Long = runBlocking {
    resourceScope {
        val fetchDispatcher = autoCloseable { newSingleThreadContext("fetchDispatcher") }
        val writeDispatcher = autoCloseable { newSingleThreadContext("writeDispatcher") }

        // Opprett en databaseforbindelse til matrikkeldatabasen, og sett opp en read-only transaksjon for å få
        // konsistent lesning av data.
        val (matrikkeldb, endringsnummer) = autoCloseable {
            DriverManager.getConnection(
                url,
                username,
                password
            ) as OracleConnection
        }.run {
            autoCommit = false
            this to createStatement().use { st ->
                st.execute("SET TRANSACTION READ ONLY")
                st.executeQuery("SELECT max(e.id) FROM endring e").use { rs ->
                    check(rs.next()) { "No max endring id" }
                    rs.getLong(1)
                }
            }
        }
        println("Endringsnummer: $endringsnummer")

        val outDir = File(File("."), "$dirName-$endringsnummer")
        check(outDir.mkdir()) { "Failed to create directory" }

        withContext(writeDispatcher) {
            val (progress, flow) = fetchGrenslinjer(matrikkeldb, 1024 * 1024 * 1024)
            CoroutineScope(Dispatchers.Default).launch {
                println("Querying for grenselinjer...")
                progress.collect { (current, total) ->
                    println("Grenselinje: $current / ca. $total")
                }
            }
            flow.flowOn(fetchDispatcher).collectIndexed { index, value ->
                RandomAccessFile(File(outDir, String.format("grenselinje_%03d.fb", index)), "rw").use { file ->
                    file.channel.write(value.byteBuffer)
                }
            }
        }

        withContext(writeDispatcher) {
            val (progress, flow) = fetchGrenspunkt(matrikkeldb, 1024 * 1024 * 1024)
            CoroutineScope(Dispatchers.Default).launch {
                println("Querying for grensepunkter...")
                progress.collect { (current, total) ->
                    println("Grensepunkt: $current / ca. $total")
                }
            }
            flow.flowOn(fetchDispatcher).collectIndexed { index, value ->
                RandomAccessFile(File(outDir, String.format("grensepunkt%03d.fb", index)), "rw").use { file ->
                    file.channel.write(value.byteBuffer)
                }
            }
        }

        withContext(writeDispatcher) {
            val (progress, flow) = fetchTeiger(matrikkeldb, 1024 * 1024 * 1024)
            CoroutineScope(Dispatchers.Default).launch {
                println("Querying for teiger...")
                progress.collect { (current, total) ->
                    println("Teig: $current / ca. $total")
                }
            }
            flow.flowOn(fetchDispatcher).collectIndexed { index, value ->
                RandomAccessFile(File(outDir, String.format("teig%03d.fb", index)), "rw").use { file ->
                    file.channel.write(value.byteBuffer)
                }
            }
        }

        endringsnummer
    }
}


@OptIn(ExperimentalCoroutinesApi::class)
fun main() {
    // ------------------------------------------------------------------------
    // Last ned rå database til flatbufferfiler
    // -------------------------------------------------------------------------
    val url = System.getenv("MATRIKKEL_DB_URL") ?: error("MATRIKKEL_DB_URL not set")
    val username = System.getenv("MATRIKKEL_DB_USERNAME") ?: error("MATRIKKEL_DB_USERNAME not set")
    val password = System.getenv("MATRIKKEL_DB_PASSWORD") ?: error("MATRIKKEL_DB_PASSWORD not set")
    println("Downloading matrikkel data from $username@$url")
    val endringsnummer = downloadMatrikkelGeometry(
        url,
        username,
        password,
        "M22-DATA"
    )
    println(endringsnummer)
//    val endringsnummer = 364590149L
}
