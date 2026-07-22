<script setup>
import { onMounted, ref } from 'vue'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import { taskApi } from '../../../lib/taskApi.js'
import { statusLabel } from '../../../lib/statusLabels.js'

const rows = ref([])
const page = ref(0)
const total = ref(0)
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await taskApi.list(page.value, 20)
    rows.value = result.items || []
    total.value = result.total || 0
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
}

function changePage(value) {
  page.value = value
  load()
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <PageActions
      title="采集权限"
      description="选择任务后处理采集员申请、直接授权和权限撤销。"
    />
    <div class="business-card">
      <AsyncState
        :loading="loading"
        :error="error"
        :empty="!rows.length"
        empty-text="尚未创建任务"
        @retry="load"
      >
        <div class="business-table-wrap">
          <table class="business-table">
            <thead>
              <tr>
                <th>任务编号</th>
                <th>任务名称</th>
                <th>最终成果</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td>{{ row.taskCode }}</td>
                <td>{{ row.name }}</td>
                <td>{{ row.configuration?.resultType === 'TEXT' ? '文本或录音' : '仅录音' }}</td>
                <td>{{ statusLabel('task', row.lifecycle) }}</td>
                <td>
                  <router-link class="button-link" :to="`/admin/tasks/${row.id}/permissions`">
                    管理权限
                  </router-link>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationControls :page="page" :size="20" :total="total" @change="changePage" />
      </AsyncState>
    </div>
  </section>
</template>
