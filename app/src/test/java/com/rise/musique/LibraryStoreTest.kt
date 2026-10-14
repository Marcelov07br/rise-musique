package com.rise.musique

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File


/** The on-disk migration of a real ≤0.22 install: library.json + follow prefs + spotify_playlists.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LibraryStoreTest {
  private val c: Application get() = ApplicationProvider.getApplicationContext()
  @Before fun setUp() { Library.resetCache(); c.filesDir.listFiles()?.forEach { it.deleteRecursively() } }
  @After fun tearDown() { Library.resetCache() }

  @Test fun upgradesLegacyFilesInPlaceAndKeepsACopy() {
    File(c.filesDir, "library.json").writeText("""{"fav":["/m/a.mp3"],"pl":{"Treino":["/m/a.mp3","/m/b.mp3"]}}""")
    File(c.filesDir, "history.json").writeText(JSONArray().put(JSONObject().put("t", "A").put("m", "MP3").put("u", "/m/a.mp3").put("r", "Artista")).toString())
    File(c.filesDir, "spotify_playlists.json").writeText("""[{"id":"x1","type":"album","title":"Disco","tracks":[{"uri":"spotify:track:9","title":"T","artist":"S"}]}]""")
    c.getSharedPreferences("follow", 0).edit().putStringSet("artists", setOf("Elis")).commit()

    assertEquals(setOf("/m/a.mp3"), Library.favorites(c))
    assertEquals(listOf("/m/a.mp3", "/m/b.mp3"), Library.playlists(c)["Treino"])
    assertEquals("Artista", Library.data(c).track("/m/a.mp3")!!.artist)
    assertEquals(setOf("Elis"), FollowStore.all(c))
    assertEquals(listOf("Disco"), SpotifyStore.load(c).map { it.title })
    assertEquals("album", SpotifyStore.load(c).single().type)
    assertTrue(File(c.filesDir, "library.v1.json").exists())
    assertEquals(LibrarySchema.CURRENT, JSONObject(File(c.filesDir, "library.json").readText()).getInt("schema"))

    // Reload from disk: same data, no second migration.
    val before = Library.data(c); Library.resetCache()
    c.getSharedPreferences("follow", 0).edit().putStringSet("artists", setOf("Outro")).commit()
    assertEquals(before, Library.data(c))
  }

  @Test fun writesGoThroughTheUnifiedModel() {
    Library.createPlaylist(c, "Nova"); Library.addToPlaylist(c, "Nova", "rise-stream://dQw4w9WgXcQ")
    Library.toggleFavorite(c, "stream:https://music.youtube.com/watch?v=dQw4w9WgXcQ")
    Library.toggleArtist(c, "Djavan")
    SpotifyStore.savePlaylist(c, SpotifyPlaylist("p1", "playlist", "Lista", "", "", listOf(SpotifyTrack("spotify:track:1", "T", "A", 1000))))
    Library.resetCache()
    val d = Library.data(c)
    assertEquals(setOf("rise-stream://dQw4w9WgXcQ"), d.likedKeys)
    assertEquals("dQw4w9WgXcQ", d.track("rise-stream://dQw4w9WgXcQ")!!.videoId)
    assertEquals(setOf("Nova", "Lista"), d.playlists.map { it.name }.toSet())
    assertEquals(LibSource.SPOTIFY, d.playlists.first { it.name == "Lista" }.source)
    assertEquals(setOf("Djavan"), FollowStore.all(c))
    SpotifyStore.removePlaylist(c, "p1"); assertTrue(SpotifyStore.load(c).isEmpty())
  }
}
