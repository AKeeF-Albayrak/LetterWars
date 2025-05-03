enum class MineType {
    POINT_DIVISION,
    POINT_TRANSFER,
    LETTER_RESET,
    BONUS_CANCEL,
    WORD_CANCEL,
    AREA_BLOCK,       // Bölge Yasağı (Rakibin belirli bir bölgeye harf koyamaması)
    LETTER_FREEZE,    // Harf Yasağı (Rakibin belirli harfleri kullanamaması)
    EXTRA_TURN        // Ekstra Hamle Jokeri (Ekstra bir hamle hakkı kazanılması)
}
