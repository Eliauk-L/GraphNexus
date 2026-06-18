<script setup lang="ts">
import { NPopover } from 'naive-ui'

defineProps<{
  properties: Record<string, unknown>
}>()
</script>

<template>
  <NPopover trigger="hover" placement="right">
    <template #trigger>
      <slot />
    </template>
    <div class="node-detail supporting">
      <table>
        <tr v-for="(val, key) in properties" :key="key">
          <td class="key">{{ key }}</td>
          <td class="val">{{ String(val) }}</td>
        </tr>
      </table>
    </div>
  </NPopover>
</template>

<style scoped>
.node-detail table {
  border-collapse: collapse;
}
.node-detail td {
  padding: 2px 8px;
}
.node-detail .key {
  font-weight: 500;
  color: var(--color-text-secondary);
}
.node-detail .val {
  color: var(--color-text-primary);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>