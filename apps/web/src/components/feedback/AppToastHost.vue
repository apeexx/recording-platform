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
        <span class="app-toast-icon" aria-hidden="true">
          <svg v-if="item.type === 'error'" data-toast-icon="error" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="8.75" />
            <path d="M12 7.5v5.25" />
            <circle cx="12" cy="16.25" r="1" class="app-toast-icon-dot" />
          </svg>
          <template v-else>{{ item.type === 'success' ? '✓' : 'i' }}</template>
        </span>
        {{ item.message }}
      </button>
    </div>
  </Teleport>
</template>
