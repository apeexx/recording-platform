<script setup>
import { computed } from 'vue'

const props = defineProps({
  name: {
    type: String,
    required: true
  }
})

const source = computed(() => `/assets/icons/admin-sidebar/${props.name}.svg`)
</script>

<template>
  <span class="admin-sidebar-item__icon" aria-hidden="true">
    <img :src="source" alt="">
  </span>
</template>
