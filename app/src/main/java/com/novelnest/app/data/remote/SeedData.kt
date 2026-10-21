package com.novelnest.app.data.remote

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.model.SectionType

object SeedData {

    // --- NOVEL PHOENIX SEED DATA ---
    val novelPhoenixHeroSlides = listOf(
        Novel(
            id = "np-1",
            title = "Shadow Slave",
            coverUrl = "https://novelphoenix.com/server-1/shadow-slave.jpg",
            source = NovelSourceType.NOVEL_PHOENIX,
            sourceUrl = "https://novelphoenix.com/novel/shadow-slave",
            synopsis = "Growing up in poverty, Sunny was not entirely oblivious to the things in store for him. But when the Nightmare Spell fell upon the world, he awoke to an enigmatic destiny.",
            author = "Guiltythree",
            rating = 4.9f,
            latestChapter = "Chapter 1845",
            genres = listOf("Fantasy", "Action", "Mystery", "System"),
            status = "Ongoing",
            views = "5.2M"
        ),
        Novel(
            id = "np-2",
            title = "Full-Star Player Returns as a Godslayer",
            coverUrl = "https://novelphoenix.com/server-1/full-star-player-returns-as-a-godslayer.jpg",
            source = NovelSourceType.NOVEL_PHOENIX,
            sourceUrl = "https://novelphoenix.com/novel/full-star-player-returns-as-a-godslayer",
            synopsis = "The legendary apex player returns after slaying the deities of the outer realm, armed with god-tier techniques to conquer modern catastrophe.",
            author = "Star Abyss",
            rating = 4.85f,
            latestChapter = "Chapter 310",
            genres = listOf("Action", "System", "Overpowered"),
            status = "Ongoing",
            views = "3.1M"
        ),
        Novel(
            id = "np-3",
            title = "Genius Martial Arts Trainer",
            coverUrl = "https://novelphoenix.com/server-1/genius-martial-arts-trainer.jpg",
            source = NovelSourceType.NOVEL_PHOENIX,
            sourceUrl = "https://novelphoenix.com/novel/genius-martial-arts-trainer",
            synopsis = "Blessed with the eyes of supreme enlightenment, Kang can deduce any martial art flaws with a single glance and forge unparalleled masters.",
            author = "Iron Fist",
            rating = 4.78f,
            latestChapter = "Chapter 454",
            genres = listOf("Martial Arts", "Cultivation", "Progression"),
            status = "Ongoing",
            views = "2.9M"
        )
    )

    val novelPhoenixTrending = listOf(
        Novel(
            id = "np-4",
            title = "The Mine Lord: A Dwarven Survival Base-Builder",
            coverUrl = "https://novelphoenix.com/server-1/the-mine-lord-a-dwarven-survival-base-builder.jpg",
            source = NovelSourceType.NOVEL_PHOENIX,
            sourceUrl = "https://novelphoenix.com/novel/the-mine-lord-a-dwarven-survival-base-builder",
            synopsis = "Reincarnated underground in a subterranean fantasy world with a dwarven citadel system, building impenetrable subterranean bastions.",
            author = "DeepDelver",
            rating = 4.8f,
            latestChapter = "Chapter 132",
            genres = listOf("Kingdom Building", "LitRPG", "Survival"),
            status = "Ongoing"
        ),
        Novel(
            id = "np-5",
            title = "Lord of the Mysteries",
            coverUrl = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=800&q=80",
            source = NovelSourceType.NOVEL_PHOENIX,
            sourceUrl = "https://novelphoenix.com/novel/lord-of-the-mysteries",
            synopsis = "With the rising tide of steam power and machinery, who can come close to being a Beyonder? Shrouded in the fog of history and darkness.",
            author = "Cuttlefish That Loves Diving",
            rating = 4.95f,
            latestChapter = "Chapter 1432 (Completed)",
            genres = listOf("Steampunk", "Supernatural", "Mystery"),
            status = "Completed"
        ),
        Novel(
            id = "np-6",
            title = "Reverend Insanity",
            coverUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=600&q=80",
            source = NovelSourceType.NOVEL_PHOENIX,
            sourceUrl = "https://novelphoenix.com/novel/reverend-insanity",
            synopsis = "A scheming ancient demon travels five hundred years back in time with the Spring Autumn Cicada to conquer the Gu world.",
            author = "Gu Zhen Ren",
            rating = 4.92f,
            latestChapter = "Chapter 2334",
            genres = listOf("Xianxia", "Villain MC", "Dark Fantasy"),
            status = "Ongoing"
        ),
        Novel(
            id = "np-7",
            title = "Collecting Powerful Girlfriends: My Invincible Harem",
            coverUrl = "https://novelphoenix.com/server-1/collecting-powerful-girlfriends-my-invincible-harem.jpg",
            source = NovelSourceType.NOVEL_PHOENIX,
            sourceUrl = "https://novelphoenix.com/novel/collecting-powerful-girlfriends-my-invincible-harem",
            synopsis = "Awakening a supernatural charm enhancement system, Lin Chen forms an invincible syndicate with extraordinary heroines.",
            author = "Night Breeze",
            rating = 4.65f,
            latestChapter = "Chapter 637",
            genres = listOf("Harem", "Action", "Supernatural"),
            status = "Ongoing"
        )
    )

    val novelPhoenixLatest = listOf(
        Novel(
            id = "np-8",
            title = "Tyrant? No, I am the Villain",
            coverUrl = "https://novelphoenix.com/server-1/tyrant-no-i-am-the-villain.jpg",
            source = NovelSourceType.NOVEL_PHOENIX,
            sourceUrl = "https://novelphoenix.com/novel/tyrant-no-i-am-the-villain",
            synopsis = "Transmigrating into a web novel as the final boss destined to be slain, he outmaneuvers the protagonist at every turn.",
            author = "Dark Throne",
            rating = 4.7f,
            latestChapter = "Chapter 123",
            genres = listOf("Villain", "Strategy", "Fantasy"),
            status = "Ongoing"
        ),
        Novel(
            id = "np-9",
            title = "The Beginning After the End",
            coverUrl = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?auto=format&fit=crop&w=800&q=80",
            source = NovelSourceType.NOVEL_PHOENIX,
            sourceUrl = "https://novelphoenix.com/novel/the-beginning-after-the-end",
            synopsis = "King Grey has unrivaled strength and prestige. Reborn into a new world steeped in magic and monsters, he gets a second chance.",
            author = "TurtleMe",
            rating = 4.88f,
            latestChapter = "Chapter 480",
            genres = listOf("Action", "Adventure", "Reincarnation"),
            status = "Ongoing"
        ),
        Novel(
            id = "np-10",
            title = "Mother of Learning",
            coverUrl = "https://images.unsplash.com/photo-1456513080510-7bf3a84b82f8?auto=format&fit=crop&w=600&q=80",
            source = NovelSourceType.NOVEL_PHOENIX,
            sourceUrl = "https://novelphoenix.com/novel/mother-of-learning",
            synopsis = "Zorian Kazinski is caught in a magical one-month time loop and must master esoteric magic to survive.",
            author = "nobody103",
            rating = 4.95f,
            latestChapter = "Chapter 108 (Completed)",
            genres = listOf("Time Loop", "Magic", "Progression"),
            status = "Completed"
        )
    )

    // --- FREE WEB NOVEL SEED DATA ---
    val fwnHeroSlides = listOf(
        Novel(
            id = "fwn-1",
            title = "Martial World",
            coverUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?auto=format&fit=crop&w=800&q=80",
            source = NovelSourceType.FREE_WEB_NOVEL,
            sourceUrl = "https://freewebnovel.com/martial-world.html",
            synopsis = "In the Realm of the Gods, countless legends contend. A young youth from the Sky Spill Continent obtains the Magic Cube and steps onto the path of peak martial arts.",
            author = "Cocooned Cow",
            rating = 4.88f,
            latestChapter = "Chapter 2255",
            genres = listOf("Martial Arts", "Xianxia", "Action"),
            status = "Completed",
            views = "5.1M"
        ),
        Novel(
            id = "fwn-2",
            title = "Chrysalis",
            coverUrl = "https://images.unsplash.com/photo-1532012164546-f432f2e3edd4?auto=format&fit=crop&w=800&q=80",
            source = NovelSourceType.FREE_WEB_NOVEL,
            sourceUrl = "https://freewebnovel.com/chrysalis.html",
            synopsis = "Anthony reawakens in a terrifying fantasy dungeon reincarnated as a monster ant. For the Colony!",
            author = "RinoZ",
            rating = 4.75f,
            latestChapter = "Chapter 1340",
            genres = listOf("Monster MC", "LitRPG", "Comedy"),
            status = "Ongoing",
            views = "2.8M"
        )
    )

    val fwnTrending = listOf(
        Novel(
            id = "fwn-3",
            title = "Coiling Dragon",
            coverUrl = "https://images.unsplash.com/photo-1516979187457-637abb4f9353?auto=format&fit=crop&w=600&q=80",
            source = NovelSourceType.FREE_WEB_NOVEL,
            sourceUrl = "https://freewebnovel.com/coiling-dragon.html",
            synopsis = "Linley Baruch finds a ring resembling a coiling dragon that completely changes the fate of the Four Divine Beasts Clan.",
            author = "I Eat Tomatoes",
            rating = 4.82f,
            latestChapter = "Book 21 Chapter 44",
            genres = listOf("Cultivation", "Dragons", "Fantasy"),
            status = "Completed"
        ),
        Novel(
            id = "fwn-4",
            title = "Birth of the Demonic Sword",
            coverUrl = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=600&q=80",
            source = NovelSourceType.FREE_WEB_NOVEL,
            sourceUrl = "https://freewebnovel.com/birth-of-the-demonic-sword.html",
            synopsis = "Noah Balvan is reborn into a cruel world where rank and birth determine life. He crafts a path of blood and darkness.",
            author = "Erebus",
            rating = 4.7f,
            latestChapter = "Chapter 2100",
            genres = listOf("Dark Fantasy", "Cultivation", "Action"),
            status = "Completed"
        ),
        Novel(
            id = "fwn-5",
            title = "Warlock of the Magus World",
            coverUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=600&q=80",
            source = NovelSourceType.FREE_WEB_NOVEL,
            sourceUrl = "https://freewebnovel.com/warlock-of-the-magus-world.html",
            synopsis = "Leylin transmigrates into a magical world with an advanced biological AI chip fused into his soul.",
            author = "The Plagiarist",
            rating = 4.79f,
            latestChapter = "Chapter 1200",
            genres = listOf("Sci-Fi Magic", "Ruthless MC", "Warlock"),
            status = "Completed"
        )
    )

    // --- ASURA SCANS (MANHWA) SEED DATA ---
    val asuraHeroSlides = listOf(
        Novel(
            id = "asura-1",
            title = "Solo Leveling: Ragnarok",
            coverUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?auto=format&fit=crop&w=800&q=80",
            source = NovelSourceType.ASURA_SCANS,
            sourceUrl = "https://asuracomic.net/series/solo-leveling-ragnarok",
            synopsis = "The earth faces catastrophic cosmic threats as the outer gods arrive. Sung Suho, son of the Shadow Monarch Sung Jinwoo, awakens his bloodline.",
            author = "Chugong / Daul",
            rating = 4.96f,
            latestChapter = "Chapter 42",
            genres = listOf("Action", "Fantasy", "Monarch", "Hunters"),
            status = "Ongoing",
            views = "8.4M"
        ),
        Novel(
            id = "asura-2",
            title = "Revenge of the Iron-Blooded Sword Hound",
            coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=800&q=80",
            source = NovelSourceType.ASURA_SCANS,
            sourceUrl = "https://asuracomic.net/series/sword-hound",
            synopsis = "The loyal hunting dog of the Baskerville clan, Vikir, was falsely executed. Reborn into the past, he sharpens his fangs for vengeance.",
            author = "Redice Studio",
            rating = 4.88f,
            latestChapter = "Chapter 85",
            genres = listOf("Revenge", "Action", "Swordmaster"),
            status = "Ongoing",
            views = "5.6M"
        ),
        Novel(
            id = "asura-3",
            title = "The Greatest Estate Developer",
            coverUrl = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?auto=format&fit=crop&w=800&q=80",
            source = NovelSourceType.ASURA_SCANS,
            sourceUrl = "https://asuracomic.net/series/the-greatest-estate-developer",
            synopsis = "Civil engineering student Suho wakes up as Lloyd Frontera, an infamous debtor noble. Using modern engineering knowledge, he transforms the territory with hilarious expressions.",
            author = "BK_Moon",
            rating = 4.97f,
            latestChapter = "Chapter 155",
            genres = listOf("Comedy", "Fantasy", "Kingdom Building"),
            status = "Ongoing",
            views = "7.9M"
        )
    )

    val asuraTrending = listOf(
        Novel(
            id = "asura-4",
            title = "Standard of Reincarnation",
            coverUrl = "https://images.unsplash.com/photo-1507842229451-7f01be777a26?auto=format&fit=crop&w=600&q=80",
            source = NovelSourceType.ASURA_SCANS,
            sourceUrl = "https://asuracomic.net/series/standard-of-reincarnation",
            synopsis = "The one-armed martial genius Daven of the legendary Samion family defies his fate after reincarnation.",
            author = "Blue Wolf",
            rating = 4.82f,
            latestChapter = "Chapter 112",
            genres = listOf("Martial Arts", "Reincarnation", "Action"),
            status = "Ongoing"
        ),
        Novel(
            id = "asura-5",
            title = "Damn Reincarnation",
            coverUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=600&q=80",
            source = NovelSourceType.ASURA_SCANS,
            sourceUrl = "https://asuracomic.net/series/damn-reincarnation",
            synopsis = "Hamel, who perished right before defeating the Demon King with his party, wakes up three hundred years later as the descendant of his rival, Great Vermouth.",
            author = "Mok-ma",
            rating = 4.85f,
            latestChapter = "Chapter 88",
            genres = listOf("Action", "Rebirth", "Fantasy"),
            status = "Ongoing"
        ),
        Novel(
            id = "asura-6",
            title = "Return of the Mount Hua Sect",
            coverUrl = "https://images.unsplash.com/photo-1512820790803-83ca734da794?auto=format&fit=crop&w=600&q=80",
            source = NovelSourceType.ASURA_SCANS,
            sourceUrl = "https://asuracomic.net/series/return-of-the-mount-hua-sect",
            synopsis = "The Plum Blossom Sword Saint Chung Myung defeats the Heavenly Demon, only to reincarnate 100 years later to find his cherished sect in complete ruins.",
            author = "Biga",
            rating = 4.95f,
            latestChapter = "Chapter 136",
            genres = listOf("Murim", "Comedy", "Martial Arts"),
            status = "Ongoing"
        )
    )

    val asuraLatest = listOf(
        Novel(
            id = "asura-7",
            title = "Pick Me Up, Infinite Gacha",
            coverUrl = "https://images.unsplash.com/photo-1519791883288-dc8bd696e667?auto=format&fit=crop&w=600&q=80",
            source = NovelSourceType.ASURA_SCANS,
            sourceUrl = "https://asuracomic.net/series/pick-me-up",
            synopsis = "The top-ranked mobile gacha player Han Yslat is pulled directly into the brutal mobile game 'Pick Me Up' as a 1-star expendable unit.",
            author = "Hermod",
            rating = 4.91f,
            latestChapter = "Chapter 96",
            genres = listOf("Survival", "Gacha", "Dark Action"),
            status = "Ongoing"
        ),
        Novel(
            id = "asura-8",
            title = "Player Who Returned 10,000 Years Later",
            coverUrl = "https://images.unsplash.com/photo-1463320726281-696a485928c7?auto=format&fit=crop&w=600&q=80",
            source = NovelSourceType.ASURA_SCANS,
            sourceUrl = "https://asuracomic.net/series/player-returned",
            synopsis = "Kang Woo survived ten thousand years in the depths of Hell. Now he returns to modern Earth craving kimchi jjigae and peace.",
            author = "Butterfly Valley",
            rating = 4.74f,
            latestChapter = "Chapter 98",
            genres = listOf("Demonic", "Overpowered MC", "Comedy"),
            status = "Ongoing"
        )
    )

    fun getSectionsFor(source: NovelSourceType): List<NovelSection> {
        return when (source) {
            NovelSourceType.NOVEL_PHOENIX -> listOf(
                NovelSection(
                    id = "np-hero",
                    title = "Phoenix Featured",
                    iconEmoji = "",
                    subtitle = "Handpicked light & web novels",
                    type = SectionType.HERO_SLIDER,
                    novels = novelPhoenixHeroSlides
                ),
                NovelSection(
                    id = "np-trending",
                    title = "Top Trending Novels",
                    iconEmoji = "",
                    subtitle = "Most read on NovelPhoenix this week",
                    type = SectionType.HORIZONTAL_LIST,
                    novels = novelPhoenixTrending
                ),
                NovelSection(
                    id = "np-latest",
                    title = "Recently Added & Completed",
                    iconEmoji = "",
                    subtitle = "Fresh chapters and completed sagas",
                    type = SectionType.HORIZONTAL_LIST,
                    novels = novelPhoenixLatest
                )
            )

            NovelSourceType.FREE_WEB_NOVEL -> listOf(
                NovelSection(
                    id = "fwn-hero",
                    title = "FWN Highlights",
                    iconEmoji = "",
                    subtitle = "Popular free web novels",
                    type = SectionType.HERO_SLIDER,
                    novels = fwnHeroSlides
                ),
                NovelSection(
                    id = "fwn-trending",
                    title = "Hot Rankings",
                    iconEmoji = "",
                    subtitle = "Top ranked books on FreeWebNovel",
                    type = SectionType.HORIZONTAL_LIST,
                    novels = fwnTrending
                )
            )

            NovelSourceType.ASURA_SCANS -> listOf(
                NovelSection(
                    id = "asura-hero",
                    title = "Top Manhwa Spotlight",
                    iconEmoji = "",
                    subtitle = "High adrenaline Korean manhwa",
                    type = SectionType.HERO_SLIDER,
                    novels = asuraHeroSlides
                ),
                NovelSection(
                    id = "asura-trending",
                    title = "Most Popular Manhwa",
                    iconEmoji = "",
                    subtitle = "Trending updates from Asura Scans",
                    type = SectionType.HORIZONTAL_LIST,
                    novels = asuraTrending
                ),
                NovelSection(
                    id = "asura-latest",
                    title = "Fresh Releases",
                    iconEmoji = "",
                    subtitle = "Brand new manhwa chapters",
                    type = SectionType.HORIZONTAL_LIST,
                    novels = asuraLatest
                )
            )
        }
    }

    fun search(source: NovelSourceType, query: String): List<Novel> {
        val q = query.trim().lowercase()
        val allNovels = when (source) {
            NovelSourceType.NOVEL_PHOENIX -> (novelPhoenixHeroSlides + novelPhoenixTrending + novelPhoenixLatest).distinctBy { it.id }
            NovelSourceType.FREE_WEB_NOVEL -> (fwnHeroSlides + fwnTrending).distinctBy { it.id }
            NovelSourceType.ASURA_SCANS -> (asuraHeroSlides + asuraTrending + asuraLatest).distinctBy { it.id }
        }

        if (q.isEmpty()) return allNovels

        return allNovels.filter { novel ->
            novel.title.lowercase().contains(q) ||
                    novel.genres.any { it.lowercase().contains(q) } ||
                    novel.author.lowercase().contains(q) ||
                    novel.synopsis.lowercase().contains(q)
        }
    }

    fun findNovelById(id: String): Novel? {
        val all = (novelPhoenixHeroSlides + novelPhoenixTrending + novelPhoenixLatest +
                fwnHeroSlides + fwnTrending +
                asuraHeroSlides + asuraTrending + asuraLatest)
        return all.find { it.id == id }
    }
}
