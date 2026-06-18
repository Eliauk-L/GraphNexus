<script setup lang="ts">
import { NButton } from 'naive-ui'
import type { ButtonProps } from 'naive-ui'

interface Props {
  variant?: 'primary' | 'danger'
  size?: 'small' | 'medium' | 'large'
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'primary',
  size: 'medium',
})

const typeMap: Record<string, ButtonProps['type']> = {
  primary: 'primary',
  danger: 'error',
}

const sizeMap: Record<string, ButtonProps['size']> = {
  small: 'small',
  medium: 'medium',
  large: 'large',
}
</script>

<template>
  <NButton
    :type="typeMap[props.variant]"
    :size="sizeMap[props.size]"
    :secondary="props.variant === 'danger'"
    v-bind="$attrs"
  >
    <slot />
  </NButton>
</template>