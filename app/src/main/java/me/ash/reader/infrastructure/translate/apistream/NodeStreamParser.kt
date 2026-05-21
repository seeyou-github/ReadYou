package me.ash.reader.infrastructure.translate.apistream

/**
 * 节点标记流式解析器。
 *
 * LLM 翻译输出由 `##[id]## ...内容...` 形式的多段构成。把流式收到的文本块
 * 逐字符喂进 [feed]，解析器会按节点解析并在切换到下一节点时回调上一节点。
 * 流结束时调用 [finish] 输出最后一个节点。
 *
 * 该解析器与具体协议（OpenAI / Gemini / Claude）无关，由各 Stream 服务复用。
 */
class NodeStreamParser(
    private val onNodeCompleted: (id: Int, text: String) -> Unit,
) {
    private enum class State {
        NONE, EXPECT_HASH1, EXPECT_HASH2, PARSING_ID,
        EXPECT_HASH_END1, EXPECT_HASH_END2, PARSING_CONTENT,
    }

    private var state: State = State.NONE
    private var currentNodeId: Int? = null
    private val buffer = StringBuilder()
    private val idBuffer = StringBuilder()
    private val completedNodes = mutableSetOf<Int>()

    /** 已完成的节点结果（id -> 翻译文本）；外层可在流结束后读取。 */
    val results: MutableMap<Int, String> = linkedMapOf()

    fun feed(text: String) {
        for (c in text) {
            handle(c)
        }
    }

    private fun emitCurrent() {
        val id = currentNodeId ?: return
        val out = buffer.toString().trim()
        if (out.isBlank()) return
        if (completedNodes.contains(id)) return
        completedNodes.add(id)
        results[id] = out
        onNodeCompleted(id, out)
    }

    fun finish() {
        emitCurrent()
    }

    private fun handle(char: Char) {
        when (state) {
            State.NONE -> when (char) {
                '#' -> state = State.EXPECT_HASH1
                else -> buffer.append(char)
            }

            State.EXPECT_HASH1 -> when (char) {
                '#' -> state = State.EXPECT_HASH2
                else -> {
                    state = State.NONE
                    buffer.append('#').append(char)
                }
            }

            State.EXPECT_HASH2 -> when (char) {
                '[' -> {
                    emitCurrent()
                    buffer.clear()
                    idBuffer.clear()
                    state = State.PARSING_ID
                }
                else -> {
                    state = State.NONE
                    buffer.append("##").append(char)
                }
            }

            State.PARSING_ID -> when {
                char == ']' -> {
                    currentNodeId = idBuffer.toString().toIntOrNull()
                    idBuffer.clear()
                    state = State.EXPECT_HASH_END1
                }
                char.isDigit() || char == '-' -> idBuffer.append(char)
                else -> {
                    state = State.NONE
                    buffer.append("##[").append(idBuffer).append(char)
                    idBuffer.clear()
                }
            }

            State.EXPECT_HASH_END1 -> when (char) {
                '#' -> state = State.EXPECT_HASH_END2
                else -> {
                    state = State.NONE
                    buffer.append("##[${currentNodeId ?: ""}]").append(char)
                }
            }

            State.EXPECT_HASH_END2 -> when (char) {
                '#' -> state = State.PARSING_CONTENT
                else -> {
                    state = State.NONE
                    buffer.append("##[${currentNodeId ?: ""}]#").append(char)
                }
            }

            State.PARSING_CONTENT -> when (char) {
                '#' -> state = State.EXPECT_HASH1
                else -> buffer.append(char)
            }
        }
    }
}

object TranslatePrompt {
    const val SYSTEM = """你是一个专业翻译助手，负责将英文翻译为中文。
重要规则：
1. 必须严格按照输入的段落分隔符（\n\n---\n\n）来划分翻译结果
2. 输入有多少个段落，输出就必须有多少个段落
3. 每个段落必须单独翻译，不能合并多个段落
4. 非英文字符不做处理，保持原样输出
5. 【重要】不要翻译或修改 ##[-1]##、##[0]##、##[1]##、##[2]## 这类节点标记，输出时必须保留此类标记
6. 只输出翻译结果，不要有任何解释或思考过程"""

    fun hasEnglishChars(text: String): Boolean =
        text.any { it in 'a'..'z' || it in 'A'..'Z' }

    /**
     * 构造带 `##[id]##` 节点标记的合并文本与原 index 映射。
     * 返回 Triple(标记文本列表, mergedText, "原 texts 索引 -> 节点 id" 映射)。
     */
    fun buildMarked(
        title: String?,
        texts: List<String>,
    ): Triple<List<String>, String, Map<Int, Int>> {
        val marked = mutableListOf<String>()
        val sourceIndexToNodeId = linkedMapOf<Int, Int>()
        if (title != null && hasEnglishChars(title)) {
            marked.add("##[-1]## $title")
        }
        texts.forEachIndexed { index, text ->
            if (hasEnglishChars(text)) {
                marked.add("##[$index]## $text")
                sourceIndexToNodeId[index] = index
            }
        }
        return Triple(marked, marked.joinToString("\n"), sourceIndexToNodeId)
    }
}
