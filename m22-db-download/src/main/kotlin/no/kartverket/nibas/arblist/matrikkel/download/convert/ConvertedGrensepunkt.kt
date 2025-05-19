package no.kartverket.nibas.arblist.matrikkel.download.convert

import no.kartverket.nibas.flatbuffer.Nibas.MatrikkelGrenseDB
import no.kartverket.nibas.flatbuffer.Nibas.MatrikkelGrenseEntry
import no.kartverket.nibas.flatbuffer.Nibas.MatrikkelGrensepunktDB
import no.kartverket.nibas.flatbuffer.Nibas.MatrikkelGrensepunktEntry
import java.io.File

class ConvertedGrensepunkt(
    dir: File,
    filename: String
) : ConvertedFile.LongKey<MatrikkelGrensepunktEntry.Vector, MatrikkelGrensepunktEntry>(
    dir,
    filename,
    {
        MatrikkelGrensepunktDB.getRootAsMatrikkelGrensepunktDB(it).run {
            Page.LongKey(this.matrikkelgrensepunkterVector(), firstKey(), lastKey())
        }
    }
) {
    override val MatrikkelGrensepunktEntry.key: Long get() = id()

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER") // Delegerer til metoden som er shadowed
    override fun MatrikkelGrensepunktEntry.Vector.get(
        target: MatrikkelGrensepunktEntry,
        index: Int
    ): MatrikkelGrensepunktEntry = get(target, index)

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    override fun MatrikkelGrensepunktEntry.Vector.get(index: Int): MatrikkelGrensepunktEntry = get(index)

}


class ConvertedGrenselinjeFile(dir: File, filename: String) : ConvertedFile.LongKey<MatrikkelGrenseEntry.Vector, MatrikkelGrenseEntry>(
    dir,
    filename,
    {
        MatrikkelGrenseDB.getRootAsMatrikkelGrenseDB(it).run {
            Page.LongKey(this.matrikkelgrenserVector(), firstKey(), lastKey())
        }
    }
) {
    override val MatrikkelGrenseEntry.key: Long
        get() = id()

    override fun MatrikkelGrenseEntry.Vector.get(index: Int): MatrikkelGrenseEntry = get(index)

    override fun MatrikkelGrenseEntry.Vector.get(target: MatrikkelGrenseEntry, index: Int): MatrikkelGrenseEntry =
        get(target, index)

}
