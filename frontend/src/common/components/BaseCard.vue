<script setup lang="ts">
defineProps<{
  title?: string
}>()
</script>

<template>
  <div class="base-card">
    <div v-if="title || $slots.header" class="base-card__header">
      <h3 v-if="title" class="title">{{ title }}</h3>
      <div v-if="$slots.header" class="base-card__header-actions">
        <slot name="header" />
      </div>
    </div>
    <div class="base-card__body">
      <slot />
    </div>
    <div v-if="$slots.footer" class="base-card__footer">
      <slot name="footer" />
    </div>
  </div>
</template>

<style scoped>
.base-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-md);
  padding: var(--spacing-md);
}

.base-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.base-card__header-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.base-card__footer {
  margin-top: var(--spacing-md);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--color-border);
}
</style>