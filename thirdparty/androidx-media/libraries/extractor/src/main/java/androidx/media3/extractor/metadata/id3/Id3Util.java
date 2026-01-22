/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.extractor.metadata.id3;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;

/** Utility methods for working with ID3 metadata. */
@UnstableApi
public final class Id3Util {

  private static final ImmutableList<String> STANDARD_GENRES =
      ImmutableList.of(
          // These are the official ID3v1 genres.
          "Blues",
          "Classic Rock",
          "Country",
          "Dance",
          "Disco",
          "Funk",
          "Grunge",
          "Hip-Hop",
          "Jazz",
          "Metal",
          "New Age",
          "Oldies",
          "Other",
          "Pop",
          "R&B",
          "Rap",
          "Reggae",
          "Rock",
          "Techno",
          "Industrial",
          "Alternative",
          "Ska",
          "Death Metal",
          "Pranks",
          "Soundtrack",
          "Euro-Techno",
          "Ambient",
          "Trip-Hop",
          "Vocal",
          "Jazz+Funk",
          "Fusion",
          "Trance",
          "Classical",
          "Instrumental",
          "Acid",
          "House",
          "Game",
          "Sound Clip",
          "Gospel",
          "Noise",
          "AlternRock",
          "Bass",
          "Soul",
          "Punk",
          "Space",
          "Meditative",
          "Instrumental Pop",
          "Instrumental Rock",
          "Ethnic",
          "Gothic",
          "Darkwave",
          "Techno-Industrial",
          "Electronic",
          "Pop-Folk",
          "Eurodance",
          "Dream",
          "Southern Rock",
          "Comedy",
          "Cult",
          "Gangsta",
          "Top 40",
          "Christian Rap",
          "Pop/Funk",
          "Jungle",
          "Native American",
          "Cabaret",
          "New Wave",
          "Psychadelic",
          "Rave",
          "Showtunes",
          "Trailer",
          "Lo-Fi",
          "Tribal",
          "Acid Punk",
          "Acid Jazz",
          "Polka",
          "Retro",
          "Musical",
          "Rock & Roll",
          "Hard Rock",
          // Genres made up by the authors of Winamp (v1.91) and later added to the ID3 spec.
          "Folk",
          "Folk-Rock",
          "National Folk",
          "Swing",
          "Fast Fusion",
          "Bebob",
          "Latin",
          "Revival",
          "Celtic",
          "Bluegrass",
          "Avantgarde",
          "Gothic Rock",
          "Progressive Rock",
          "Psychedelic Rock",
          "Symphonic Rock",
          "Slow Rock",
          "Big Band",
          "Chorus",
          "Easy Listening",
          "Acoustic",
          "Humour",
          "Speech",
          "Chanson",
          "Opera",
          "Chamber Music",
          "Sonata",
          "Symphony",
          "Booty Bass",
          "Primus",
          "Porn Groove",
          "Satire",
          "Slow Jam",
          "Club",
          "Tango",
          "Samba",
          "Folklore",
          "Ballad",
          "Power Ballad",
          "Rhythmic Soul",
          "Freestyle",
          "Duet",
          "Punk Rock",
          "Drum Solo",
          "A capella",
          "Euro-House",
          "Dance Hall",
          // Genres made up by the authors of Winamp (v1.91) but have not been added to the ID3
          // spec.
          "Goa",
          "Drum & Bass",
          "Club-House",
          "Hardcore",
          "Terror",
          "Indie",
          "BritPop",
          "Afro-Punk",
          "Polsk Punk",
          "Beat",
          "Christian Gangsta Rap",
          "Heavy Metal",
          "Black Metal",
          "Crossover",
          "Contemporary Christian",
          "Christian Rock",
          "Merengue",
          "Salsa",
          "Thrash Metal",
          "Anime",
          "Jpop",
          "Synthpop",
          // Genres made up by the authors of Winamp (v5.6) but have not been added to the ID3 spec.
          "Abstract",
          "Art Rock",
          "Baroque",
          "Bhangra",
          "Big beat",
          "Breakbeat",
          "Chillout",
          "Downtempo",
          "Dub",
          "EBM",
          "Eclectic",
          "Electro",
          "Electroclash",
          "Emo",
          "Experimental",
          "Garage",
          "Global",
          "IDM",
          "Illbient",
          "Industro-Goth",
          "Jam Band",
          "Krautrock",
          "Leftfield",
          "Lounge",
          "Math Rock",
          "New Romantic",
          "Nu-Breakz",
          "Post-Punk",
          "Post-Rock",
          "Psytrance",
          "Shoegaze",
          "Space Rock",
          "Trop Rock",
          "World Music",
          "Neoclassical",
          "Audiobook",
          "Audio theatre",
          "Neue Deutsche Welle",
          "Podcast",
          "Indie-Rock",
          "G-Funk",
          "Dubstep",
          "Garage Rock",
          "Psybient");

  /**
   * Resolves an ID3v1 numeric genre code to its string form, or {@code null} if the code isn't
   * recognized.
   *
   * <p>Includes codes that were added later by various versions of Winamp. See this Wikipedia <a
   * href="https://en.wikipedia.org/wiki/List_of_ID3v1_genres">list of official and unofficial ID3v1
   * genres</a>.
   */
  @Nullable
  public static String resolveV1Genre(int id3v1GenreCode) {
    return id3v1GenreCode >= 0 && id3v1GenreCode < STANDARD_GENRES.size()
        ? STANDARD_GENRES.get(id3v1GenreCode)
        : null;
  }

  private Id3Util() {}
}
