<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
import 'highlight.js/styles/github.css'

const props = withDefaults(defineProps<{
  content: string
  maxWidth?: number
}>(), {
  maxWidth: 720,
})

marked.setOptions({
  breaks: false,
  gfm: true,
})

const html = computed(() => {
  if (!props.content) return ''
  return DOMPurify.sanitize(marked.parse(props.content) as string)
})
</script>

<template>
  <div
    class="markdown-viewer body"
    :style="{ maxWidth: maxWidth + 'px' }"
    v-html="html"
  />
</template>

<style scoped>
.markdown-viewer {
  margin: 0 auto;
}

.markdown-viewer :deep(h2) {
  font-family: var(--font-display);
  font-size: 1.5rem;
  font-weight: 600;
  line-height: 1.3;
  margin: var(--spacing-2xl) 0 var(--spacing-md);
}

.markdown-viewer :deep(h3) {
  font-family: var(--font-display);
  font-size: 1.125rem;
  font-weight: 500;
  line-height: 1.4;
  margin: var(--spacing-xl) 0 var(--spacing-sm);
}

.markdown-viewer :deep(p) {
  margin: var(--spacing-sm) 0;
}

.markdown-viewer :deep(ul),
.markdown-viewer :deep(ol) {
  padding-left: var(--spacing-lg);
  margin: var(--spacing-sm) 0;
}

.markdown-viewer :deep(li) {
  margin: var(--spacing-xs) 0;
}

.markdown-viewer :deep(code) {
  font-family: var(--font-mono);
  font-size: 0.875rem;
  background: var(--color-bg);
  padding: 2px 6px;
  border-radius: var(--rounded-sm);
}

.markdown-viewer :deep(pre) {
  background: var(--color-bg);
  border-radius: var(--rounded-sm);
  padding: var(--spacing-md);
  overflow-x: auto;
  margin: var(--spacing-md) 0;
}

.markdown-viewer :deep(pre code) {
  background: none;
  padding: 0;
}

.markdown-viewer :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: var(--spacing-md) 0;
}

.markdown-viewer :deep(th) {
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

.markdown-viewer :deep(td) {
  padding: var(--spacing-sm) var(--spacing-md);
  border-bottom: 1px solid var(--color-border);
}

.markdown-viewer :deep(strong) {
  font-weight: 600;
}

.markdown-viewer :deep(blockquote) {
  border-left: 3px solid var(--color-brand);
  padding-left: var(--spacing-md);
  color: var(--color-text-secondary);
  margin: var(--spacing-md) 0;
}
</style>
