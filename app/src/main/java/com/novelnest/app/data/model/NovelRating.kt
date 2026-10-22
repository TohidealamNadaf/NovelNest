package com.novelnest.app.data.model

import java.util.Locale

/** Zero is the source's unrated/missing sentinel, not a reader score. */
fun Novel.hasRating(): Boolean = rating.isFinite() && rating > 0f && rating <= 5f
fun Novel.ratingLabel(): String = if (hasRating()) String.format(Locale.ROOT, "%.1f", rating) else "—"
