package me.ash.reader.infrastructure.translate.webbiew

/**
 * 翻译相关 JavaScript 脚本（极简版）
 *
 * 核心原则：
 * - 直接操作 DOM 文本内容，不创建额外元素
 * - 原文保存在 data-original-text 属性
 * - 译文保存在 data-translated-text 属性
 * - 切换时直接替换 textContent
 */
object TranslateScripts {

    /**
     * 脚本 1：标记 DOM 文本节点
     *
     * 功能：
     * - 遍历所有文本节点
     * - 为可翻译文本添加 data-tid 标记
     * - 保存原文到 data-original-text
     */
    val MARK_TEXT_NODES = """
        (function() {
            function hasEnglishChars(text) {
                return /[a-zA-Z]/.test(text);
            }
            
            const EXCLUDED_TAGS = ['SCRIPT', 'STYLE', 'CODE', 'PRE', 'NOSCRIPT', 'TEMPLATE', 'BR-SPAN', 'BR-FIXATION', 'BR-BOLD', 'BR-EDGE'];
            
            function isInExcludedTag(node) {
                let parent = node.parentElement;
                while (parent) {
                    if (EXCLUDED_TAGS.includes(parent.tagName)) {
                        return true;
                    }
                    parent = parent.parentElement;
                }
                return false;
            }
            
            let id = 0;
            const walker = document.createTreeWalker(
                document.body,
                NodeFilter.SHOW_TEXT,
                null,
                false
            );
            
            const nodesToReplace = [];
            let node;
            while (node = walker.nextNode()) {
                if (isInExcludedTag(node)) {
                    continue;
                }
                
                const text = node.textContent.trim();
                if (text.length > 1 && !/^[\s\p{P}\p{S}]+$/u.test(text) && hasEnglishChars(text)) {
                    nodesToReplace.push({
                        node: node,
                        text: node.textContent
                    });
                }
            }
            
            nodesToReplace.forEach(item => {
                const span = document.createElement('span');
                span.dataset.tid = id++;
                span.dataset.originalText = item.node.textContent;
                span.className = 'translatable-text';
                span.textContent = item.node.textContent;
                item.node.parentNode.replaceChild(span, item.node);
            });
            
            if (window.JavaScriptInterface) {
                const markedElements = document.querySelectorAll('[data-tid]');
                JavaScriptInterface.onLog('[MARK_TEXT_NODES] 页面上共有 ' + markedElements.length + ' 个带 data-tid 的元素');
            }
            
            return id;
        })();
    """

    /**
     * 脚本 2：提取文本节点
     */
    val EXTRACT_TEXT_NODES = """
        (function() {
            const nodes = [];
            document.querySelectorAll('[data-tid]').forEach(el => {
                const text = el.textContent.trim();
                if (text.length > 0) {
                    nodes.push({
                        id: parseInt(el.dataset.tid),
                        text: text,
                        priority: el.dataset.priority || 'normal'
                    });
                }
            });
            
            if (window.JavaScriptInterface) {
                JavaScriptInterface.onLog('[EXTRACT_TEXT_NODES] 提取了 ' + nodes.length + ' 个节点');
            }
            
            return JSON.stringify(nodes);
        })();
    """

    /**
     * 脚本 3：更新翻译结果（极简版）
     *
     * 功能：
     * - 直接替换文本内容
     * - 原文保存在 data-original-text（标记时已保存）
     * - 译文保存在 data-translated-text
     * - 使用 Base64 传递 JSON 以正确处理 Unicode
     */
    val UPDATE_TRANSLATIONS = """
        (function(base64Json) {
            try {
                if (!base64Json) {
                    console.log('[UPDATE_TRANSLATIONS] 错误: base64Json 为空');
                    return 0;
                }
                
                // 使用 decodeURIComponent(atob(...)) 正确解码 UTF-8 Base64
                const jsonStr = decodeURIComponent(atob(base64Json));
                const translations = JSON.parse(jsonStr);
                
                if (!Array.isArray(translations)) {
                    console.log('[UPDATE_TRANSLATIONS] 错误: translations 不是数组');
                    return 0;
                }
                
                let count = 0;
                
                translations.forEach(function(item) {
                    // 跳过标题（id = -1）
                    if (item.id === -1) {
                        return;
                    }

                    const el = document.querySelector('[data-tid="' + item.id + '"]');
                    if (el) {
                        // 首次翻译时确保原文已保存
                        if (!el.dataset.originalText) {
                            el.dataset.originalText = el.textContent.trim();
                        }
                        
                        // 保存译文并直接显示
                        el.dataset.translatedText = item.text;
                        el.textContent = item.text;
                        count++;
                    }
                });

                return count;
            } catch (e) {
                console.log('[UPDATE_TRANSLATIONS] 错误: ' + e.message);
                return 0;
            }
        })();
    """

    /**
     * 脚本 4：获取滚动位置
     */
    val GET_SCROLL_POSITION = """
        (function() {
            return window.scrollY || document.documentElement.scrollTop || document.body.scrollTop;
        })();
    """

    /**
     * 脚本 5：设置滚动位置
     */
    val SET_SCROLL_POSITION = """
        (function(scrollY) {
            window.scrollTo(0, scrollY);
        });
    """

    /**
     * 脚本 6：切换显示原文/译文（极简版）
     *
     * 功能：
     * - showTranslation = true: 显示译文
     * - showTranslation = false: 显示原文
     */
    val TOGGLE_TRANSLATION_DISPLAY = """
        (function(showTranslation) {
            let count = 0;
            document.documentElement.setAttribute('data-showing-translation', showTranslation ? 'true' : 'false');
            document.documentElement.setAttribute('data-translation-mode', showTranslation ? 'on' : 'off');
            
            document.querySelectorAll('[data-tid]').forEach(el => {
                if (el.dataset.translatedText) {
                    el.textContent = showTranslation 
                        ? el.dataset.translatedText 
                        : el.dataset.originalText;
                    count++;
                }
            });
            
            return count;
        })
    """

    /**
     * 脚本 7：获取完整HTML内容（用于缓存）
     */
    val GET_FULL_HTML_CONTENT = """
        (function() {
            const isShowingTranslation = document.documentElement.getAttribute('data-showing-translation') === 'true';
            document.documentElement.setAttribute('data-showing-translation', isShowingTranslation);
            document.documentElement.setAttribute('data-translation-mode', isShowingTranslation ? 'on' : 'off');
            return document.documentElement.outerHTML;
        })();
    """

    /**
     * 脚本 8：检查DOM是否包含译文（极简版）
     */
    val CHECK_HAS_TRANSLATION = """
        (function() {
            const translatedElements = document.querySelectorAll('[data-tid][data-translated-text]');
            return {
                hasTranslation: translatedElements.length > 0,
                translatedCount: translatedElements.length
            };
        })();
    """

    /**
     * 脚本 8.5：设置DOM翻译标志位【废弃，不使用标志位】
     */
    val SET_HAS_TRANSLATION = """
        (function(has) {
            if (has) {
                document.documentElement.setAttribute('data-has-translation', 'true');
            } else {
                document.documentElement.removeAttribute('data-has-translation');
            }
            return has;
        });
    """

    /**
     * 脚本 8.6：获取DOM翻译标志位【废弃，不使用标志位】
     */
    val GET_HAS_TRANSLATION = """
        (function() {
            return document.documentElement.getAttribute('data-has-translation') === 'true';
        })();
    """

    /**
     * 脚本 9：恢复DOM（加载缓存时使用）
     */
    val RESTORE_FROM_CACHE = """
        (function() {
            const showingTranslation = document.documentElement.getAttribute('data-showing-translation') === 'true';
            document.documentElement.setAttribute('data-translation-mode', showingTranslation ? 'on' : 'off');
            
            if (showingTranslation) {
                // 恢复显示译文
                document.querySelectorAll('[data-tid][data-translated-text]').forEach(el => {
                    el.textContent = el.dataset.translatedText;
                });
            } else {
                // 恢复显示原文
                document.querySelectorAll('[data-tid][data-translated-text]').forEach(el => {
                    el.textContent = el.dataset.originalText;
                });
            }
            
            return showingTranslation;
        })();
    """

    /**
     * 脚本 10：清除所有翻译（极简版）
     *
     * 功能：
     * - 恢复原文
     * - 清除译文标记
     */
    val CLEAR_ALL_TRANSLATIONS = """
        (function() {
            let count = 0;
            
            document.querySelectorAll('[data-tid]').forEach(el => {
                if (el.dataset.originalText) {
                    el.textContent = el.dataset.originalText;
                    el.removeAttribute('data-translated-text');
                    count++;
                }
            });
            
            return count;
        })();
    """

    /**
     * 获取翻译统计
     */
    val GET_TRANSLATION_STATS = """
        (function() {
            const translatedElements = document.querySelectorAll('[data-tid][data-translated-text]');
            const totalElements = document.querySelectorAll('[data-tid]').length;
            return JSON.stringify({
                total: totalElements,
                translated: translatedElements.length,
                untranslated: totalElements - translatedElements.length
            });
        })();
    """

    /**
     * 批量更新翻译结果（极简版）
     */
    val UPDATE_TRANSLATIONS_BATCH = """
        (function(translationsJson) {
            const translations = JSON.parse(translationsJson);
            let count = 0;
            
            translations.forEach(function(item) {
                const el = document.querySelector('[data-tid="' + item.id + '"]');
                if (el) {
                    if (!el.dataset.originalText) {
                        el.dataset.originalText = el.textContent.trim();
                    }
                    el.dataset.translatedText = item.text;
                    el.textContent = item.text;
                    count++;
                }
            });
            
            return count;
        });
    """
}
