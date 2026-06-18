<script setup lang="ts">
import { NMessageProvider, NDialogProvider, NConfigProvider } from 'naive-ui'
import AppLayout from '@/common/components/AppLayout.vue'
</script>

<template>
  <NConfigProvider>
    <NDialogProvider>
      <NMessageProvider>
        <AppLayout />
      </NMessageProvider>
    </NDialogProvider>
  </NConfigProvider>
</template>