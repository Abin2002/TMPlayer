package com.tmplayer.data

/**
 * File names in the shapes real chats post them, with made-up titles, and what each should read
 * as. Shared by the name, grouping and search tests, so a parser change is checked against one
 * list. Every title here is invented; none is a real film or show.
 */
object NameCorpus {

    /** One name and its reading. Null fields are not checked. */
    data class Case(
        val fileName: String,
        val title: String,
        val year: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val part: Int? = null,
        val extra: ExtraKind? = null,
        val airDate: String? = null,
        val caption: String? = null,
    )

    val CASES = listOf(
        // A channel handle run into the name with underscores.
        Case("@HarbourMob_LinkZz_Night_Lantern_2022_Tamil_Proper_HQ_HDRip_700MB_x264_AAC.mkv", "Night Lantern", 2022),
        Case("@HEVC_Mob_Night_Lantern_2022_Tamil_720p.mkv", "Night Lantern", 2022),
        Case("@TG_Clips4u -Night Lantern (2024) Tamil 720p.mkv", "Night Lantern", 2024),
        Case("Paper_Kite_2005_Tamil_DVDRip_XviD_2.avi", "Paper Kite", 2005),
        Case("Paper Kite (2009) Tamil HDRip 720p x264.mkv", "Paper Kite", 2009),
        Case("www.filmsite.xyz - Paper Kite (2024) 1080p WEB-DL.mkv", "Paper Kite", 2024),
        Case("Paper Kite Tamil Hd.mp4", "Paper Kite"),
        Case("Paper Kite Full Movie HD | Arun Devan.mp4", "Paper Kite"),
        Case("Velvet Hour [ Arun Devan, Mira Joshi ].mp4", "Velvet Hour"),
        // Numbers that are not episodes.
        Case("Siren Patrol 100.mp4", "Siren Patrol 100"),
        Case("Harbour Lights Part 2 (2019) 720p.mkv", "Harbour Lights Part 2", 2019),
        Case("Harbour Lights Chapter 3 (2021) 1080p.mkv", "Harbour Lights Chapter 3", 2021),
        Case("Coast Run 2 2014 720p HDRip.mkv", "Coast Run 2", 2014),
        Case("1944.1080p.BluRay.mkv", "1944"),
        Case("1944 (2015) 1080p.mkv", "1944", 2015),
        Case("Tide 1080.mkv", "Tide 1080"),
        // Tag words inside a title, before its year.
        Case("The Italian Garden 2003 1080p BluRay.mkv", "The Italian Garden", 2003),
        Case("A Complete Stranger (2024) 720p.mkv", "A Complete Stranger", 2024),
        Case("The Final Cut 2004 720p.mkv", "The Final Cut", 2004),
        Case("Into the Web 2020 HDRip.mkv", "Into the Web", 2020),
        Case("Harbour Notes Tamil 2022 HDRip.mkv", "Harbour Notes", 2022),
        Case("Learn_Tamil_Fast.mp4", "Learn Tamil Fast"),
        // Split films.
        Case("Coast Run 2014 720p CD1.avi", "Coast Run", 2014, part = 1),
        Case("Coast.Run.2014.720p.CD2.avi", "Coast Run", 2014, part = 2),
        Case("Coast.Run.2014.Part2.mkv", "Coast Run", 2014, part = 2),
        Case("Coast Run Pt1.mkv", "Coast Run", part = 1),
        // Extras.
        Case("Coast Run (2014) Official Trailer.mp4", "Coast Run", 2014, extra = ExtraKind.Trailer),
        Case("Trailer - Coast Run (2014).mp4", "Coast Run", 2014, extra = ExtraKind.Trailer),
        Case("Coast Run 2014 Teaser 2 1080p.mp4", "Coast Run", 2014, extra = ExtraKind.Teaser),
        Case("The Sample Room (2020) 720p.mkv", "The Sample Room", 2020),
        Case("Sample Room Tales.mkv", "Sample Room Tales"),
        // Episodes, and episodes by air date.
        Case("Harbour.Notes.S01E02.1080p.WEB-DL.mkv", "Harbour Notes", season = 1, episode = 2),
        Case("Morning Desk 2024.10.14 720p.mp4", "Morning Desk", 2024, airDate = "2024-10-14"),
        Case("Morning Desk 14-10-2024.mp4", "Morning Desk", 2024, airDate = "2024-10-14"),
        // Other scripts and decorations.
        Case("இரவு நிலா (2023) 720p.mkv", "இரவு நிலா", 2023),
        Case("ليلة القمر (2021) 1080p.mkv", "ليلة القمر", 2021),
        Case("🎬🔥 Night Lantern (2022) 🔥.mkv", "Night Lantern", 2022),
    )
}
