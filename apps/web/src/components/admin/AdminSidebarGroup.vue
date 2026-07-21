<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import AdminSidebarIcon from './AdminSidebarIcon.vue'
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
const titleButton = ref(null)
const childrenPanel = ref(null)

function handleToggle() {
  emit('toggle', props.item.key)
}

watch(
  () => props.isOpen,
  async (isOpen, wasOpen) => {
    if (isOpen || !wasOpen || !childrenPanel.value || typeof document === 'undefined') {
      return
    }

    if (!childrenPanel.value.contains(document.activeElement)) {
      return
    }

    await nextTick()
    titleButton.value?.focus({ preventScroll: true })
  }
)
</script>

<template>
  <div
    class="admin-sidebar-group"
    :class="{ 'is-active': isActive, 'is-open': isOpen }"
  >
    <AdminSidebarItem v-if="!hasChildren" :item="item" />

    <template v-else>
      <button
        ref="titleButton"
        type="button"
        class="admin-sidebar-group__title"
        :aria-expanded="isOpen"
        :aria-controls="childrenId"
        @click="handleToggle"
      >
        <AdminSidebarIcon :name="item.icon" />
        <span class="admin-sidebar-group__text">{{ item.title }}</span>
        <span class="admin-sidebar-group__arrow" aria-hidden="true">›</span>
      </button>
      <div
        :id="childrenId"
        ref="childrenPanel"
        class="admin-sidebar-group__children"
        :class="isOpen ? 'is-open' : 'is-collapsed'"
        :aria-hidden="isOpen ? undefined : 'true'"
        :inert="isOpen ? undefined : true"
      >
        <div class="admin-sidebar-group__children-clip">
          <div class="admin-sidebar-group__children-list">
            <AdminSidebarItem
              v-for="child in item.children"
              :key="child.key"
              :item="child"
              child
            />
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
