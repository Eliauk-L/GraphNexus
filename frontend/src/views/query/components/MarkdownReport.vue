<script setup lang="ts">
import MarkdownViewer from '@/common/components/MarkdownViewer.vue'

defineProps<{
  content: string
}>()
</script>

<template>
  <div class="markdown-report">
    <MarkdownViewer :content="content" />
  </div>
</template>

<style scoped>
.markdown-report {
  padding: var(--spacing-lg) 0;
}
</style>