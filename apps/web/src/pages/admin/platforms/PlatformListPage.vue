<script setup>
import { onMounted, reactive, ref } from 'vue'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import { platformApi } from '../../../lib/platformApi.js'
import { operationId } from '../../../lib/apiUtils.js'

const rows = ref([]), loading = ref(false), error = ref(''), editingId = ref(''), page = ref(0), total = ref(0)
const form = reactive({ code: '', name: '', description: '', active: true })
async function load() { loading.value = true; error.value = ''; try { const result=await platformApi.list(page.value,20); rows.value=result.items||[];total.value=result.total||0 } catch (e) { error.value = e.message } finally { loading.value = false } }
function changePage(value){page.value=value;load()}
function edit(row) { editingId.value = row.id; Object.assign(form, { code: row.code, name: row.name, description: row.description || '', active: row.active }) }
function reset() { editingId.value = ''; Object.assign(form, { code: '', name: '', description: '', active: true }) }
async function save() { error.value = ''; try { const data = { ...form }; if (editingId.value) await platformApi.update(editingId.value, data, operationId('platform-update')); else await platformApi.create(data, operationId('platform-create')); reset(); await load() } catch (e) { error.value = e.message } }
async function remove(row) { if (!confirm(`确认删除平台“${row.name}”？已被任务使用的平台无法删除。`)) return; try { await platformApi.remove(row.id, operationId('platform-delete')); await load() } catch (e) { error.value = e.message } }
onMounted(load)
</script>
<template><section class="admin-page"><PageActions title="平台管理" description="平台用于归类苏州话、金华话等独立任务。" /><div class="business-grid"><form class="business-card business-form" @submit.prevent="save"><h3>{{ editingId ? '编辑平台' : '新增平台' }}</h3><label>平台编码<input v-model.trim="form.code" required maxlength="128" /></label><label>平台名称<input v-model.trim="form.name" required /></label><label>说明<textarea v-model.trim="form.description" rows="3" /></label><label class="business-check"><input v-model="form.active" type="checkbox" />启用</label><p v-if="error" class="business-error">{{ error }}</p><div class="business-actions"><button v-if="editingId" type="button" class="button-secondary" @click="reset">取消</button><button class="button-primary">保存</button></div></form><div class="business-card"><AsyncState :loading="loading" :error="error" :empty="!rows.length" @retry="load"><div class="business-table-wrap"><table class="business-table"><thead><tr><th>编码</th><th>名称</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td>{{ row.code }}</td><td>{{ row.name }}</td><td>{{ row.active ? '启用' : '停用' }}</td><td><button class="button-link" @click="edit(row)">编辑</button><button class="button-link is-danger" @click="remove(row)">删除</button></td></tr></tbody></table></div><PaginationControls :page="page" :total="total" :size="20" @change="changePage"/></AsyncState></div></div></section></template>
