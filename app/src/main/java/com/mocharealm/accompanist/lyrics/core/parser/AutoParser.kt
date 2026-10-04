package com.mocharealm.accompanist.lyrics.core.parser

import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.utils.PhoneticProvider

/**
 * A smart parser that automatically detects the lyrics format and uses the appropriate parser.
 *
 * This class combines the functionality of all individual parsers (`EnhancedLrcParser`,
 * `TTMLParser`, and `LyricifySyllableParser`) into a single, easy-to-use interface. It uses
 * `LyricsFormatGuesser` to determine the most likely format and then delegates the parsing task.
 *
 * This parser is extensible. You can register custom formats and their corresponding parsers.
 */
class AutoParser(
    private val fallbackPhoneticProvider: PhoneticProvider? = null,
    private val parsers: List<ILyricsParser> = listOf(
        TTMLParser(fallbackPhoneticProvider = fallbackPhoneticProvider),
        // QRC 必须排在 YRC 之前：YRC 的 canParse 只查行头 [num,num] + '('，
        // 二元组 QRC 行同样命中，放后面会导致 QRCParser 永远轮不到
        com.example.lxmusic.ui.lyrics.QRCParser,
        NeteaseYrcParser,
        LyricifySyllableParser,
        EnhancedLrcParser,
        KugouKrcParser,
    )
) : ILyricsParser {

    override fun canParse(content: String): Boolean =
        parsers.any { it.canParse(content) }

    override fun parse(content: String): SyncedLyrics {
        val parser = parsers.firstOrNull { it.canParse(content) }
        return parser?.parse(content) ?: SyncedLyrics(emptyList())
    }
}
