<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const props = defineProps({
  item: {
    type: Object,
    required: true
  },
  child: {
    type: Boolean,
    default: false
  }
})

const route = useRoute()
const isActive = computed(() => route.path === props.item.path)
const marker = computed(() => (props.child ? '' : props.item.title.slice(0, 1)))
</script>

<template>
  <router-link
    class="admin-sidebar-item"
    :class="{ 'is-active': isActive, 'is-child': child }"
    :to="item.path"
  >
    <span v-if="child" class="admin-sidebar-item__dot" aria-hidden="true"></span>
    <span v-else class="admin-sidebar-item__icon" aria-hidden="true">{{ marker }}</span>
    <span>{{ item.title }}</span>
  </router-link>
</template>
