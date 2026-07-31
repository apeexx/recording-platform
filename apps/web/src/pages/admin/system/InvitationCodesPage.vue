<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import HelpPopover from '../../../components/form/HelpPopover.vue'
import { useNotifications } from '../../../composables/useNotifications.js'
import { operationId } from '../../../lib/apiUtils.js'
import { invitationApi } from '../../../lib/invitationApi.js'

const notifications = useNotifications()
const rows = ref([])
const loading = ref(false)
const error = ref('')
const page = ref(0)
const size = ref(20)
const total = ref(0)
const createOpen = ref(false)
const creating = ref(false)
const revealCode = ref('')
const createPanel = ref(null)
const nameInput = ref(null)
const noteInput = ref(null)
const maxUsesInput = ref(null)
const form = reactive({ name: '', note: '', maxUses: 1 })
let loadSequence = 0

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

async function load(showToast = false) {
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const response = await invitationApi.list(page.value, size.value)
    if (sequence !== loadSequence) return
    rows.value = response.items || []
    total.value = Number(response.total) || 0
    if (page.value >= totalPages.value && page.value > 0) {
      page.value = totalPages.value - 1
      await load(showToast)
    }
  } catch (exception) {
    if (sequence !== loadSequence) return
    if (showToast || rows.value.length) notifications.error(exception.message)
    else error.value = exception.message
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function openCreate() {
  createOpen.value = true
}

function resetForm() {
  Object.assign(form, { name: '', note: '', maxUses: 1 })
}

function closeCreate() {
  if (creating.value) return
  createOpen.value = false
  resetForm()
}

async function create() {
  if (creating.value) return
  if (!form.name.trim()) {
    notifications.error('请输入邀请码名称')
    nameInput.value?.focus()
    return
  }
  if (form.name.trim().length > 64) {
    notifications.error('邀请码名称不能超过 64 个字符')
    nameInput.value?.focus()
    return
  }
  if (form.note.trim().length > 200) {
    notifications.error('邀请码备注不能超过 200 个字符')
    noteInput.value?.focus()
    return
  }
  const maxUses = Number(form.maxUses)
  if (!Number.isInteger(maxUses) || maxUses < 1 || maxUses > 1000) {
    notifications.error('使用次数必须为 1 到 1000 的整数')
    maxUsesInput.value?.focus()
    return
  }
  creating.value = true
  try {
    const created = await invitationApi.create({
      name: form.name.trim(),
      note: form.note.trim() || null,
      maxUses
    })
    revealCode.value = created.invitationCode
    createOpen.value = false
    resetForm()
    await load(true)
  } catch (exception) {
    notifications.error(exception.message)
  } finally {
    creating.value = false
  }
}

async function copyInvitation() {
  try {
    await navigator.clipboard.writeText(revealCode.value)
    notifications.success('邀请码已复制')
  } catch {
    notifications.error('复制失败，请手动复制邀请码')
  }
}

function closeReveal() {
  revealCode.value = ''
}

async function disable(row) {
  if (!confirm(`确认停用邀请码“${row.name}”？停用后不能恢复。`)) return
  try {
    await invitationApi.disable(row.id, operationId('invitation-disable'))
    notifications.success('邀请码已停用')
    await load(true)
  } catch (exception) {
    notifications.error(exception.message)
  }
}

function statusText(status) {
  return { ACTIVE: '有效', EXHAUSTED: '已用尽', DISABLED: '已停用' }[status] || status
}

function timeText(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

async function go(nextPage) {
  if (nextPage < 0 || nextPage >= totalPages.value || nextPage === page.value) return
  page.value = nextPage
  await load(true)
}
async function changeSize(value) { size.value = value; page.value = 0; await load(true) }

watch(createOpen, async (open) => {
  document.body.style.overflow = open || revealCode.value ? 'hidden' : ''
  if (open) {
    await nextTick()
    nameInput.value?.focus()
  }
})
watch(revealCode, code => {
  document.body.style.overflow = code || createOpen.value ? 'hidden' : ''
})
onMounted(load)
onBeforeUnmount(() => { document.body.style.overflow = '' })
</script>

<template>
  <section>
    <PageActions title="邀请码管理" description="控制新微信身份首次进入小程序的使用范围">
      <HelpPopover label="邀请码使用规则说明" content="邀请码仅用于新微信身份首次准入；每次成功兑换占用一次，停用后永久不可恢复，完整邀请码只在创建后显示一次。" />
      <button data-testid="open-create-invitation" class="primary" @click="openCreate">创建邀请码</button>
    </PageActions>
    <AsyncState :loading="loading" :error="error" :empty="!rows.length" @retry="load">
      <div class="panel">
        <table>
          <thead>
            <tr><th>名称</th><th>邀请码</th><th>用途备注</th><th>使用情况</th><th>状态</th><th>创建信息</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td>{{ row.name }}</td>
              <td class="code">****-****-{{ row.codeSuffix }}</td>
              <td>{{ row.note || '-' }}</td>
              <td><strong>{{ row.usedCount }} / {{ row.maxUses }}</strong><small>剩余 {{ row.remainingUses }} 次</small></td>
              <td><span :class="['status', row.status.toLowerCase()]">{{ statusText(row.status) }}</span></td>
              <td>{{ row.createdByName || row.createdByUserId }}<small>{{ timeText(row.createdAt) }}</small></td>
              <td><button v-if="row.status === 'ACTIVE'" data-testid="disable-invitation" class="link danger" @click="disable(row)">停用</button><span v-else>-</span></td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationControls :page="page" :size="size" :total="total" @change="go" @size-change="changeSize" />
    </AsyncState>

    <Teleport to="body">
      <div v-if="createOpen" data-testid="create-invitation-modal" class="modal-backdrop" @click.self="closeCreate">
        <section ref="createPanel" class="modal" role="dialog" aria-modal="true" aria-label="创建邀请码">
          <h2>创建邀请码</h2>
          <p>完整邀请码只在创建成功后显示一次，请及时复制保存。</p>
          <form novalidate @submit.prevent="create">
            <label>名称<input ref="nameInput" v-model="form.name" maxlength="64" placeholder="例如：审核体验"></label>
            <label>备注（可选）<input ref="noteInput" v-model="form.note" maxlength="200" placeholder="记录发放对象或用途"></label>
            <div class="invitation-help-label">最大使用次数 <HelpPopover label="最大使用次数说明" content="允许 1 至 1000 次；只有新身份成功兑换才计数，重复登录不会再次占用。" /></div>
            <input ref="maxUsesInput" v-model.number="form.maxUses" type="number" min="1" max="1000" step="1" aria-label="最大使用次数">
            <div class="modal-actions"><button type="button" :disabled="creating" @click="closeCreate">取消</button><button class="primary" type="submit" :disabled="creating">{{ creating ? '创建中…' : '创建' }}</button></div>
          </form>
        </section>
      </div>
      <div v-if="revealCode" data-testid="invitation-reveal-modal" class="modal-backdrop">
        <section class="modal reveal" role="dialog" aria-modal="true" aria-label="邀请码创建成功">
          <h2>邀请码创建成功</h2>
          <p>关闭后无法再次查看完整邀请码，请立即复制并妥善发送。</p>
          <div class="secret">{{ revealCode }}</div>
          <div class="modal-actions"><button data-testid="copy-invitation" @click="copyInvitation">复制邀请码</button><button data-testid="close-invitation-reveal" class="primary" @click="closeReveal">我已保存</button></div>
        </section>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.panel{overflow:auto;background:var(--card);border:1px solid var(--border);border-radius:var(--radius);padding:20px}.primary{border:0;border-radius:10px;padding:10px 18px;background:var(--primary);color:var(--primary-foreground)}.primary:disabled{opacity:.65}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:14px;border-bottom:1px solid var(--border);vertical-align:top}small{display:block;margin-top:5px;color:var(--muted-foreground)}.code{font-family:ui-monospace,SFMono-Regular,Consolas,monospace;white-space:nowrap}.status{display:inline-flex;padding:4px 9px;border-radius:999px;background:var(--accent);color:var(--foreground)}.status.disabled{color:var(--muted-foreground)}.status.exhausted{color:var(--chart-3)}.link{border:0;background:transparent;color:var(--primary)}.danger{color:var(--destructive)}.modal button{border:1px solid var(--border);border-radius:10px;background:var(--card);color:var(--foreground);padding:9px 14px}.modal-backdrop{position:fixed;inset:0;z-index:2900;display:grid;place-items:center;padding:24px;background:color-mix(in srgb,var(--foreground) 48%,transparent);backdrop-filter:blur(4px)}.modal{width:min(500px,100%);padding:26px;border:1px solid var(--border);border-radius:var(--radius);background:var(--card);color:var(--foreground);box-shadow:0 24px 70px var(--shadow-color)}.modal h2{margin:0 0 10px}.modal p{color:var(--muted-foreground)}.modal form{display:grid;gap:16px;margin-top:20px}.modal label{display:grid;gap:8px;font-weight:700}.modal input{width:100%;box-sizing:border-box;border:1px solid var(--border);border-radius:10px;background:var(--background);color:var(--foreground);padding:11px 12px}.modal-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:12px}.modal-actions .primary{background:var(--primary);color:var(--primary-foreground)}.secret{margin:20px 0;padding:18px;border:1px dashed var(--primary);border-radius:12px;background:var(--background);font:700 24px ui-monospace,SFMono-Regular,Consolas,monospace;text-align:center;letter-spacing:2px}@media(max-width:720px){.modal-backdrop{padding:16px}}
</style>
