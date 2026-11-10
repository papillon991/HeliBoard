# Naratgeul (나랏글) layout

A one-handed Korean input method added by this fork. It types all of Hangul with 12 keys on a 3×4 grid. Choose **Korean (Naratgeul)** in the keyboard language list.

## Layout

```
 ?   ㄱ   ㄴ   ㅏㅓ   ⌫
 (   ㄹ   ㅁ   ㅗㅜ   Space
 🌐  ㅅ   ㅇ   ㅣ    Enter
!#1  획   ㅡ   쌍    .
```

- All five columns are 20% wide. Touch logs of one-handed right thumb typing showed taps drifting toward the middle, so the rarely used left column gave up width to the right column (backspace, space, enter).
- Consonant keys: ㄱ ㄴ ㄹ ㅁ ㅅ ㅇ
- Vowel keys: ㅏㅓ, ㅗㅜ (pressing the same key again toggles ㅏ↔ㅓ, ㅗ↔ㅜ), ㅣ, ㅡ
- **획** (stroke): adds a stroke to the previous letter. **쌍** (double): turns it into a double consonant.
- Long press: consonant and vowel keys give the digits 1-9 and 0, 획 gives `` ` `` and `-`, 쌍 gives `#` and `:`, `?` gives `'`, `(` gives `)`, `.` gives `,`.

## Input rules

| Action | Cycle |
|---|---|
| consonant + 획 | ㄱ→ㅋ, ㄴ→ㄷ→ㅌ, ㅁ→ㅂ→ㅍ, ㅅ→ㅈ→ㅊ, ㅇ→ㅎ (none for ㄹ) |
| consonant + 쌍 | ㄱ↔ㄲ, ㄷ↔ㄸ, ㅂ↔ㅃ, ㅅ↔ㅆ, ㅈ↔ㅉ |
| vowel + 획 | ㅏ→ㅑ, ㅓ→ㅕ, ㅗ→ㅛ, ㅜ→ㅠ |
| compound vowels | ㅏ+ㅣ=ㅐ, ㅓ+ㅣ=ㅔ, ㅗ+ㅣ=ㅚ, ㅜ+ㅣ=ㅟ, ㅡ+ㅣ=ㅢ, ㅗ+ㅏ=ㅘ, ㅜ+ㅓ=ㅝ … |
| compound final consonants | ㄱㅅ ㄴㅈ ㄴㅎ ㄹㄱ ㄹㅁ ㄹㅂ ㄹㅅ ㄹㅌ ㄹㅍ ㄹㅎ ㅂㅅ (press 획 on the second consonant to complete it, e.g. ㄴ+ㅅ→획→ㄴㅈ=ㄵ) |

- A vowel typed after a final consonant moves that consonant to the next syllable as its initial.
- If a final consonant becomes one that cannot be a final (ㄸ ㅃ ㅉ), it detaches from the previous syllable and becomes an initial. Pressing 쌍/획 again to get back to a valid final reattaches it. Example: `읻` → 쌍 → `이ㄸ` → 쌍 → `읻`
- Backspace deletes the word being composed one jamo at a time (`한글` → `한그` → `한ㄱ` → `한` → `하` → `ㅎ`). Syllables already committed with Space or Enter are deleted by the editor, one syllable at a time.
- Everything being typed stays as composing text and is committed at once on Space, Enter, a function key, or an emoji.
- Moving the cursor into the composing word and then pressing any key but shift commits the word and continues at the cursor (`사과`, ㄴㅏ typed at `사|과` gives `사나과`). If the cursor is moved back to the end of the word, the earlier syllable is already finished and gets no final consonant.

## Implementation

| File | Description |
|---|---|
| `app/src/main/java/helium314/keyboard/event/NaratgeulCombiner.kt` (new) | Composing engine. A state machine `IDLE → INITIAL_CONSONANT → VOWEL_COMPOSING → FINAL_CONSONANT` handles consonants, vowels, 획, 쌍 and deletion, and builds Unicode syllables |
| `app/src/main/assets/layouts/main/naratgeul.json` (new) | Layout definition, uses key codes -10100 to -10111 |
| `event/CombinerChain.kt` | Registers NaratgeulCombiner when `CombiningRules=naratgeul` |
| `keyboard/internal/keyboard_parser/KeyboardParser.kt` | Does not add the function key row, symbol popups or number popups to the Naratgeul alphabet layout. The number row shows up if enabled in the settings |
| `keyboard/internal/keyboard_parser/floris/KeyCode.kt` | `NARATGEUL_*` key code constants |
| `latin/utils/SubtypeUtilsAdditional.kt`, `res/xml/method.xml`, `res/values/strings.xml` | Subtype registration and names. The combining rule follows the main layout |
| `latin/inputlogic/InputLogic.java` | Ignores Naratgeul key codes that arrive right after a language switch (avoids a debug build crash), and ends composing on key input at a cursor moved into the word |

## Touch log

Turning on **Touch log (Naratgeul)** in the advanced settings records touches for tuning the layout. Off by default.

- What is recorded: key presses on the Naratgeul alphabet layout. Other layouts, password fields and incognito mode are excluded. Popup keys picked by long press are not recorded.
- File: `no_backup/touch_log.tsv` in the app's internal storage. It is excluded from backups, never sent anywhere, and stops growing at 5 MB. The pressed keys can reveal what you typed, so turn it off when you are done: that deletes the file.
- Columns: time, key code, label, press position, release position, press duration, key position and size

```
adb exec-out run-as helium314.keyboard.debug cat no_backup/touch_log.tsv > touch_log.tsv
adb shell run-as helium314.keyboard.debug rm no_backup/touch_log.tsv
```

For a work profile, find its user id with `adb shell pm list users` and add `--user <id>` after the package name.

## Tests

`InputLogicTest.naratgeul*` and `naratgeul*` in `ParserTest`. Needs JDK 21 because of Robolectric SDK 36.

```
JAVA_HOME=<jdk21> ANDROID_HOME=<sdk> ./gradlew testDebugUnitTest --tests '*naratgeul*'
```

## Other differences in this fork

- **Language key long-press keyboard** (Preferences): when set, a short press on the language key only cycles HeliBoard's own languages and a long press switches straight to the chosen keyboard. It never opens the system keyboard picker, which crashes system_server on Samsung devices without the Samsung keyboard.
- The app is named HeliBoard, but the package is `helium314.keyboard.debug` and it is signed with a fixed key. It can be installed alongside the official HeliBoard.
- Pushing to `main` makes GitHub Actions build the APK and publish it to [Releases](../../releases).
- Upstream: https://github.com/Helium314/HeliBoard
