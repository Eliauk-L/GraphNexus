<script setup lang="ts">
import { ShieldOff } from '@lucide/vue'
import { useRouter } from 'vue-router'
import BaseButton from '@/common/components/BaseButton.vue'

const router = useRouter()
</script>

<template>
  <div class="forbidden-page">
    <ShieldOff :size="48" color="var(--color-text-tertiary)" />
    <h1 class="display" style="color: var(--color-text-primary)">403</h1>
    <p class="body-lead" style="color: var(--color-text-secondary); margin-bottom: var(--spacing-lg)">
      您没有权限访问此页面
    </p>
    <BaseButton variant="primary" @click="router.push('/materials')">返回首页</BaseButton>
  </div>
</template>

<style scoped>
.forbidden-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  gap: var(--spacing-sm);
}
</style>