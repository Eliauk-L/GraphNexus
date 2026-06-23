<script setup lang="ts">
import { computed } from 'vue'
import DOMPurify from 'dompurify'

const props = withDefaults(defineProps<{
  content: string
  maxWidth?: number
}>(), {
  maxWidth: 720,
})

// ── DOMPurify 白名单配置（组件内常量，不暴露为 prop）──
const ALLOWED_TAGS = [
  // HTML 结构
  'h2', 'h3', 'h4', 'p', 'div', 'span', 'table', 'thead', 'tbody', 'tr', 'th', 'td',
  'ul', 'ol', 'li', 'strong', 'em', 'br', 'hr', 'a',
  // SVG 图形
  'svg', 'g', 'circle', 'ellipse', 'rect', 'line', 'path', 'polygon', 'polyline',
  'text', 'tspan', 'defs', 'linearGradient', 'stop', 'title', 'desc',
  // MathML
  'math', 'mi', 'mo', 'mn', 'mrow', 'msup', 'mfrac', 'msqrt', 'mroot',
]

const ALLOWED_ATTR = [
  'class', 'id', 'style',
  // SVG 属性
  'd', 'cx', 'cy', 'r', 'x', 'y', 'x1', 'y1', 'x2', 'y2',
  'width', 'height', 'viewBox', 'xmlns',
  'fill', 'stroke', 'stroke-width', 'stroke-linecap',
  'text-anchor', 'dominant-baseline', 'font-size', 'font-family', 'font-weight',
  'transform', 'points', 'rx', 'ry',
  'offset', 'stop-color', 'marker-end',
  'text-decoration', 'href', 'textLength', 'lengthAdjust',
  // HTML 属性
  'href', 'target', 'rel', 'colspan', 'rowspan',
]

const FORBID_TAGS = ['script', 'foreignObject', 'iframe', 'object', 'embed', 'use']
const FORBID_ATTR = ['onclick', 'onload', 'onerror', 'onmouseover', 'onfocus', 'xlink:href']

const purifyConfig: DOMPurify.Config = {
  ALLOWED_TAGS,
  ALLOWED_ATTR,
  FORBID_TAGS,
  FORBID_ATTR,
}

const sanitizedHtml = computed(() => {
  if (!props.content || props.content.length < 20) return ''
  return DOMPurify.sanitize(props.content, purifyConfig) as string
})
</script>

<template>
  <div
    class="html-svg-viewer body"
    :style="{ maxWidth: maxWidth + 'px' }"
    v-html="sanitizedHtml"
  />
</template>

<style scoped>
.html-svg-viewer {
  margin: 0 auto;
}

/* ── 排版层次 :deep() 规则 — 对标 MarkdownViewer ── */
.html-svg-viewer :deep(h2) {
  font-family: var(--font-display);
  font-size: 1.5rem;
  font-weight: 600;
  line-height: 1.3;
  margin: var(--spacing-2xl) 0 var(--spacing-md);
}

.html-svg-viewer :deep(h3) {
  font-family: var(--font-display);
  font-size: 1.125rem;
  font-weight: 500;
  line-height: 1.4;
  margin: var(--spacing-xl) 0 var(--spacing-sm);
}

.html-svg-viewer :deep(p) {
  margin: var(--spacing-sm) 0;
}

.html-svg-viewer :deep(ul),
.html-svg-viewer :deep(ol) {
  padding-left: var(--spacing-lg);
  margin: var(--spacing-sm) 0;
}

.html-svg-viewer :deep(li) {
  margin: var(--spacing-xs) 0;
}

.html-svg-viewer :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: var(--spacing-md) 0;
}

.html-svg-viewer :deep(th) {
  font-family: var(--font-display);
  font-size: 0.6875rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-secondary);
  text-align: left;
  padding: var(--spacing-sm) var(--spacing-md);
  border-bottom: 1px solid var(--color-border);
}

.html-svg-viewer :deep(td) {
  padding: var(--spacing-sm) var(--spacing-md);
  border-bottom: 1px solid var(--color-border);
}

.html-svg-viewer :deep(strong) {
  font-weight: 600;
}

.html-svg-viewer :deep(svg) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: var(--spacing-md) 0;
  overflow: visible;
}

.html-svg-viewer :deep(svg text) {
  /* 防止 SVG 文字被 viewBox 裁剪 */
  overflow: visible;
}

.html-svg-viewer :deep(code) {
  font-family: var(--font-mono);
  font-size: 0.875rem;
  background: var(--color-bg);
  padding: 2px 6px;
  border-radius: var(--rounded-sm);
}

.html-svg-viewer :deep(pre) {
  background: var(--color-bg);
  border-radius: var(--rounded-sm);
  padding: var(--spacing-md);
  overflow-x: auto;
  margin: var(--spacing-md) 0;
}

.html-svg-viewer :deep(blockquote) {
  border-left: 3px solid var(--color-brand);
  padding-left: var(--spacing-md);
  color: var(--color-text-secondary);
  margin: var(--spacing-md) 0;
}
</style>