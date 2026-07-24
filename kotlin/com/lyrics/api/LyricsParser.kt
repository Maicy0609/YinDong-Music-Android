package com.lyrics.api

object LyricsParser {

    fun parseLrcLine(line: String): LyricsLine? {
        val regex = Regex("""\[(\d+):(\d+\.?\d*)\](.*)""")
        val match = regex.find(line) ?: return null
        val minutes = match.groupValues[1].toInt()
        val seconds = match.groupValues[2].toFloat()
        val text = match.groupValues[3].trim()
        val timeMs = ((minutes * 60 + seconds) * 1000).toInt()
        return LyricsLine(time = timeMs, text = text)
    }

    fun parseKrcLine(line: String): LyricsLineWithWords? {
        val regex = Regex("""\[(\d+),(\d+)\](.*)""")
        val match = regex.find(line) ?: return null
        val lineStart = match.groupValues[1].toInt()
        val lineDuration = match.groupValues[2].toInt()
        val content = match.groupValues[3]

        val words = mutableListOf<LyricsWord>()
        val fullText = StringBuilder()
        val wordPattern = Regex("""<(\d+),(\d+),(\d+)>([^<]*)""")
        for (m in wordPattern.findAll(content)) {
            val wordStart = m.groupValues[1].toInt()
            val wordDuration = m.groupValues[2].toInt()
            val wordText = m.groupValues[4]
            words.add(LyricsWord(startTime = wordStart, duration = wordDuration, text = wordText))
            fullText.append(wordText)
        }

        return LyricsLineWithWords(
            startTime = lineStart,
            duration = lineDuration,
            text = fullText.toString(),
            words = words
        )
    }

    fun parseYrcLine(line: String): LyricsLineWithWords? {
        val regex = Regex("""\[(\d+),(\d+)\](.*)""")
        val match = regex.find(line) ?: return null
        val lineStart = match.groupValues[1].toInt()
        val lineDuration = match.groupValues[2].toInt()
        val content = match.groupValues[3]

        val words = mutableListOf<LyricsWord>()
        val fullText = StringBuilder()
        val wordPattern = Regex("""\((\d+),(\d+),(\d+)\)([^()]*)""")
        for (m in wordPattern.findAll(content)) {
            val wordStart = m.groupValues[1].toInt()
            val wordDuration = m.groupValues[2].toInt()
            val wordText = m.groupValues[4]
            words.add(LyricsWord(startTime = wordStart, duration = wordDuration, text = wordText))
            fullText.append(wordText)
        }

        return LyricsLineWithWords(
            startTime = lineStart,
            duration = lineDuration,
            text = fullText.toString(),
            words = words
        )
    }

    fun parseLyrics(content: String, formatType: String): LyricsData {
        val lines = content.trim().split("\n")
        var title: String? = null
        var artist: String? = null
        var album: String? = null

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("[ti:") -> title = trimmed.substring(4, trimmed.length - 1).trim()
                trimmed.startsWith("[ar:") -> artist = trimmed.substring(4, trimmed.length - 1).trim()
                trimmed.startsWith("[al:") -> album = trimmed.substring(4, trimmed.length - 1).trim()
            }
        }

        val parsedLines = mutableListOf<LyricsLine>()
        val parsedLinesWithWords = mutableListOf<LyricsLineWithWords>()
        val isWordByWord = formatType in listOf("krc", "yrc", "qrc")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            when (formatType) {
                "krc" -> {
                    val parsed = parseKrcLine(trimmed)
                    if (parsed != null) {
                        parsedLines.add(LyricsLine(time = parsed.startTime, text = parsed.text))
                        parsedLinesWithWords.add(parsed)
                    }
                }
                "yrc" -> {
                    val parsed = parseYrcLine(trimmed)
                    if (parsed != null) {
                        parsedLines.add(LyricsLine(time = parsed.startTime, text = parsed.text))
                        parsedLinesWithWords.add(parsed)
                    }
                }
                else -> {
                    val parsed = parseLrcLine(trimmed)
                    if (parsed != null) {
                        parsedLines.add(parsed)
                    }
                }
            }
        }

        val sortedLines = parsedLines.sortedBy { it.time }
        val sortedLinesWithWords = parsedLinesWithWords.sortedBy { it.startTime }

        return LyricsData(
            format = formatType,
            isWordByWord = isWordByWord && sortedLinesWithWords.isNotEmpty(),
            title = title,
            artist = artist,
            album = album,
            lines = sortedLines,
            linesWithWords = if (sortedLinesWithWords.isNotEmpty()) sortedLinesWithWords else null,
            rawContent = content
        )
    }
}
