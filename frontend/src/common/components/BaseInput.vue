<script setup lang="ts">
import { NInput } from 'naive-ui'

defineProps<{
  modelValue?: string
  placeholder?: string
  disabled?: boolean
}>()

defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <NInput
    :value="modelValue"
    :placeholder="placeholder"
    :disabled="disabled"
    clearable
    @update:value="$emit('update:modelValue', $event)"
    v-bind="$attrs"
  />
</template>