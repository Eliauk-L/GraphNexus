<script setup lang="ts">
import { ref } from 'vue'
import { Send } from '@lucide/vue'
import BaseButton from '@/common/components/BaseButton.vue'

const props = defineProps<{
  disabled?: boolean
}>()

const emit = defineEmits<{
  send: [question: string]
}>()

const question = ref('')

function handleSend() {
  const q = question.value.trim()
  if (!q || props.disabled) return
  emit('send', q)
  question.value = ''
}
</script>

<template>
  <div class="chat-input">
    <textarea
      v-model="question"
      class="chat-input__textarea body"
      :disabled="disabled"
      placeholder="输入你的问题，如「分析一下张三最近数学怎么样」"
      rows="1"
      @input="(e) => { const t = e.target as HTMLTextAreaElement; t.style.height = 'auto'; t.style.height = Math.min(t.scrollHeight, 120) + 'px' }"
      @keydown.enter.exact.prevent="handleSend"
    />
    <BaseButton :disabled="disabled || !question.trim()" @click="handleSend">
      <Send :size="16" />
    </BaseButton>
  </div>
</template>

<style scoped>
.chat-input {
  display: flex;
  gap: var(--spacing-sm);
  align-items: flex-end;
}

.chat-input__textarea {
  flex: 1;
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-md);
  font-family: var(--font-body);
  font-size: 1rem;
  line-height: 1.6;
  resize: none;
  outline: none;
  transition: border-color var(--duration-fast) var(--ease-out);
  background: var(--color-surface);
}

.chat-input__textarea:focus {
  border-color: var(--color-border-focus);
  box-shadow: 0 0 0 3px var(--color-brand-veil);
}

.chat-input__textarea:disabled {
  opacity: 0.5;
}
</style>