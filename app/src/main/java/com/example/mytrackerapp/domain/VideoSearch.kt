package com.example.mytrackerapp.domain

import java.net.URLEncoder

/**
 * YouTube search for exactly [query] — the exercise name, with no "proper form" style
 * suffix. Uses the (String, String) overload because the Charset one needs API 33.
 */
fun youtubeSearchUrl(query: String): String =
    "https://www.youtube.com/results?search_query=" +
        URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
