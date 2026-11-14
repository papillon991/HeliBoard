// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only

package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.isEmoji
import java.util.ArrayList

/**
 * NaratgeulCombiner — 나랏글(EZ한글) 3×4 한손 키보드 조합기
 *
 * HeliBoard의 Combiner 인터페이스를 구현하여,
 * 나랏글 특유의 획추가/쌍자음/모음토글 로직을 처리한 뒤
 * 확정된 자모를 한글 음절로 조합한다.
 *
 * HangulCombiner와 동일한 패턴 사용:
 * - 입력 중에는 모든 텍스트를 composing 상태로 유지
 * - 스페이스/엔터 등 확정 트리거 시 한꺼번에 커밋
 */
class NaratgeulCombiner : Combiner {

    // =========================================================================
    //  1. 커스텀 키 코드 상수
    // =========================================================================
    companion object {
        // 자음 키 (6개) — KeyCode.NARATGEUL_* 와 동일 값
        const val KEY_GIYEOK  = -10100  // ㄱ
        const val KEY_NIEUN   = -10101  // ㄴ
        const val KEY_RIEUL   = -10102  // ㄹ
        const val KEY_MIEUM   = -10103  // ㅁ
        const val KEY_SIOT    = -10104  // ㅅ
        const val KEY_IEUNG   = -10105  // ㅇ

        // 모음 키 - 토글형 (2개)
        const val KEY_A_EO    = -10106  // ㅏ/ㅓ
        const val KEY_O_U     = -10107  // ㅗ/ㅜ

        // 모음 키 - 단독형 (2개)
        const val KEY_I       = -10108  // ㅣ
        const val KEY_EU      = -10109  // ㅡ

        // 기능 키 (2개)
        const val KEY_STROKE  = -10110  // 획추가
        const val KEY_SSANG   = -10111  // 쌍자음

        // 자음 키코드 → 기본 자모 매핑
        val CONSONANT_KEY_MAP: Map<Int, Char> = mapOf(
            KEY_GIYEOK to 'ㄱ',
            KEY_NIEUN  to 'ㄴ',
            KEY_RIEUL  to 'ㄹ',
            KEY_MIEUM  to 'ㅁ',
            KEY_SIOT   to 'ㅅ',
            KEY_IEUNG  to 'ㅇ',
        )

        // 모음 키코드 집합
        val VOWEL_KEY_SET: Set<Int> = setOf(KEY_A_EO, KEY_O_U, KEY_I, KEY_EU)

        // 기능 키코드 집합
        val FUNCTION_KEY_SET: Set<Int> = setOf(KEY_STROKE, KEY_SSANG)

        // 나랏글 키코드 전체 범위 확인
        fun isNaratgeulKey(code: Int): Boolean =
            code in -10111..-10100

        // 이벤트 체인 헬퍼: HangulCombiner와 동일 패턴
        private fun createEventChainFromSequence(
            text: CharSequence,
            originalEvent: Event
        ): Event {
            return Event.createSoftwareTextEvent(
                text, KeyCode.MULTIPLE_CODE_POINTS, originalEvent
            )
        }
    }

    // =========================================================================
    //  2. 상태 머신 정의
    // =========================================================================
    enum class State {
        /** 아무것도 조합 중이 아닌 초기 상태 */
        IDLE,
        /** 초성이 입력됨 — 획추가/쌍자음/모음 대기 */
        INITIAL_CONSONANT,
        /** 중성(모음) 입력 중 — 토글/획추가/복합모음/종성 대기 */
        VOWEL_COMPOSING,
        /** 종성이 입력됨 — 획추가/쌍자음 대기, 모음 오면 분리 */
        FINAL_CONSONANT,
    }

    // =========================================================================
    //  3. 자음 획추가 순환 테이블
    // =========================================================================

    /** 자음 획추가 순환: ㄱ↔ㅋ, ㄴ→ㄷ→ㅌ→ㄴ, ... */
    private val CONSONANT_STROKE_CYCLE: Map<Char, List<Char>> = mapOf(
        'ㄱ' to listOf('ㄱ', 'ㅋ'),            // 2단계 순환
        'ㄴ' to listOf('ㄴ', 'ㄷ', 'ㅌ'),      // 3단계 순환
        'ㅁ' to listOf('ㅁ', 'ㅂ', 'ㅍ'),      // 3단계 순환
        'ㅅ' to listOf('ㅅ', 'ㅈ', 'ㅊ'),      // 3단계 순환
        'ㅇ' to listOf('ㅇ', 'ㅎ'),            // 2단계 순환
        'ㄹ' to listOf('ㄹ'),                  // 순환 없음
    )

    // 역방향 룩업: 임의의 파생 자음 → 해당 순환 리스트
    private val consonantToCycle: Map<Char, List<Char>> = buildMap {
        for ((_, cycle) in CONSONANT_STROKE_CYCLE) {
            for (ch in cycle) {
                put(ch, cycle)
            }
        }
    }

    // =========================================================================
    //  4. 쌍자음 순환 테이블
    // =========================================================================

    /** 쌍자음 가능한 자음 쌍: ㄱ↔ㄲ, ㄷ↔ㄸ, ㅂ↔ㅃ, ㅅ↔ㅆ, ㅈ↔ㅉ */
    private val SSANG_CYCLE: Map<Char, Char> = mapOf(
        'ㄱ' to 'ㄲ', 'ㄲ' to 'ㄱ',
        'ㄷ' to 'ㄸ', 'ㄸ' to 'ㄷ',
        'ㅂ' to 'ㅃ', 'ㅃ' to 'ㅂ',
        'ㅅ' to 'ㅆ', 'ㅆ' to 'ㅅ',
        'ㅈ' to 'ㅉ', 'ㅉ' to 'ㅈ',
    )

    // =========================================================================
    //  5. 모음 토글 테이블
    // =========================================================================

    /** 토글 모음 키: 같은 키 반복 시 순환 */
    private val VOWEL_TOGGLE: Map<Int, List<Char>> = mapOf(
        KEY_A_EO to listOf('ㅏ', 'ㅓ'),  // ㅏ↔ㅓ
        KEY_O_U  to listOf('ㅗ', 'ㅜ'),  // ㅗ↔ㅜ
    )

    // =========================================================================
    //  6. 모음 획추가 순환 테이블 (y-계열 변환)
    // =========================================================================

    /** 기본 모음 + 획추가 → y-계열: ㅏ↔ㅑ, ㅓ↔ㅕ, ㅗ↔ㅛ, ㅜ↔ㅠ */
    private val VOWEL_STROKE_CYCLE: Map<Char, List<Char>> = mapOf(
        'ㅏ' to listOf('ㅏ', 'ㅑ'),
        'ㅓ' to listOf('ㅓ', 'ㅕ'),
        'ㅗ' to listOf('ㅗ', 'ㅛ'),
        'ㅜ' to listOf('ㅜ', 'ㅠ'),
    )

    // 역방향 룩업
    private val vowelToStrokeCycle: Map<Char, List<Char>> = buildMap {
        for ((_, cycle) in VOWEL_STROKE_CYCLE) {
            for (ch in cycle) {
                put(ch, cycle)
            }
        }
    }

    // =========================================================================
    //  7. 복합 모음 조합 테이블
    // =========================================================================

    /**
     * (선행모음, 후행모음) → 복합모음
     * 나랏글에서 발생 가능한 모든 복합 모음 조합.
     */
    private val COMPOUND_VOWEL: Map<Pair<Char, Char>, Char> = mapOf(
        // ㅗ 계열
        ('ㅗ' to 'ㅏ') to 'ㅘ',
        ('ㅗ' to 'ㅐ') to 'ㅙ',
        ('ㅗ' to 'ㅣ') to 'ㅚ',
        // ㅜ 계열
        ('ㅜ' to 'ㅓ') to 'ㅝ',
        ('ㅜ' to 'ㅔ') to 'ㅞ',
        ('ㅜ' to 'ㅣ') to 'ㅟ',
        // ㅡ 계열
        ('ㅡ' to 'ㅣ') to 'ㅢ',
        // ㅏ/ㅓ + ㅣ → ㅐ/ㅔ
        ('ㅏ' to 'ㅣ') to 'ㅐ',
        ('ㅓ' to 'ㅣ') to 'ㅔ',
        ('ㅑ' to 'ㅣ') to 'ㅒ',
        ('ㅕ' to 'ㅣ') to 'ㅖ',
        // ㅘ/ㅝ 확장
        ('ㅘ' to 'ㅣ') to 'ㅙ',
        ('ㅝ' to 'ㅣ') to 'ㅞ',
    )

    // =========================================================================
    //  8. 종성 유효성 테이블
    // =========================================================================

    /**
     * 유효한 종성(받침) 집합.
     * ㄸ, ㅃ, ㅉ는 초성 전용이므로 종성 불가.
     */
    private val VALID_JONGSEONG: Set<Char> = setOf(
        // 단일 종성
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅅ', 'ㅆ',
        'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
        // 겹받침
        'ㄳ', 'ㄵ', 'ㄶ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅄ',
    )

    private fun isValidJongseong(jamo: Char): Boolean = jamo in VALID_JONGSEONG

    // =========================================================================
    //  8b. 겹받침(복합 종성) 조합/분해 테이블
    // =========================================================================

    /**
     * 겹받침 조합 테이블: (첫째 자음, 둘째 자음) → 겹받침
     */
    private val COMPOUND_JONGSEONG: Map<Pair<Char, Char>, Char> = mapOf(
        ('ㄱ' to 'ㅅ') to 'ㄳ',
        ('ㄴ' to 'ㅈ') to 'ㄵ',
        ('ㄴ' to 'ㅎ') to 'ㄶ',
        ('ㄹ' to 'ㄱ') to 'ㄺ',
        ('ㄹ' to 'ㅁ') to 'ㄻ',
        ('ㄹ' to 'ㅂ') to 'ㄼ',
        ('ㄹ' to 'ㅅ') to 'ㄽ',
        ('ㄹ' to 'ㅌ') to 'ㄾ',
        ('ㄹ' to 'ㅍ') to 'ㄿ',
        ('ㄹ' to 'ㅎ') to 'ㅀ',
        ('ㅂ' to 'ㅅ') to 'ㅄ',
    )

    /**
     * 겹받침 분해 테이블 (역방향): 겹받침 → (첫째 자음, 둘째 자음)
     */
    private val DECOMPOSE_COMPOUND_JONG: Map<Char, Pair<Char, Char>> = mapOf(
        'ㄳ' to ('ㄱ' to 'ㅅ'),
        'ㄵ' to ('ㄴ' to 'ㅈ'),
        'ㄶ' to ('ㄴ' to 'ㅎ'),
        'ㄺ' to ('ㄹ' to 'ㄱ'),
        'ㄻ' to ('ㄹ' to 'ㅁ'),
        'ㄼ' to ('ㄹ' to 'ㅂ'),
        'ㄽ' to ('ㄹ' to 'ㅅ'),
        'ㄾ' to ('ㄹ' to 'ㅌ'),
        'ㄿ' to ('ㄹ' to 'ㅍ'),
        'ㅀ' to ('ㄹ' to 'ㅎ'),
        'ㅄ' to ('ㅂ' to 'ㅅ'),
    )

    /** 겹받침의 첫째 자음이 될 수 있는 자음 집합 */
    private val COMPOUND_JONG_FIRST_SET: Set<Char> = setOf('ㄱ', 'ㄴ', 'ㄹ', 'ㅂ')

    // =========================================================================
    //  9. 유니코드 한글 조합 상수 및 인덱스 테이블
    // =========================================================================

    /** 한글 음절 시작점 U+AC00 = '가' */
    private val HANGUL_SYLLABLE_BASE = 0xAC00

    /** 초성 19자 */
    private val CHO_TABLE: List<Char> = listOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ',
        'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    )

    /** 중성 21자 */
    private val JUNG_TABLE: List<Char> = listOf(
        'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ',
        'ㅗ', 'ㅘ', 'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ',
        'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ'
    )

    /** 종성 28자: index 0 = 종성 없음 */
    private val JONG_TABLE: List<Char?> = listOf(
        null,
        'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ',
        'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ',
        'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ',
        'ㅌ', 'ㅍ', 'ㅎ'
    )

    // 인덱스 역방향 룩업 맵
    private val choIndex:  Map<Char, Int> = CHO_TABLE.withIndex().associate  { (i, c) -> c to i }
    private val jungIndex: Map<Char, Int> = JUNG_TABLE.withIndex().associate { (i, c) -> c to i }
    private val jongIndex: Map<Char?, Int> = JONG_TABLE.withIndex().associate { (i, c) -> c to i }

    // =========================================================================
    //  10. HangulBuffer — 초성/중성/종성 버퍼 + 유니코드 조합
    // =========================================================================

    /**
     * 한 글자(음절)의 초성·중성·종성을 관리하는 버퍼.
     * 조합이 확정되면 composeSyllable()로 유니코드 한글 음절 문자를 생성한다.
     */
    inner class HangulBuffer {
        var cho:  Char? = null
        var jung: Char? = null
        var jong: Char? = null

        val isEmpty: Boolean get() = cho == null && jung == null && jong == null
        val hasOnlyCho: Boolean get() = cho != null && jung == null && jong == null
        val hasChoJung: Boolean get() = cho != null && jung != null && jong == null
        val hasChoJungJong: Boolean get() = cho != null && jung != null && jong != null

        /**
         * 현재 버퍼를 유니코드 한글 음절로 조합.
         * 공식: ((초성인덱스) × 21 + (중성인덱스)) × 28 + (종성인덱스) + 0xAC00
         */
        fun composeSyllable(): Char? {
            val c = cho ?: return null
            val j = jung ?: return null

            val ci = choIndex[c] ?: return null
            val ji = jungIndex[j] ?: return null
            val ki = if (jong != null) (jongIndex[jong] ?: return null) else 0

            val code = (ci * 21 + ji) * 28 + ki + HANGUL_SYLLABLE_BASE
            return code.toChar()
        }

        /** 현재 버퍼의 표시 문자열 생성 (조합 미완성 시 개별 자모 반환) */
        fun getDisplayChars(): List<Char> {
            if (isEmpty) return emptyList()

            if (cho != null && jung != null) {
                val syllable = composeSyllable()
                if (syllable != null) return listOf(syllable)
            }

            if (hasOnlyCho) return listOf(cho!!)
            if (cho == null && jung != null) return listOf(jung!!)

            return listOfNotNull(cho, jung, jong)
        }

        /** 종성을 분리하여 다음 음절의 초성으로 반환 */
        fun detachJong(): Char? {
            val j = jong ?: return null
            jong = null
            return j
        }

        fun clear() {
            cho = null
            jung = null
            jong = null
        }

        override fun toString(): String =
            "HangulBuffer(cho=${cho ?: '-'}, jung=${jung ?: '-'}, jong=${jong ?: '-'})"
    }

    // =========================================================================
    //  11. 순환 적용 유틸리티 함수
    // =========================================================================

    /** 자음에 획추가를 적용 (순환) */
    private fun applyConsonantStroke(current: Char): Char {
        val cycle = consonantToCycle[current] ?: return current
        val idx = cycle.indexOf(current)
        return cycle[(idx + 1) % cycle.size]
    }

    /** 자음에 쌍자음을 적용 (순환) */
    private fun applySsang(current: Char): Char =
        SSANG_CYCLE[current] ?: current

    /** 모음에 획추가를 적용 (y-계열 순환) */
    private fun applyVowelStroke(current: Char): Char {
        val cycle = vowelToStrokeCycle[current] ?: return current
        val idx = cycle.indexOf(current)
        return cycle[(idx + 1) % cycle.size]
    }

    /** 토글 모음 키를 적용 */
    private fun applyVowelToggle(keyCode: Int, currentVowel: Char?): Char {
        val cycle = VOWEL_TOGGLE[keyCode] ?: return currentVowel ?: ' '
        if (currentVowel != null && currentVowel in cycle) {
            val idx = cycle.indexOf(currentVowel)
            return cycle[(idx + 1) % cycle.size]
        }
        return cycle[0]
    }

    /** 복합 모음 조합을 시도 */
    private fun tryCompoundVowel(first: Char, second: Char): Char? =
        COMPOUND_VOWEL[first to second]

    /**
     * 복합 모음을 한 단계 분해 (백스페이스용).
     * 예: ㅘ→ㅗ, ㅐ→ㅏ, ㅙ→ㅘ, ㅢ→ㅡ, ㅑ→ㅏ
     * @return 분해된 선행 모음, 또는 분해 불가 시 null
     */
    private fun decomposeVowel(vowel: Char): Char? {
        return when (vowel) {
            // 복합 모음 분해
            'ㅘ' -> 'ㅗ'
            'ㅙ' -> 'ㅘ'    // 단계적: ㅙ→ㅘ→ㅗ
            'ㅚ' -> 'ㅗ'
            'ㅝ' -> 'ㅜ'
            'ㅞ' -> 'ㅝ'    // 단계적: ㅞ→ㅝ→ㅜ
            'ㅟ' -> 'ㅜ'
            'ㅢ' -> 'ㅡ'
            'ㅐ' -> 'ㅏ'
            'ㅔ' -> 'ㅓ'
            'ㅒ' -> 'ㅑ'
            'ㅖ' -> 'ㅕ'
            // y-계열 → 기본 모음 (획추가 역순)
            'ㅑ' -> 'ㅏ'
            'ㅕ' -> 'ㅓ'
            'ㅛ' -> 'ㅗ'
            'ㅠ' -> 'ㅜ'
            // 기본 단모음은 분해 불가
            else -> null
        }
    }

    // =========================================================================
    //  12. 인스턴스 상태 필드
    // =========================================================================

    private var state: State = State.IDLE
    private val buffer = HangulBuffer()
    private var lastKeyCode: Int = 0
    private var pendingJamo: Char = '\u0000'

    /**
     * 겹받침 빌딩 중 첫째 자음 추적.
     * null이 아니면 "잠정 겹받침 빌딩" 모드.
     * 예: ㄵ 입력 시 — jong=ㄴ → ㅅ 입력 → compoundJongFirst=ㄴ, jong=ㅅ
     * → 획추가 → ㅅ→ㅈ → compound(ㄴ,ㅈ)=ㄵ → jong=ㄵ, compoundJongFirst=null
     */
    private var compoundJongFirst: Char? = null

    /**
     * 종성에서 분리되어 초성이 된 경우 true.
     * 쌍자음/획추가로 되돌릴 때 이전 음절에 종성으로 재합류할 수 있는지 판단용.
     * 예: '읻' → 쌍 → '이ㄸ'(flag=true) → 쌍 → ㄸ→ㄷ → 재합류 → '읻'
     */
    private var wasSplitFromJong: Boolean = false

    /**
     * 내부적으로 확정된 음절들의 버퍼.
     * HangulCombiner의 composingWord와 동일한 역할:
     * 완성된 음절이 여기에 쌓이고, 스페이스/엔터 시 한꺼번에 커밋된다.
     * combiningStateFeedback에 포함되어 composing text로 표시된다.
     */
    private val composingWord: StringBuilder = StringBuilder()

    // =========================================================================
    //  13. 버퍼 확정(commit) 헬퍼
    // =========================================================================

    /**
     * 현재 buffer의 내용을 composingWord에 추가 (내부 확정).
     * buffer → composingWord 이동 후, buffer는 비워진다.
     * 실제 에디터 커밋은 processEvent에서 일괄 처리.
     */
    private fun commitBuffer() {
        val chars = buffer.getDisplayChars()
        for (c in chars) {
            composingWord.append(c)
        }
        buffer.clear()
    }

    /** 전체 composing 내용이 비어있는지 확인 */
    private fun isComposingEmpty(): Boolean =
        composingWord.isEmpty() && buffer.isEmpty

    // =========================================================================
    //  13b. 겹받침 빌딩 헬퍼
    // =========================================================================

    /**
     * 첫째 자음과 둘째 자음(의 파생형)이 겹받침을 형성할 수 있는지 확인.
     * 둘째 자음의 획추가 순환 체인의 모든 파생형을 검사한다.
     */
    private fun canFormCompoundWithDerived(first: Char, secondBase: Char): Boolean {
        val cycle = consonantToCycle[secondBase] ?: return false
        for (derived in cycle) {
            if (COMPOUND_JONGSEONG.containsKey(first to derived)) return true
        }
        return false
    }

    /**
     * 잠정 겹받침 빌딩 포기.
     * 첫째 자음으로 구성된 음절을 커밋하고,
     * 둘째 자음(buffer.jong)을 새 초성으로 분리한다.
     */
    private fun abandonCompoundBuilding() {
        val second = buffer.jong
        buffer.jong = compoundJongFirst
        compoundJongFirst = null
        commitBuffer()

        if (second != null) {
            buffer.cho = second
            pendingJamo = second
            lastKeyCode = 0
            state = State.INITIAL_CONSONANT
        } else {
            state = State.IDLE
        }
    }

    /**
     * 유니코드 한글 음절을 초성/중성/종성으로 분해.
     * @return Triple(초성, 중성, 종성?) 또는 한글 음절이 아니면 null
     */
    private fun decomposeSyllable(syllable: Char): Triple<Char, Char, Char?>? {
        val code = syllable.code - HANGUL_SYLLABLE_BASE
        if (code < 0 || code >= 11172) return null
        val choIdx = code / (21 * 28)
        val jungIdx = (code % (21 * 28)) / 28
        val jongIdx = code % 28
        val cho = CHO_TABLE.getOrNull(choIdx) ?: return null
        val jung = JUNG_TABLE.getOrNull(jungIdx) ?: return null
        val jong = if (jongIdx == 0) null else JONG_TABLE.getOrNull(jongIdx)
        return Triple(cho, jung, jong)
    }

    /**
     * 종성에서 분리된 초성을 이전 음절에 재합류시키는 시도.
     *
     * composingWord의 마지막 음절을 분해하고, 현재 자음을 종성으로 붙일 수 있으면
     * buffer를 해당 음절로 복원하고 FINAL_CONSONANT로 돌아간다.
     *
     * @param consonant 재합류 대상 자음
     * @return true면 재합류 성공
     */
    private fun tryRejoinWithPreviousSyllable(consonant: Char): Boolean {
        if (!wasSplitFromJong) return false
        if (!isValidJongseong(consonant)) return false
        if (composingWord.isEmpty()) return false

        val lastChar = composingWord[composingWord.lastIndex]
        val decomposed = decomposeSyllable(lastChar) ?: return false
        val (cho, jung, existingJong) = decomposed

        val newJong: Char
        if (existingJong == null) {
            // 단일 종성으로 붙이기 (예: '이' + ㄷ → '읻')
            newJong = consonant
        } else {
            // 겹받침 시도 (예: '갈' + ㄱ → compound(ㄹ,ㄱ) = ㄺ → '갉')
            val compound = COMPOUND_JONGSEONG[existingJong to consonant] ?: return false
            newJong = compound
        }

        // 재합류 실행
        composingWord.deleteCharAt(composingWord.lastIndex)
        buffer.cho = cho
        buffer.jung = jung
        buffer.jong = newJong
        pendingJamo = newJong
        lastKeyCode = 0
        state = State.FINAL_CONSONANT
        wasSplitFromJong = false
        return true
    }

    // =========================================================================
    //  14. handleConsonant — 자음 키 처리
    // =========================================================================

    /**
     * 자음 키(ㄱ,ㄴ,ㄹ,ㅁ,ㅅ,ㅇ) 입력 처리.
     *
     * - IDLE              → 새 초성 시작
     * - INITIAL_CONSONANT → 이전 초성 커밋, 새 초성 시작
     * - VOWEL_COMPOSING   → 초성 있으면 종성으로, 없으면 커밋 후 새 초성
     * - FINAL_CONSONANT   → 현재 음절 커밋, 새 초성 시작
     */
    private fun handleConsonant(jamo: Char) {
        wasSplitFromJong = false
        when (state) {
            State.IDLE -> {
                buffer.cho = jamo
                pendingJamo = jamo
                lastKeyCode = 0
                state = State.INITIAL_CONSONANT
            }

            State.INITIAL_CONSONANT -> {
                commitBuffer()
                buffer.cho = jamo
                pendingJamo = jamo
                lastKeyCode = 0
                // state 유지: INITIAL_CONSONANT
            }

            State.VOWEL_COMPOSING -> {
                if (buffer.cho != null) {
                    if (isValidJongseong(jamo)) {
                        buffer.jong = jamo
                        pendingJamo = jamo
                        lastKeyCode = 0
                        state = State.FINAL_CONSONANT
                    } else {
                        // ㄸ, ㅃ, ㅉ 등 → 커밋 후 새 초성
                        commitBuffer()
                        buffer.cho = jamo
                        pendingJamo = jamo
                        lastKeyCode = 0
                        state = State.INITIAL_CONSONANT
                    }
                } else {
                    commitBuffer()
                    buffer.cho = jamo
                    pendingJamo = jamo
                    lastKeyCode = 0
                    state = State.INITIAL_CONSONANT
                }
            }

            State.FINAL_CONSONANT -> {
                // 잠정 겹받침 빌딩 중이었다면 → 포기 후 새 처리
                if (compoundJongFirst != null) {
                    abandonCompoundBuilding()
                    commitBuffer() // 떨어져 나온 둘째 자음을 확정해야 새 초성에 덮이지 않는다
                    buffer.cho = jamo
                    pendingJamo = jamo
                    lastKeyCode = 0
                    state = State.INITIAL_CONSONANT
                    return
                }

                val currentJong = buffer.jong ?: run {
                    commitBuffer()
                    buffer.cho = jamo
                    pendingJamo = jamo
                    lastKeyCode = 0
                    state = State.INITIAL_CONSONANT
                    return
                }

                // 1) 직접 겹받침 시도
                val compound = COMPOUND_JONGSEONG[currentJong to jamo]
                if (compound != null) {
                    buffer.jong = compound
                    pendingJamo = compound
                    lastKeyCode = 0
                    return
                }

                // 2) 잠정 겹받침 빌딩 시도
                if (currentJong in COMPOUND_JONG_FIRST_SET &&
                    canFormCompoundWithDerived(currentJong, jamo)) {
                    compoundJongFirst = currentJong
                    buffer.jong = jamo
                    pendingJamo = jamo
                    lastKeyCode = 0
                    return
                }

                // 3) 겹받침 불가 → 커밋 후 새 음절
                commitBuffer()
                buffer.cho = jamo
                pendingJamo = jamo
                lastKeyCode = 0
                state = State.INITIAL_CONSONANT
            }
        }
    }

    // =========================================================================
    //  15. processVowel — 모음 공통 처리
    // =========================================================================

    /**
     * 확정된 모음 문자를 받아 상태에 따라 처리.
     * handleVowelToggle과 handleVowelDirect에서 호출.
     */
    private fun processVowel(vowel: Char, keyCode: Int) {
        wasSplitFromJong = false
        when (state) {
            State.IDLE -> {
                buffer.jung = vowel
                pendingJamo = vowel
                lastKeyCode = keyCode
                state = State.VOWEL_COMPOSING
            }

            State.INITIAL_CONSONANT -> {
                buffer.jung = vowel
                pendingJamo = vowel
                lastKeyCode = keyCode
                state = State.VOWEL_COMPOSING
            }

            State.VOWEL_COMPOSING -> {
                val currentJung = buffer.jung!!
                val compound = tryCompoundVowel(currentJung, vowel)

                if (compound != null) {
                    buffer.jung = compound
                    pendingJamo = compound
                    lastKeyCode = keyCode
                } else {
                    commitBuffer()
                    buffer.jung = vowel
                    pendingJamo = vowel
                    lastKeyCode = keyCode
                    state = State.VOWEL_COMPOSING
                }
            }

            State.FINAL_CONSONANT -> {
                // 잠정 겹받침 빌딩 중 → 빌딩 포기, 둘째 자음이 새 초성
                if (compoundJongFirst != null) {
                    val second = buffer.jong
                    buffer.jong = compoundJongFirst
                    compoundJongFirst = null
                    commitBuffer()

                    buffer.cho = second
                    buffer.jung = vowel
                    pendingJamo = vowel
                    lastKeyCode = keyCode
                    state = State.VOWEL_COMPOSING
                    return
                }

                // 겹받침 종성분리
                val currentJong = buffer.jong ?: return
                val decomposed = DECOMPOSE_COMPOUND_JONG[currentJong]
                if (decomposed != null) {
                    val (first, second) = decomposed
                    buffer.jong = first
                    commitBuffer()
                    buffer.cho = second
                    buffer.jung = vowel
                    pendingJamo = vowel
                    lastKeyCode = keyCode
                    state = State.VOWEL_COMPOSING
                    return
                }

                // 단일 종성 분리 (기존 로직)
                val detached = buffer.jong!!
                buffer.jong = null
                commitBuffer()
                buffer.cho = detached
                buffer.jung = vowel
                pendingJamo = vowel
                lastKeyCode = keyCode
                state = State.VOWEL_COMPOSING
            }
        }
    }

    // =========================================================================
    //  16. handleVowelToggle — 토글형 모음 키 (ㅏ/ㅓ, ㅗ/ㅜ)
    // =========================================================================

    private fun handleVowelToggle(keyCode: Int) {
        val toggleList = VOWEL_TOGGLE[keyCode]!!

        // 토글 조건: 중성 조합 중 + 같은 키 반복 + 현재 중성이 토글 리스트 내
        if (state == State.VOWEL_COMPOSING && lastKeyCode == keyCode) {
            val currentJung = buffer.jung
            if (currentJung != null && currentJung in toggleList) {
                val idx = toggleList.indexOf(currentJung)
                val toggled = toggleList[(idx + 1) % toggleList.size]
                buffer.jung = toggled
                pendingJamo = toggled
                return
            }
        }

        // 토글이 아닌 경우 → 첫 모음 결정
        val vowel = selectToggleVowel(toggleList, keyCode)
        processVowel(vowel, keyCode)
    }

    /**
     * 토글 리스트에서 최적의 첫 모음을 선택.
     *
     * VOWEL_COMPOSING 상태에서 기존 중성과 compound 가능한 모음이 있으면
     * 그것을 우선 선택한다. 없으면 리스트의 첫 번째를 반환.
     */
    private fun selectToggleVowel(toggleList: List<Char>, keyCode: Int): Char {
        if (state == State.VOWEL_COMPOSING && buffer.jung != null) {
            val currentJung = buffer.jung!!
            for (v in toggleList) {
                if (tryCompoundVowel(currentJung, v) != null) {
                    return v
                }
            }
        }
        return toggleList[0]
    }

    // =========================================================================
    //  17. handleVowelDirect — 단독형 모음 키 (ㅣ, ㅡ)
    // =========================================================================

    private fun handleVowelDirect(vowel: Char, keyCode: Int) {
        processVowel(vowel, keyCode)
    }

    // =========================================================================
    //  18. handleStrokeAdd — 획추가 키 처리
    // =========================================================================

    /**
     * 획추가 키 처리.
     * - 자음 → 자음 획추가 순환
     * - 모음 → y-계열 순환
     * - 종성 → 유효성 검증 후 획추가 순환
     */
    private fun handleStrokeAdd() {
        when (state) {
            State.IDLE -> { /* 무시 */ }

            State.INITIAL_CONSONANT -> {
                val current = buffer.cho ?: return
                val stroked = applyConsonantStroke(current)
                buffer.cho = stroked
                pendingJamo = stroked
                // 종성에서 분리된 초성이면 재합류 시도
                if (wasSplitFromJong && tryRejoinWithPreviousSyllable(stroked)) return
            }

            State.VOWEL_COMPOSING -> {
                val current = buffer.jung ?: return
                val stroked = applyVowelStroke(current)
                if (stroked != current) {
                    buffer.jung = stroked
                    pendingJamo = stroked
                }
            }

            State.FINAL_CONSONANT -> {
                // === 잠정 겹받침 빌딩 중 ===
                if (compoundJongFirst != null) {
                    val first = compoundJongFirst!!
                    val second = buffer.jong ?: return
                    val strokedSecond = applyConsonantStroke(second)

                    if (strokedSecond == second) return  // 획추가 불가

                    val compound = COMPOUND_JONGSEONG[first to strokedSecond]
                    if (compound != null) {
                        // 겹받침 완성!
                        buffer.jong = compound
                        pendingJamo = compound
                        compoundJongFirst = null
                        return
                    }

                    if (canFormCompoundWithDerived(first, strokedSecond)) {
                        // 계속 빌딩
                        buffer.jong = strokedSecond
                        pendingJamo = strokedSecond
                        return
                    }

                    // 가능성 없음 → 빌딩 포기
                    abandonCompoundBuilding()
                    return
                }

                // === 확정된 겹받침의 둘째 자음에 획추가 ===
                val currentJong = buffer.jong ?: return
                val decomposed = DECOMPOSE_COMPOUND_JONG[currentJong]
                if (decomposed != null) {
                    val (first, second) = decomposed
                    val strokedSecond = applyConsonantStroke(second)
                    if (strokedSecond != second) {
                        val newCompound = COMPOUND_JONGSEONG[first to strokedSecond]
                        if (newCompound != null) {
                            buffer.jong = newCompound
                            pendingJamo = newCompound
                            return
                        }
                        // 새 겹받침 불가 → 분리: 첫째 유지, 획추가된 둘째를 새 초성
                        buffer.jong = first
                        commitBuffer()
                        buffer.cho = strokedSecond
                        pendingJamo = strokedSecond
                        lastKeyCode = 0
                        state = State.INITIAL_CONSONANT
                        wasSplitFromJong = true
                        return
                    }
                    return  // 획추가 불가 → 원래 겹받침 유지
                }

                // === 단일 종성 획추가 (기존 로직) ===
                val stroked = applyConsonantStroke(currentJong)
                if (stroked != currentJong && isValidJongseong(stroked)) {
                    buffer.jong = stroked
                    pendingJamo = stroked
                }
            }
        }
    }

    // =========================================================================
    //  19. handleSsang — 쌍자음 키 처리
    // =========================================================================

    /**
     * 쌍자음 키 처리.
     * 종성에서는 유효성 검증: ㄸ, ㅃ, ㅉ는 종성 불가이므로 거부.
     */
    private fun handleSsang() {
        when (state) {
            State.IDLE -> { /* 무시 */ }

            State.INITIAL_CONSONANT -> {
                val current = buffer.cho ?: return
                val ssanged = applySsang(current)
                if (ssanged != current) {
                    buffer.cho = ssanged
                    pendingJamo = ssanged
                    // 종성에서 분리된 초성이면 재합류 시도
                    if (wasSplitFromJong && tryRejoinWithPreviousSyllable(ssanged)) return
                }
            }

            State.VOWEL_COMPOSING -> { /* 모음에는 적용 불가 */ }

            State.FINAL_CONSONANT -> {
                // 잠정 겹받침 빌딩 중 → 빌딩 포기 후 새 초성에 쌍자음
                if (compoundJongFirst != null) {
                    abandonCompoundBuilding()
                    if (state == State.INITIAL_CONSONANT) {
                        val current = buffer.cho ?: return
                        val ssanged = applySsang(current)
                        if (ssanged != current) {
                            buffer.cho = ssanged
                            pendingJamo = ssanged
                        }
                    }
                    return
                }

                // 확정된 겹받침의 둘째 자음에 쌍자음
                val currentJong = buffer.jong ?: return
                val decomposed = DECOMPOSE_COMPOUND_JONG[currentJong]
                if (decomposed != null) {
                    val (first, second) = decomposed
                    val ssangedSecond = applySsang(second)
                    if (ssangedSecond != second) {
                        val newCompound = COMPOUND_JONGSEONG[first to ssangedSecond]
                        if (newCompound != null) {
                            buffer.jong = newCompound
                            pendingJamo = newCompound
                            return
                        }
                        // 새 겹받침 불가 → 분리: 첫째 유지, 둘째+쌍을 새 초성
                        buffer.jong = first
                        commitBuffer()
                        buffer.cho = ssangedSecond
                        pendingJamo = ssangedSecond
                        lastKeyCode = 0
                        state = State.INITIAL_CONSONANT
                        wasSplitFromJong = true
                        return
                    }
                    return  // 쌍자음 적용 불가 (쌍 대상 아닌 자음) → 무시
                }

                // 단일 종성 쌍자음
                val ssanged = applySsang(currentJong)
                if (ssanged != currentJong) {
                    if (isValidJongseong(ssanged)) {
                        buffer.jong = ssanged
                        pendingJamo = ssanged
                    } else {
                        // ㄸ, ㅃ, ㅉ 등 종성 불가 → 종성 떼고 커밋, 쌍자음을 새 초성
                        buffer.jong = null
                        commitBuffer()
                        buffer.cho = ssanged
                        pendingJamo = ssanged
                        lastKeyCode = 0
                        state = State.INITIAL_CONSONANT
                        wasSplitFromJong = true
                    }
                }
            }
        }
    }

    // =========================================================================
    //  20. handleBackspace — 자모 단위 삭제 (내부 로직)
    // =========================================================================

    /**
     * 백스페이스 키 처리 (buffer 내부).
     *
     * 조합 중인 버퍼에서 마지막 입력 단위를 하나씩 되돌린다:
     * - FINAL_CONSONANT  → 종성 제거 → VOWEL_COMPOSING
     * - VOWEL_COMPOSING  → 복합모음 분해 또는 모음 제거
     * - INITIAL_CONSONANT → 초성 제거 → IDLE
     * - IDLE             → false (외부 처리)
     *
     * @return true면 buffer 내에서 처리됨, false면 buffer가 비어있어 처리 불가
     */
    private fun handleBackspace(): Boolean {
        when (state) {
            State.IDLE -> {
                return false
            }

            State.FINAL_CONSONANT -> {
                // 잠정 겹받침 빌딩 중 → 둘째 자음만 제거
                if (compoundJongFirst != null) {
                    buffer.jong = compoundJongFirst
                    pendingJamo = compoundJongFirst!!
                    compoundJongFirst = null
                    lastKeyCode = 0
                    return true
                }

                // 확정된 겹받침 → 둘째 자음 제거, 첫째만 남김
                val currentJong = buffer.jong
                if (currentJong != null) {
                    val decomposed = DECOMPOSE_COMPOUND_JONG[currentJong]
                    if (decomposed != null) {
                        buffer.jong = decomposed.first
                        pendingJamo = decomposed.first
                        lastKeyCode = 0
                        return true
                    }
                }

                // 단일 종성 제거 (기존 로직)
                buffer.jong = null
                pendingJamo = buffer.jung ?: '\u0000'
                lastKeyCode = 0
                state = State.VOWEL_COMPOSING
            }

            State.VOWEL_COMPOSING -> {
                val currentJung = buffer.jung
                if (currentJung != null) {
                    val decomposed = decomposeVowel(currentJung)
                    if (decomposed != null) {
                        buffer.jung = decomposed
                        pendingJamo = decomposed
                        lastKeyCode = 0
                    } else {
                        buffer.jung = null
                        if (buffer.cho != null) {
                            pendingJamo = buffer.cho!!
                            lastKeyCode = 0
                            state = State.INITIAL_CONSONANT
                        } else {
                            buffer.clear()
                            pendingJamo = '\u0000'
                            lastKeyCode = 0
                            state = State.IDLE
                        }
                    }
                }
            }

            State.INITIAL_CONSONANT -> {
                buffer.cho = null
                buffer.clear()
                pendingJamo = '\u0000'
                lastKeyCode = 0
                state = State.IDLE
            }
        }
        return true
    }

    // =========================================================================
    //  21. dispatch — 키코드 기반 디스패치
    // =========================================================================

    /**
     * 나랏글 키코드를 받아 적절한 핸들러로 라우팅.
     * @return true면 나랏글이 처리함, false면 외부 처리 필요
     */
    fun dispatch(keyCode: Int): Boolean {
        CONSONANT_KEY_MAP[keyCode]?.let { jamo ->
            handleConsonant(jamo)
            return true
        }

        if (keyCode == KEY_A_EO || keyCode == KEY_O_U) {
            handleVowelToggle(keyCode)
            return true
        }

        when (keyCode) {
            KEY_I  -> { handleVowelDirect('ㅣ', KEY_I);  return true }
            KEY_EU -> { handleVowelDirect('ㅡ', KEY_EU); return true }
        }

        when (keyCode) {
            KEY_STROKE -> { handleStrokeAdd(); return true }
            KEY_SSANG  -> { handleSsang();     return true }
        }

        return false
    }

    // =========================================================================
    //  22. Combiner 인터페이스 구현
    // =========================================================================

    /**
     * HeliBoard Combiner.processEvent() 구현.
     *
     * HangulCombiner와 동일한 패턴:
     * - 나랏글 키 → 내부 조합 처리 후 consumed 이벤트 반환
     * - 공백/기능키 → 조합 중인 텍스트 전부 커밋 후 원래 이벤트 전달
     * - 백스페이스 → 자모 단위 삭제
     */
    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event): Event {
        val keyCode = event.keyCode

        // Shift는 조합에 영향 없이 통과
        if (keyCode == KeyCode.SHIFT) return event

        // 이모지는 조합 대상이 아니다. HangulCombiner처럼 그대로 넘기면 조합 중인 글자 앞에 붙으므로 확정 후 넘긴다
        if (isEmoji(event.codePoint)) {
            val text = combiningStateFeedback
            reset()
            return createEventChainFromSequence(text, event)
        }

        // ── 1) 나랏글 키 → 내부 조합 처리 ──
        if (isNaratgeulKey(keyCode)) {
            dispatch(keyCode)
            return Event.createConsumedEvent(event)
        }

        // ── 2) 공백 문자 → 조합 확정 후 통과 ──
        if (Character.isWhitespace(event.codePoint)) {
            val text = combiningStateFeedback
            reset()
            return createEventChainFromSequence(text, event)
        }

        // ── 3) 기능 키 (백스페이스 포함) ──
        if (event.isFunctionalKeyEvent) {
            if (keyCode == KeyCode.DELETE) {
                return handleBackspaceEvent(event)
            }
            // 기타 기능 키 → 조합 확정 후 통과
            val text = combiningStateFeedback
            reset()
            return createEventChainFromSequence(text, event)
        }

        // ── 4) 일반 문자 (영문, 숫자 등) → composingWord에 흡수 (HangulCombiner 패턴) ──
        if (event.codePoint > 0) {
            if (compoundJongFirst != null) {
                // 잠정 빌딩 정리: 첫째 자음을 종성으로 복원, 둘째 자음도 커밋
                val second = buffer.jong
                buffer.jong = compoundJongFirst
                compoundJongFirst = null
                commitBuffer()
                if (second != null) composingWord.append(second)
            } else {
                commitBuffer()
            }
            state = State.IDLE
            pendingJamo = '\u0000'
            lastKeyCode = 0
            composingWord.append(String(Character.toChars(event.codePoint)))
            return Event.createConsumedEvent(event)
        }

        // ── 5) 기타 (알 수 없는 키) → 조합 확정 후 통과 ──
        val text = combiningStateFeedback
        reset()
        if (text.isNotEmpty()) return createEventChainFromSequence(text, event)
            return event

    }

    /** 완성 음절이면 buffer로 되살린다. 낱자·문장부호 등은 false */
    private fun restoreSyllableToBuffer(codePoint: Int): Boolean {
        if (codePoint > 0xFFFF) return false
        val (cho, jung, jong) = decomposeSyllable(codePoint.toChar()) ?: return false
        buffer.cho = cho
        buffer.jung = jung
        buffer.jong = jong
        pendingJamo = jong ?: jung
        lastKeyCode = 0
        wasSplitFromJong = false
        state = if (jong != null) State.FINAL_CONSONANT else State.VOWEL_COMPOSING
        return true
    }

    /**
     * 백스페이스 이벤트 처리. 3단계:
     * 1. buffer에 조합 중인 자모가 있으면 → 자모 단위 삭제
     * 2. composingWord에 확정된 글자가 있으면 → 음절은 되살려 자모 단위 삭제, 그 외는 코드포인트 삭제
     * 3. 둘 다 비었으면 → 이벤트 통과 (에디터의 실제 텍스트 삭제)
     *
     * "마지막 composing 문자 삭제" 시에는 HangulCombiner와 동일하게
     * space 이벤트 트릭으로 composing text를 깨끗이 정리.
     */
    private fun handleBackspaceEvent(event: Event): Event {
        // 1단계: buffer 내 자모 단위 삭제
        if (state != State.IDLE) {
            handleBackspace()
            if (isComposingEmpty()) {
                // 마지막 composing 문자가 삭제됨 → composing text 정리 트릭
                return Event.createHardwareKeypressEvent(
                    0x20, Constants.CODE_SPACE, 0, event, event.isKeyRepeat
                )
            }
            return Event.createConsumedEvent(event)
        }

        // 2단계: composingWord의 마지막 코드포인트 처리. 완성 음절이면 버퍼로 되살려 자모 단위로 계속 지운다
        if (composingWord.isNotEmpty()) {
            val last = composingWord.codePointBefore(composingWord.length)
            composingWord.setLength(composingWord.length - Character.charCount(last))
            if (restoreSyllableToBuffer(last)) handleBackspace()
            if (isComposingEmpty()) {
                // composingWord의 마지막 문자 삭제됨
                return Event.createHardwareKeypressEvent(
                    0x20, Constants.CODE_SPACE, 0, event, event.isKeyRepeat
                )
            }
            return Event.createConsumedEvent(event)
        }

        // 3단계: composing 상태 아님 → 이벤트 통과
        return event
    }

    /**
     * HeliBoard Combiner.combiningStateFeedback 구현.
     *
     * HangulCombiner와 동일 패턴:
     * composingWord(확정된 음절들) + buffer의 현재 표시 문자를 합쳐 반환.
     * 이 전체가 화면에 composing text(밑줄)로 표시된다.
     */
    override val combiningStateFeedback: CharSequence
        get() {
            val sb = StringBuilder(composingWord)

            if (compoundJongFirst != null) {
                // 잠정 빌딩 모드: 첫째 자음만 종성으로 표시 + 둘째 자음 낱자
                val savedJong = buffer.jong
                buffer.jong = compoundJongFirst
                for (c in buffer.getDisplayChars()) sb.append(c)
                buffer.jong = savedJong  // 복원
                if (savedJong != null) sb.append(savedJong)
            } else {
                for (c in buffer.getDisplayChars()) sb.append(c)
            }

            return sb
        }

    /**
     * HeliBoard Combiner.reset() 구현.
     * 커서 이동 등으로 조합 상태를 완전 초기화.
     */
    override fun reset() {
        state = State.IDLE
        buffer.clear()
        lastKeyCode = 0
        pendingJamo = '\u0000'
        compoundJongFirst = null
        wasSplitFromJong = false
        composingWord.setLength(0)
    }
}
