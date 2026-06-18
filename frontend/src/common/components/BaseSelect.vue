<script setup lang="ts">
import { NSelect } from 'naive-ui'
import type { SelectOption } from 'naive-ui'

defineProps<{
  modelValue?: string | string[] | number | null
  options: SelectOption[]
  placeholder?: string
  multiple?: boolean
  disabled?: boolean
}>()

defineEmits<{
  'update:modelValue': [value: string | string[] | number | null]
}>()
</script>

<template>
  <NSelect
    :value="modelValue"
    :options="options"
    :placeholder="placeholder"
    :multiple="multiple"
    :disabled="disabled"
    clearable
    @update:value="$emit('update:modelValue', $event)"
    v-bind="$attrs"
  />
</template>