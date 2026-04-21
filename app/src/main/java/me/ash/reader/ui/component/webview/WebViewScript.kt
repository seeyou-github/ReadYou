package me.ash.reader.ui.component.webview

object WebViewScript {

    fun get(boldCharacters: Boolean) = """
const BR_WORD_STEM_PERCENTAGE = 0.7;
const MAX_FIXATION_PARTS = 4;
const FIXATION_LOWER_BOUND = 0
function highlightText(sentenceText) {
	return sentenceText.replace(/\p{L}+/gu, (word) => {
		const { length } = word;

		const brWordStemWidth = length > 3 ? Math.round(length * BR_WORD_STEM_PERCENTAGE) : length;

		const firstHalf = word.slice(0, brWordStemWidth);
		const secondHalf = word.slice(brWordStemWidth);
		var htmlWord = "<br-bold>";
        htmlWord += makeFixations(firstHalf);
        htmlWord += "</br-bold>";
        if (secondHalf.length) {
            htmlWord += "<br-edge>";
            htmlWord += makeFixations(secondHalf);
            htmlWord += "</br-edge>";
        }
		return htmlWord;
	});
}

function makeFixations(textContent) {
	const COMPUTED_MAX_FIXATION_PARTS = textContent.length >= MAX_FIXATION_PARTS ? MAX_FIXATION_PARTS : textContent.length;

	const fixationWidth = Math.ceil(textContent.length * (1 / COMPUTED_MAX_FIXATION_PARTS));

	if (fixationWidth === FIXATION_LOWER_BOUND) {
		return '<br-fixation fixation-strength="1">' + textContent + '</br-fixation>';
	}

	const fixationsSplits = new Array(COMPUTED_MAX_FIXATION_PARTS).fill(null).map((item, index) => {
		const wordStartBoundary = index * fixationWidth;
		const wordEndBoundary = wordStartBoundary + fixationWidth > textContent.length ? textContent.length : wordStartBoundary + fixationWidth;

		return `<br-fixation fixation-strength="` + (index + 1) + `">` + textContent.slice(wordStartBoundary, wordEndBoundary) + `</br-fixation>`;
	});

	return fixationsSplits.join('');
}

const IGNORE_NODE_TAGS = ['STYLE', 'SCRIPT', 'BR-SPAN', 'BR-FIXATION', 'BR-BOLD', 'BR-EDGE', 'SVG', 'INPUT', 'TEXTAREA'];
function parseNode(node) {
    if (!node?.parentElement?.tagName || IGNORE_NODE_TAGS.includes(node.parentElement.tagName)) {
        return;
    }
    
    if (node.nodeType === Node.TEXT_NODE && node.nodeValue.length) {
        try {
            const brSpan = document.createElement('br-span');
            brSpan.innerHTML = highlightText(node.nodeValue);
            if (brSpan.childElementCount === 0) return;
            node.parentElement.replaceChild(brSpan, node); // JiffyReader keeps the old element around, but we don't need it
        } catch (e) {
            console.error('Error parsing text node:', e);
        }
        return;
    }
    
    if (node.hasChildNodes()) [...node.childNodes].forEach(parseNode);
}

function setBold(enabled) {
    if (enabled) {
        document.body.setAttribute("br-mode", "on");
        [...document.body.childNodes].forEach(parseNode);
    } else {
        document.body.setAttribute("br-mode", "off");
    }
}

${if (boldCharacters) "setBold(true);" else ""}

var images = document.querySelectorAll("img");

images.forEach(function(img) {
    img.onload = function() {
        img.classList.add("loaded");
        console.log("Image width:", img.width, "px");
        if (img.width < 412) {
            img.classList.add("thin");
        }
    };

    img.onerror = function() {
        console.error("Failed to load image:", img.src);
    };
});

function mediaAspectRatio(media) {
    const width = Number.parseFloat(media.getAttribute("width"));
    const height = Number.parseFloat(media.getAttribute("height"));

    if (Number.isFinite(width) && Number.isFinite(height) && width > 0 && height > 0) {
        return width / height;
    }

    if (media.tagName === "VIDEO" && media.videoWidth > 0 && media.videoHeight > 0) {
        return media.videoWidth / media.videoHeight;
    }

    return 16 / 9;
}

function mediaIntrinsicSize(media) {
    const attrWidth = Number.parseFloat(media.getAttribute("width"));
    const attrHeight = Number.parseFloat(media.getAttribute("height"));

    if (Number.isFinite(attrWidth) && Number.isFinite(attrHeight) && attrWidth > 0 && attrHeight > 0) {
        return { width: attrWidth, height: attrHeight };
    }

    if (media.tagName === "VIDEO" && media.videoWidth > 0 && media.videoHeight > 0) {
        return { width: media.videoWidth, height: media.videoHeight };
    }

    if (media.clientWidth > 0 && media.clientHeight > 0) {
        return { width: media.clientWidth, height: media.clientHeight };
    }

    return { width: 16, height: 9 };
}

function reportMediaAspectRatio(media) {
    try {
        const size = mediaIntrinsicSize(media);
        JavaScriptInterface.onMediaAspectRatioChanged(size.width, size.height);
    } catch (e) {
        console.error("Failed to report media aspect ratio:", e);
    }
}

function resizeEmbeddedMedia() {
    let largestMedia = null;
    let largestArea = 0;
    document.querySelectorAll("iframe, embed, object, video").forEach(function(media) {
        const ratio = mediaAspectRatio(media);
        media.style.aspectRatio = ratio;
        media.style.width = "100%";
        media.style.maxWidth = "100%";

        if (media.tagName !== "VIDEO" || media.videoWidth === 0 || media.videoHeight === 0) {
            media.style.height = (media.clientWidth / ratio) + "px";
        }

        const area = media.clientWidth * media.clientHeight;
        if (area > largestArea) {
            largestArea = area;
            largestMedia = media;
        }
    });

    if (largestMedia !== null) {
        reportMediaAspectRatio(largestMedia);
    }
}

resizeEmbeddedMedia();
window.addEventListener("resize", resizeEmbeddedMedia);
document.querySelectorAll("iframe, embed, object, video").forEach(function(media) {
    media.addEventListener("pointerdown", function() {
        reportMediaAspectRatio(media);
    });
    media.addEventListener("click", function() {
        reportMediaAspectRatio(media);
    });
});
document.querySelectorAll("video").forEach(function(video) {
    video.addEventListener("loadedmetadata", function() {
        reportMediaAspectRatio(video);
        resizeEmbeddedMedia();
    });
});
"""
}
