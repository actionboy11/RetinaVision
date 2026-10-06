<script setup lang="ts">
import { Link, Refresh, UserFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'

import { assignUserRole, listUsers, type AdminUser } from '@/api/admin'
import { linkLegacyPatientAccount, listLegacyPatientProfiles } from '@/api/patient'
import type { LegacyPatientProfile } from '@/types/patient'
import { userRoleTextMap } from '@/utils/enums'
import { formatDateTime } from '@/utils/format'

const users = ref<AdminUser[]>([])
const legacyProfiles = ref<LegacyPatientProfile[]>([])
const loading = ref(false)
const legacyLoading = ref(false)
const dialog = ref(false)
const linkDialog = ref(false)
const selected = ref<AdminUser | null>(null)
const selectedProfile = ref<LegacyPatientProfile | null>(null)
const linkUserId = ref<number>()
const form = reactive<{ roleCode: 'USER' | 'DOCTOR' | 'RESEARCHER'; professionalNo: string }>({ roleCode: 'USER', professionalNo: '' })
const patientAccounts = computed(() => users.value.filter((user) => user.roleCode === 'USER' && user.status === 1))
const roleLabel = (role: AdminUser['roleCode']) => userRoleTextMap[role]

async function loadUsers() { loading.value = true; try { users.value = await listUsers() } finally { loading.value = false } }
async function loadLegacy() { legacyLoading.value = true; try { legacyProfiles.value = await listLegacyPatientProfiles() } finally { legacyLoading.value = false } }
async function refreshAll() { await Promise.all([loadUsers(), loadLegacy()]) }
function edit(row: AdminUser) { selected.value = row; form.roleCode = row.roleCode === 'ADMIN' ? 'USER' : row.roleCode; form.professionalNo = row.professionalNo ?? ''; dialog.value = true }
async function save() {
  if (!selected.value) return
  if (form.roleCode === 'DOCTOR' && !form.professionalNo.trim()) { ElMessage.warning('授予医生角色必须填写职业标识'); return }
  await assignUserRole(selected.value.id, { roleCode: form.roleCode, professionalNo: form.professionalNo.trim() || undefined })
  dialog.value = false
  await loadUsers()
  ElMessage.success('角色已更新，用户重新登录后生效')
}
function openLink(row: LegacyPatientProfile) { selectedProfile.value = row; linkUserId.value = row.accountUserId ?? undefined; linkDialog.value = true }
async function saveLink() {
  if (!selectedProfile.value || !linkUserId.value) { ElMessage.warning('请选择患者账号'); return }
  await ElMessageBox.confirm(`确认将匿名档案 ${selectedProfile.value.patientNo} 关联到所选患者账号？已有病例的档案冲突时系统会拒绝。`, '关联历史档案', { type: 'warning' })
  await linkLegacyPatientAccount(selectedProfile.value.id, linkUserId.value)
  linkDialog.value = false
  await loadLegacy()
  ElMessage.success('历史匿名档案已关联')
}
onMounted(() => Promise.all([loadUsers(), loadLegacy()]))
</script>

<template>
  <section class="admin-page">
    <header class="page-heading">
      <div><h2>用户与匿名档案</h2><p>管理可信角色，并将历史匿名档案安全关联到患者账号。</p></div>
      <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
    </header>
    <section class="panel">
      <div class="panel-title"><el-icon><UserFilled /></el-icon><strong>用户角色与医生身份</strong></div>
      <el-table v-loading="loading" :data="users" border>
        <el-table-column prop="username" label="用户名" min-width="130" />
        <el-table-column prop="realName" label="显示名称" min-width="130" />
        <el-table-column label="角色" width="110"><template #default="{ row }: { row: AdminUser }">{{ roleLabel(row.roleCode) }}</template></el-table-column>
        <el-table-column prop="professionalNo" label="职业标识" min-width="150" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="120"><template #default="{ row }"><el-button v-if="row.roleCode !== 'ADMIN'" link type="primary" @click="edit(row)">调整角色</el-button></template></el-table-column>
      </el-table>
    </section>
    <section class="panel">
      <div class="panel-title"><el-icon><Link /></el-icon><strong>历史匿名档案关联</strong><span>只显示匿名编号、旧编号、账号和病例数量，不展示临床内容。</span></div>
      <el-table v-loading="legacyLoading" :data="legacyProfiles" border empty-text="暂无待治理历史档案">
        <el-table-column prop="patientNo" label="匿名患者编号" min-width="150" />
        <el-table-column prop="legacyPatientCode" label="历史编号" min-width="150" />
        <el-table-column prop="accountUsername" label="已关联账号" min-width="140"><template #default="{ row }">{{ row.accountUsername || '未关联' }}</template></el-table-column>
        <el-table-column prop="caseCount" label="病例数" width="90" />
        <el-table-column label="创建时间" min-width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" @click="openLink(row)">{{ row.accountUserId ? '调整关联' : '关联账号' }}</el-button></template></el-table-column>
      </el-table>
    </section>
    <el-dialog v-model="dialog" title="授予可信角色" width="480px">
      <el-form label-width="90px">
        <el-form-item label="角色"><el-select v-model="form.roleCode"><el-option label="患者" value="USER" /><el-option label="研究员" value="RESEARCHER" /><el-option label="医生" value="DOCTOR" /></el-select></el-form-item>
        <el-form-item v-if="form.roleCode === 'DOCTOR'" label="职业标识"><el-input v-model="form.professionalNo" placeholder="例如 DOC-2026-001" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="linkDialog" title="关联患者账号" width="520px">
      <el-form label-position="top">
        <el-form-item label="匿名患者档案"><el-input :model-value="selectedProfile?.patientNo" disabled /></el-form-item>
        <el-form-item label="患者账号"><el-select v-model="linkUserId" filterable class="full-width" placeholder="请选择启用的患者账号"><el-option v-for="user in patientAccounts" :key="user.id" :label="`${user.realName || user.username}（${user.username}）`" :value="user.id" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="linkDialog = false">取消</el-button><el-button type="primary" @click="saveLink">确认关联</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.admin-page { display: flex; flex-direction: column; gap: 16px; }
.page-heading { display: flex; justify-content: space-between; align-items: flex-start; }
.page-heading h2 { margin: 0; font-size: 22px; }
.page-heading p { margin: 6px 0 0; color: #6b7280; }
.panel { padding: 18px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
.panel-title { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.panel-title span { color: #6b7280; font-size: 13px; }
.full-width { width: 100%; }
</style>
