<script setup lang="ts">
import MarkdownViewer from '@/common/components/MarkdownViewer.vue'
import HtmlSvgViewer from '@/common/components/HtmlSvgViewer.vue'

const props = defineProps<{
  content: string
  outputFormat?: string
}>()
</script>

<template>
  <div v-if="content" class="markdown-report">
    <HtmlSvgViewer v-if="props.outputFormat === 'html-svg'" :content="content" />
    <MarkdownViewer v-else :content="content" />
  </div>
</template>

<style scoped>
.markdown-report {
  padding: var(--spacing-lg) 0;
}
</style>