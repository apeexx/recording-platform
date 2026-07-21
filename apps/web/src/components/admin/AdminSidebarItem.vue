<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AdminSidebarIcon from './AdminSidebarIcon.vue'

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
</script>

<template>
  <router-link
    class="admin-sidebar-item"
    :class="{ 'is-active': isActive, 'is-child': child }"
    :to="item.path"
  >
    <span v-if="child" class="admin-sidebar-item__dot" aria-hidden="true"></span>
    <AdminSidebarIcon v-else :name="item.icon" />
    <span>{{ item.title }}</span>
  </router-link>
</template>
