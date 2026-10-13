<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/shared/Icon.vue'
import StatusBadge from '@/shared/StatusBadge.vue'
import BaseModal from '@/shared/BaseModal.vue'
import { toast } from '@/shared/toast'
import { listGuides, createGuideVersion, archiveGuideVersion } from '@/api/guide'
import type { GuideListItem } from '@/types/guide'

const router = useRouter()

const items = ref<GuideListItem[]>([])
const loading = ref(false)
const filter = ref<'all' | 'active' | 'archived'>('all')
const kw = ref('')

onMounted(load)

async function load() {
  loading.value = true
  try {
    const r = await listGuides({ page: 1, size: 50 })
    items.value = r.items
  } finally {
    loading.value = false
  }
}

const currentEnable = computed(() => {
  const found = items.value.find((i) => i.guideStatus === 'ACTIVE')
  return found ? `v${found.versionCode}` : '—'
})

// 版本演进（按版本号升序）
const lineageSteps = computed(() =>
  items.value
    .slice()
    .reverse()
    .map((i) => ({ versionCode: i.versionCode, current: i.guideStatus === 'ACTIVE' }))
)

const filtered = computed(() =>
  items.value.filter((it) => {
    const s = it.guideStatus === 'ACTIVE' ? 'active' : 'archived'
    if (filter.value !== 'all' && s !== filter.value) return false
    if (kw.value) {
      const k = kw.value.toLowerCase()
      const hay = `v${it.versionCode}${it.title}`.toLowerCase()
      if (!hay.includes(k)) return false
    }
    return true
  })
)

function fmtSize(bytes: number | null): string {
  if (bytes == null) return '—'
  const kb = bytes / 1024
  if (kb >= 1024) return `${(kb / 1024).toFixed(1)} MB`
  return `${Math.round(kb)} KB`
}
function fmtDate(t: string | null): string {
  return t ? t.slice(0, 10) : '—'
}

function goDetail(row: GuideListItem) {
  router.push(`/guide/${row.id}`)
}

// ── 通用确认弹窗 ──
const confirmState = reactive<{
  open: boolean
  title: string
  body: string
  onOk: () => void
}>({ open: false, title: '', body: '', onOk: () => {} })

function askConfirm(title: string, body: string, onOk: () => void) {
  confirmState.title = title
  confirmState.body = body
  confirmState.onOk = onOk
  confirmState.open = true
}

function askArchive(row: GuideListItem) {
  askConfirm(`归档 v${row.versionCode}？`, '归档后不可用于新建任务，历史任务不受影响。', async () => {
    try {
      await archiveGuideVersion(row.versionId as string, { reason: '' })
      confirmState.open = false
      toast('已归档')
      await load()
    } catch {
      /* request 层已提示 */
    }
  })
}

function askEnable(row: GuideListItem) {
  askConfirm(`启用 v${row.versionCode}？`, '启用后新任务默认为该版本；历史任务锁定原版本不变。', () => {
    confirmState.open = false
    toast('当前契约仅支持归档与发布新版本，重新启用旧版本的接口未就绪', 'err')
  })
}

// ── 上传弹窗 ──
const uploadOpen = ref(false)
const uploadVersion = ref('1.3')
const filePicked = ref(false)
function onFile(e: Event) {
  const input = e.target as HTMLInputElement
  filePicked.value = !!input.files && input.files.length > 0
}
async function doUpload() {
  const code = uploadVersion.value.trim().replace(/^v/i, '')
  if (!/^\d+\.\d+$/.test(code)) {
    toast('版本号格式应为 x.y，例如 1.3', 'err')
    return
  }
  try {
    await createGuideVersion('g-12', { versionCode: code })
    uploadOpen.value = false
    toast(filePicked.value ? `已保存版本 v${code} 元数据；文件附件接口待接入` : `已创建草稿版本 v${code}`)
    await load()
  } catch {
    /* request 层已提示 */
  }
}

// ── 对比弹窗（严格复刻原型静态两栏） ──
const compareOpen = ref(false)
</script>

<template>
  <div class="page-head between">
    <div>
      <div class="eyebrow">GUIDE LIBRARY</div>
      <h1 class="t-h1" style="margin-top: 6px">标注指南管理</h1>
      <p class="t-sm muted" style="margin-top: 6px">
        上传、版本化管理并启用标注指南。任务创建时按所选版本确定标签体系与图示规范。
      </p>
    </div>
    <div class="row">
      <button class="btn btn-secondary" @click="compareOpen = true">版本对比</button>
      <button class="btn btn-primary" @click="uploadOpen = true">＋ 上传指南</button>
    </div>
  </div>

  <section class="card" style="overflow: hidden">
    <div class="card-head tight">
      <div class="card-title"><span class="dot"></span>版本演进</div>
      <span class="t-xs muted" style="margin-left: auto">当前启用：{{ currentEnable }}</span>
    </div>
    <div class="card-body">
      <div class="steps">
        <template v-for="(s, i) in lineageSteps" :key="i">
          <span v-if="i > 0" class="step-line"></span>
          <div class="step" :class="s.current ? 'is-current' : 'is-done'">
            <span class="step-n">{{ i + 1 }}</span>
            <span class="mono strong">v{{ s.versionCode }}</span>
          </div>
        </template>
        <span class="step-line"></span>
        <div class="step">
          <span class="step-n">→</span><span class="muted">下一版本</span>
        </div>
      </div>
    </div>
  </section>

  <section class="card" style="margin-top: 20px">
    <div class="card-head">
      <div class="card-title">全部版本</div>
      <div class="toolbar" style="margin-left: auto">
        <div class="search">
          <Icon name="search" />
          <input class="input input-sm" v-model="kw" placeholder="检索版本 / 标题" aria-label="检索指南" />
        </div>
        <div class="seg">
          <button :class="{ 'is-active': filter === 'all' }" @click="filter = 'all'">全部</button>
          <button :class="{ 'is-active': filter === 'active' }" @click="filter = 'active'">启用中</button>
          <button :class="{ 'is-active': filter === 'archived' }" @click="filter = 'archived'">已归档</button>
        </div>
      </div>
    </div>
    <div class="card-body card-pad-0">
      <div class="tbl-wrap">
        <table class="tbl">
          <thead>
            <tr>
              <th>版本</th>
              <th>标题</th>
              <th>状态</th>
              <th class="num">页数</th>
              <th class="num">大小</th>
              <th>上传时间</th>
              <th>上传人</th>
              <th style="text-align: right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in filtered" :key="row.id">
              <td class="mono strong">v{{ row.versionCode }}</td>
              <td class="cell-main">{{ row.title }}</td>
              <td>
                <StatusBadge :status="row.guideStatus === 'ACTIVE' ? 'active' : 'archived'" />
              </td>
              <td class="num">{{ row.pageCount ?? '—' }}</td>
              <td class="num">{{ fmtSize(row.fileSize) }}</td>
              <td class="mono t-xs">{{ fmtDate(row.uploadedAt) }}</td>
              <td>{{ row.uploadedBy }}</td>
              <td>
                <div class="row-actions">
                  <a class="btn btn-sm btn-ghost" @click="goDetail(row)">查看</a>
                  <button
                    v-if="row.guideStatus === 'ACTIVE'"
                    class="btn btn-sm btn-ghost"
                    @click="askArchive(row)"
                  >归档</button>
                  <button v-else class="btn btn-sm btn-secondary" @click="askEnable(row)">设为启用</button>
                </div>
              </td>
            </tr>
            <tr v-if="!filtered.length">
              <td colspan="8">
                <div class="empty">
                  <h4>没有符合条件的版本</h4>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div class="card-foot between">
      <span class="t-xs muted">共 {{ filtered.length }} 个版本{{ loading ? '（加载中）' : '' }}</span>
      <span class="t-xs muted">版本一经启用，历史任务仍锁定其原始版本</span>
    </div>
  </section>

  <!-- 上传 -->
  <BaseModal v-model:open="uploadOpen" title="上传标注指南" sub="支持 .docx / .pdf" @ok="doUpload">
    <div class="field">
      <label>指南文件</label>
      <input class="input" type="file" accept=".docx,.pdf" @change="onFile" />
    </div>
    <div class="field" style="margin-top: 14px">
      <label>版本号</label>
      <input class="input" v-model="uploadVersion" />
    </div>
  </BaseModal>

  <!-- 对比 -->
  <BaseModal v-model:open="compareOpen" wide title="版本对比" sub="v1.1 → v1.2 主要差异">
    <div class="diff-grid" style="grid-template-columns: 1fr 1fr">
      <div class="diff-col">
        <div class="diff-col-head">
          <span class="who mono">v1.1</span>
          <span class="badge default" style="margin-left: auto">已归档</span>
        </div>
        <div class="diff-body">
          <div class="diff-row">关系类型 <b>4</b>：S / A / J / I</div>
          <div class="diff-row">无匹配关系 M</div>
          <div class="diff-row">无多层嵌套图示规范</div>
        </div>
      </div>
      <div class="diff-col">
        <div class="diff-col-head">
          <span class="who mono">v1.2</span>
          <span class="badge accent" style="margin-left: auto">启用中</span>
        </div>
        <div class="diff-body">
          <div class="diff-row conflict">关系类型 <b>5</b>：新增 <b>M 匹配</b></div>
          <div class="diff-row conflict">新增 6 个 GM 子类型</div>
          <div class="diff-row conflict">新增嵌套结构图示规范</div>
        </div>
      </div>
    </div>
    <template #footer>
      <button class="btn btn-secondary" @click="compareOpen = false">关闭</button>
    </template>
  </BaseModal>

  <!-- 通用确认 -->
  <BaseModal v-model:open="confirmState.open" :title="confirmState.title">
    <p class="t-sm" style="line-height: 1.7">{{ confirmState.body }}</p>
    <template #footer>
      <button class="btn btn-secondary" @click="confirmState.open = false">取消</button>
      <button class="btn btn-primary" @click="confirmState.onOk()">确认</button>
    </template>
  </BaseModal>
</template>
