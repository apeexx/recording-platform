<script setup>
import { computed, ref, watch } from 'vue'
import { adminPrototypePages } from '../../data/adminStaticData.js'

const props = defineProps({
  pageKey: {
    type: String,
    required: true
  }
})

const activeFilter = ref('all')
const activeTabIndex = ref(0)
const selectedRow = ref(null)
const notice = ref('')
const toggledRows = ref(new Set())

const page = computed(() => adminPrototypePages[props.pageKey])
const tabs = computed(() => page.value?.tabs || ['概览'])
const rows = computed(() => page.value?.rows || [])
const filters = computed(() => page.value?.filters || [{ key: 'all', label: '全部' }])

const filteredRows = computed(() => {
  if (activeFilter.value === 'all') {
    return rows.value
  }

  return rows.value.filter((row) => row.status === activeFilter.value)
})

const activeTab = computed(() => tabs.value[activeTabIndex.value] || tabs.value[0])

function selectFilter(filterKey) {
  activeFilter.value = filterKey
  selectedRow.value = null
}

function selectTab(index) {
  activeTabIndex.value = index
}

function selectRow(row) {
  selectedRow.value = row
}

function closeDetail() {
  selectedRow.value = null
}

function runPrimaryAction() {
  notice.value = `${page.value.action}：已完成本地演示，不会提交到后端。`
}

function toggleRowStatus(row) {
  const next = new Set(toggledRows.value)

  if (next.has(row.id)) {
    next.delete(row.id)
    notice.value = `${row.name} 已恢复为示例状态。`
  } else {
    next.add(row.id)
    notice.value = `${row.name} 已切换为本地演示状态。`
  }

  toggledRows.value = next
}

function getRowStatusText(row) {
  if (toggledRows.value.has(row.id)) {
    return row.status === 'active' ? '已停用' : '已启用'
  }

  return row.statusText
}

watch(
  () => props.pageKey,
  () => {
    activeFilter.value = 'all'
    activeTabIndex.value = 0
    selectedRow.value = null
    notice.value = ''
    toggledRows.value = new Set()
  }
)
</script>

<template>
  <div v-if="page" class="admin-page admin-prototype">
    <section class="admin-prototype-hero">
      <div>
        <span class="status-pill">静态前端原型</span>
        <h2>{{ page.title }}</h2>
        <p>{{ page.description }}</p>
      </div>
      <button class="admin-primary-button" type="button" @click="runPrimaryAction">
        {{ page.action }}
      </button>
    </section>

    <p v-if="notice" class="admin-toast" role="status">{{ notice }}</p>

    <section class="admin-stat-grid" :aria-label="`${page.title}统计`">
      <article
        v-for="metric in page.metrics"
        :key="metric.label"
        class="admin-stat-card admin-metric-card"
        :class="`is-${metric.tone}`"
      >
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
      </article>
    </section>

    <section class="admin-prototype-grid">
      <article class="admin-page-card admin-prototype-main">
        <div class="admin-card-heading">
          <div>
            <span class="admin-section-label">{{ page.module }}</span>
            <h3>{{ activeTab }}</h3>
          </div>
          <div class="admin-filter-group" :aria-label="`${page.title}筛选`">
            <button
              v-for="filter in filters"
              :key="filter.key"
              class="admin-filter-button"
              :class="{ 'is-active': activeFilter === filter.key }"
              type="button"
              @click="selectFilter(filter.key)"
            >
              {{ filter.label }}
            </button>
          </div>
        </div>

        <div class="admin-tab-list" role="tablist" :aria-label="`${page.title}视图切换`">
          <button
            v-for="(tab, index) in tabs"
            :key="tab"
            class="admin-tab-button"
            :class="{ 'is-active': activeTabIndex === index }"
            type="button"
            role="tab"
            :aria-selected="activeTabIndex === index"
            @click="selectTab(index)"
          >
            {{ tab }}
          </button>
        </div>

        <div class="admin-table-wrap">
          <table class="admin-data-table">
            <thead>
              <tr>
                <th>编号</th>
                <th>名称</th>
                <th>负责人</th>
                <th>状态</th>
                <th>进度</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in filteredRows"
                :key="row.id"
                :class="{ 'is-selected': selectedRow?.id === row.id }"
                @click="selectRow(row)"
              >
                <td>{{ row.id }}</td>
                <td>
                  <strong>{{ row.name }}</strong>
                </td>
                <td>{{ row.owner }}</td>
                <td>
                  <span class="admin-status-badge" :class="`is-${row.status}`">
                    {{ getRowStatusText(row) }}
                  </span>
                </td>
                <td>{{ row.progress }}</td>
                <td>{{ row.updatedAt }}</td>
                <td>
                  <button
                    class="admin-inline-button"
                    type="button"
                    @click.stop="toggleRowStatus(row)"
                  >
                    切换状态
                  </button>
                </td>
              </tr>
            </tbody>
          </table>

          <div v-if="filteredRows.length === 0" class="admin-empty-state">
            当前筛选下暂无示例记录。
          </div>
        </div>
      </article>

      <aside class="admin-prototype-side">
        <article class="admin-page-card">
          <div class="admin-card-heading">
            <h3>流程提示</h3>
          </div>
          <ol class="admin-step-list">
            <li v-for="item in page.timeline" :key="item">{{ item }}</li>
          </ol>
        </article>

        <article class="admin-page-card">
          <div class="admin-card-heading">
            <h3>检查项</h3>
          </div>
          <div class="admin-check-list">
            <label v-for="item in page.checklist" :key="item">
              <input type="checkbox" />
              <span>{{ item }}</span>
            </label>
          </div>
        </article>
      </aside>
    </section>

    <div
      v-if="selectedRow"
      class="admin-detail-panel"
      role="dialog"
      aria-modal="true"
      :aria-label="`${selectedRow.name}详情`"
    >
      <div class="admin-detail-panel__content">
        <button class="admin-detail-panel__close" type="button" @click="closeDetail">
          关闭
        </button>
        <span class="status-pill">详情预览</span>
        <h3>{{ selectedRow.name }}</h3>
        <dl>
          <div>
            <dt>编号</dt>
            <dd>{{ selectedRow.id }}</dd>
          </div>
          <div>
            <dt>负责人</dt>
            <dd>{{ selectedRow.owner }}</dd>
          </div>
          <div>
            <dt>当前状态</dt>
            <dd>{{ getRowStatusText(selectedRow) }}</dd>
          </div>
          <div>
            <dt>更新时间</dt>
            <dd>{{ selectedRow.updatedAt }}</dd>
          </div>
        </dl>
        <p>
          这是静态详情预览，仅用于确认页面跳转、筛选和本地状态切换效果；不会调用接口或写入数据库。
        </p>
      </div>
    </div>
  </div>
</template>
