<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NInput } from 'naive-ui'
import BaseButton from '@/common/components/BaseButton.vue'
import { useAuthStore } from '@/stores/authStore'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const errorMsg = ref('')

async function handleLogin() {
  if (!username.value || !password.value) return
  errorMsg.value = ''
  loading.value = true
  try {
    await authStore.login({ username: username.value, password: password.value })
    const redirect = (route.query.redirect as string) || '/materials'
    router.push(redirect)
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.userTip || '登录失败，请重试'
  } finally {
    loading.value = false
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') handleLogin()
}
</script>

<template>
  <div class="login-page" @keydown="onKeydown">
    <div class="login-card">
      <h1 class="login-brand display">GraphNexus</h1>

      <div class="login-field">
        <label class="label">用户名</label>
        <NInput
          v-model:value="username"
          placeholder="请输入用户名"
          size="large"
          :disabled="loading"
          @keydown.enter="handleLogin"
        />
      </div>

      <div class="login-field">
        <label class="label">密码</label>
        <NInput
          v-model:value="password"
          type="password"
          placeholder="请输入密码"
          size="large"
          show-password-on="click"
          :disabled="loading"
          @keydown.enter="handleLogin"
        />
      </div>

      <div class="login-action">
        <BaseButton
          variant="primary"
          size="large"
          :loading="loading"
          :disabled="!username || !password"
          style="width: 100%"
          @click="handleLogin"
        >
          登 录
        </BaseButton>
      </div>

      <transition name="fade">
        <p v-if="errorMsg" class="login-error supporting">{{ errorMsg }}</p>
      </transition>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--color-bg);
}

.login-card {
  width: 360px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-lg);
  padding: var(--spacing-xl);
  box-shadow: var(--shadow-card-lifted);
}

.login-brand {
  text-align: center;
  color: var(--color-brand);
  margin-bottom: var(--spacing-lg);
}

.login-field {
  margin-bottom: var(--spacing-md);
}

.login-field .label {
  display: block;
  margin-bottom: var(--spacing-xs);
}

.login-action {
  margin-top: var(--spacing-lg);
}

.login-error {
  color: var(--color-error);
  text-align: center;
  margin-top: var(--spacing-sm);
  transition: opacity var(--duration-fast) var(--ease-out);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--duration-fast) var(--ease-out);
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>