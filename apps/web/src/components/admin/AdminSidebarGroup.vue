<script setup>
import { computed } from 'vue'
import AdminSidebarItem from './AdminSidebarItem.vue'

const props = defineProps({
  item: {
    type: Object,
    required: true
  },
  isOpen: {
    type: Boolean,
    default: false
  },
  isActive: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['toggle'])

const hasChildren = computed(() => Array.isArray(props.item.children) && props.item.children.length > 0)
const childrenId = computed(() => `admin-sidebar-group-${props.item.key}`)

function handleToggle() {
  emit('toggle', props.item.key)
}
</script>

<template>
  <div
    class="admin-sidebar-group"
    :class="{ 'is-active': isActive, 'is-open': isOpen }"
  >
    <AdminSidebarItem v-if="!hasChildren" :item="item" />

    <template v-else>
      <button
        type="button"
        class="admin-sidebar-group__title"
        :aria-expanded="isOpen"
        :aria-controls="childrenId"
        @click="handleToggle"
      >
        <span class="admin-sidebar-item__icon" aria-hidden="true">{{ item.title.slice(0, 1) }}</span>
        <span class="admin-sidebar-group__text">{{ item.title }}</span>
        <span class="admin-sidebar-group__arrow" aria-hidden="true">›</span>
      </button>
      <div
        v-show="isOpen"
        :id="childrenId"
        class="admin-sidebar-group__children"
      >
        <AdminSidebarItem
          v-for="child in item.children"
          :key="child.key"
          :item="child"
          child
        />
      </div>
    </template>
  </div>
</template>
