<script setup>
import { useNotifications } from '../../composables/useNotifications.js'
const notifications = useNotifications()
</script>
<template>
  <Teleport to="body">
    <div class="app-toast-host" aria-label="系统通知">
      <button v-for="item in notifications.items" :key="item.id" class="app-toast" :class="`is-${item.type}`"
        :aria-live="item.type === 'error' ? 'assertive' : 'polite'" :aria-label="`${item.message}，点击关闭`"
        @click="notifications.dismiss(item.id)">
        <span>{{ item.type === 'success' ? '✓' : item.type === 'error' ? '!' : 'i' }}</span>{{ item.message }}
      </button>
    </div>
  </Teleport>
</template>
