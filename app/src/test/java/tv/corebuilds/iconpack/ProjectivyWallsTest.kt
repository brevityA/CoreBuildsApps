package tv.corebuilds.iconpack

import org.junit.Assert.assertEquals
import org.junit.Test

/** What Projectivy's rotation is handed: plain lists in, plain lists out. */
class ProjectivyWallsTest {

    private fun wall(file: String, series: String, plus: Boolean = false) = Wallpaper(
        name = file,
        series = series,
        url = "https://example.invalid/$series/$file.png",
        thumbUrl = "",
        resolution = "3840x2160",
        thumbAsset = "",
        plus = plus,
    )

    private val earth = wall("a", "series-12-earth")
    private val wild = wall("b", "series-13-wild")
    private val flux = wall("c", "series-14-flux")
    private val spectra = wall("d", "series-15-spectra", plus = true)
    private val main = listOf(earth, wild, flux)
    private val plus = listOf(spectra)

    private fun pick(
        owned: Boolean = true,
        source: String = ProjectivyWalls.ALL,
        night: String? = null,
        isNight: Boolean = false,
        favourites: Set<String> = emptySet(),
    ) = ProjectivyWalls.pick(main, plus, owned, source, night, isNight, favourites)

    @Test fun withoutPlusTheMainCollectionWhateverIsStored() {
        assertEquals(main, pick(owned = false))
        assertEquals(main, pick(owned = false, source = ProjectivyWalls.FAVOURITES, favourites = setOf("a.png")))
        assertEquals(main, pick(owned = false, source = "series-15-spectra"))
        assertEquals(main, pick(owned = false, night = "series-13-wild", isNight = true))
    }

    @Test fun withPlusAllWallsIncludeThePlusWalls() {
        assertEquals(main + plus, pick())
    }

    @Test fun favouritesAndASeriesChooseFromThePool() {
        assertEquals(listOf(wild, spectra),
            pick(source = ProjectivyWalls.FAVOURITES, favourites = setOf("b.png", "d.png")))
        assertEquals(listOf(flux), pick(source = "series-14-flux"))
        assertEquals(plus, pick(source = "series-15-spectra"))
    }

    @Test fun theNightSourceAppliesOnlyAtNight() {
        assertEquals(listOf(wild), pick(source = "series-12-earth", night = "series-13-wild", isNight = true))
        assertEquals(listOf(earth), pick(source = "series-12-earth", night = "series-13-wild", isNight = false))
    }

    @Test fun anEmptyChoiceFallsBackRatherThanEmptyingTheRotation() {
        // No favourites at night: the day source, then everything.
        assertEquals(listOf(earth), pick(source = "series-12-earth", night = ProjectivyWalls.FAVOURITES, isNight = true))
        assertEquals(main + plus, pick(source = ProjectivyWalls.FAVOURITES))
        assertEquals(main + plus, pick(source = "series-99-gone"))
    }

    @Test fun theRowOffersAllFavouritesThenEachSeriesOnce() {
        assertEquals(
            listOf(ProjectivyWalls.ALL, ProjectivyWalls.FAVOURITES,
                "series-12-earth", "series-13-wild", "series-14-flux", "series-15-spectra"),
            ProjectivyWalls.sources(main + plus + wall("e", "series-12-earth")),
        )
    }

    @Test fun theDayRowCyclesAndAnUnknownSourceStartsOver() {
        val options = ProjectivyWalls.sources(main)
        assertEquals(ProjectivyWalls.FAVOURITES, ProjectivyWalls.nextSource(ProjectivyWalls.ALL, options))
        assertEquals(ProjectivyWalls.ALL, ProjectivyWalls.nextSource("series-14-flux", options))
        assertEquals(ProjectivyWalls.ALL, ProjectivyWalls.nextSource("series-99-gone", options))
    }
}
