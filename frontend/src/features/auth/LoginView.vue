<script setup lang="ts">
// C 登录页面接统一身份；原型的角色入口改为已授权账号登录。
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/shared/Icon.vue'
import { toast } from '@/shared/toast'
import { useUserStore } from '@/stores/user'
import { ROLE_LABEL, type UserRole } from '@/types/auth'

interface DemoAccount {
  username: string
  displayName: string
  role: UserRole
  /** 这个账号登录后能做什么——写清楚，省得演示时一个个试。 */
  hint: string
}

// 仅开发预览显示，由 LocalAccountLoader 绑定已有规范用户 ID 并保存 BCrypt 口令。
const DEMO_PASSWORD = 'demo'
const DEMO_ACCOUNTS: DemoAccount[] = import.meta.env.DEV ? [
  {username:'creator.li',displayName:'李承志',role:'creator',hint:'任务、人员分配及导出'},
  {username:'annotator.chen',displayName:'陈晓',role:'annotator',hint:'本人独立标注'},
  {username:'annotator.liu',displayName:'刘一鸣',role:'annotator',hint:'另一名独立标注员'},
  {username:'arbiter.zhao',displayName:'赵明远',role:'arbiter',hint:'裁定与最终审查'},
  {username:'expert.qian',displayName:'钱学礼',role:'expert',hint:'被指派的专家复核'},
  {username:'admin.wang',displayName:'王慧',role:'admin',hint:'管理与授权概览'},
  {username:'teacher.sun',displayName:'孙立',role:'teacher',hint:'教学模块待接入'},
  {username:'student.zhou',displayName:'周雨桐',role:'student',hint:'教学模块待接入'}
] : []

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const form = ref({ username: '', password: '' })
const submitting = ref(false)
/** 错误就地显示在表单里，而不是只弹 toast：toast 2.6 秒就没了，用户回头看不到原因。 */
const error = ref('')

function pick(account: DemoAccount) {
  form.value.username = account.username
  form.value.password = DEMO_PASSWORD
  error.value = ''
}

async function submit() {
  if (!form.value.username.trim()) {
    error.value = '请输入登录名'
    return
  }
  if (!form.value.password) {
    error.value = '请输入口令'
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await store.login(form.value.username.trim(), form.value.password)
    toast(`登录成功，欢迎 ${store.name || store.username}`)
    // 带 redirect 的是「被守卫拦下来的那一页」，登录后回到那里而不是首页。
    // 只接受站内路径：以 / 开头且不是 //（//evil.com 会被当成协议相对 URL 跳走）。
    const redirect = route.query.redirect
    const target =
      typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
        ? redirect
        : '/'
    await router.replace(target)
  } catch (e) {
    // 拦截器已经弹过一次 toast；这里再在卡片里留一条，用户回看时还在。
    error.value = messageOf(e)
    // 口令清空但不改账号：错的大多是口令，重打一遍比重新选账号快。
    form.value.password = ''
  } finally {
    submitting.value = false
  }
}

/**
 * 从错误里取中文原因。
 * 拦截器为业务失败 reject 的是 `new Error(error.code)`（只有码，没有句子），
 * 而 401/403 来自 HTTP 层、带上后端信封，所以两种形状都要能读。
 */
function messageOf(e: unknown): string {
  if (e && typeof e === 'object') {
    const resp = (e as { response?: { status?: number; data?: { error?: { message?: string } } } })
      .response
    const serverMessage = resp?.data?.error?.message
    if (serverMessage) return serverMessage
    if (resp?.status === 401) return '登录名或口令不正确'
    if (resp?.status === 403) return '该账号无法登录，请联系管理员'
    if (resp?.status === 400) return '请输入登录名与口令'
    if ((e as { message?: string }).message === 'UNAUTHENTICATED') return '登录名或口令不正确'
  }
  return '登录失败，请稍后重试'
}
</script>

<template>
  <div class="auth">
    <section class="auth-brand">
      <div class="row" style="gap: 12px">
        <span class="brand-mark">律</span>
        <div class="brand-text">
          <b style="font-size: 15px">法律论证标注系统</b>
          <span style="color: var(--nav-fg-dim)">LEGAL ARG · v1.2</span>
        </div>
      </div>

      <div style="margin-top: 56px; position: relative; z-index: 1">
        <h1 class="t-display" style="color: #fff; max-width: 16ch">裁判文书<br />法律论证结构标注</h1>
        <p style="margin-top: 14px; max-width: 40ch; color: var(--nav-fg); font-size: 14px; line-height: 1.7">
          以《中文裁判文书说理部分法律论证结构标注与图示指南（v1.2）》为规范，
          支持文本切割、要素标注、关系标注与论证图示的完整工作流。
        </p>
      </div>

      <ul class="flow">
        <li><span class="n">1</span><span>颗粒度对齐 · 按标点与子句完成文本切割</span></li>
        <li><span class="n">2</span><span>要素标注 · 非命题 / 命题（GM·SM·GF·SF）</span></li>
        <li><span class="n">3</span><span>关系标注 · 支持 S / 反对 A / 组合 J / 匹配 M / 同一 I</span></li>
        <li><span class="n">4</span><span>图示生成 · 命题矩形 + 关系圆形符号系统</span></li>
      </ul>
      <div class="auth-foot">© 2026 法律方法研究中心 · 课程演示环境</div>
    </section>

    <section class="auth-form">
      <div class="auth-card card">
        <div class="page-head">
          <div class="eyebrow">SIGN IN</div>
          <h1 class="t-h1" style="margin-top: 6px">登录工作台</h1>
          <p class="t-sm muted" style="margin-top: 6px">
            请使用机构账号登录。进入后的功能范围由账号角色决定。
          </p>
        </div>

        <form class="stack-4" novalidate @submit.prevent="submit">
          <div class="field">
            <label for="acct">登录名</label>
            <input
              id="acct"
              v-model="form.username"
              class="input"
              autocomplete="username"
              placeholder="如 legal.researcher"
            />
          </div>

          <div class="field">
            <label for="pwd">口令</label>
            <input
              id="pwd"
              v-model="form.password"
              class="input"
              type="password"
              autocomplete="current-password"
              placeholder="请输入口令"
            />
          </div>

          <p v-if="error" class="auth-error">
            <Icon name="alert" />
            <span>{{ error }}</span>
          </p>

          <button class="btn btn-primary btn-lg btn-block" type="submit" :disabled="submitting">
            <Icon v-if="submitting" name="refresh" cls="spin" />
            {{ submitting ? '登录中…' : '登 录' }}
          </button>
        </form>

        <div v-if="DEMO_ACCOUNTS.length" class="note info" style="margin-top: 20px">
          <Icon name="info" />
          <div class="note-body">
            这是本地联调环境，下列账号口令统一为 <b class="mono">demo</b>，点账号即可填入。
            <b>角色不可自选</b>——它属于账号，登录后由后端按令牌判定。
          </div>
        </div>

        <div class="demo-accounts">
          <div v-for="a in DEMO_ACCOUNTS" :key="a.username" class="demo-account">
            <button
              type="button"
              class="demo-account-main"
              :class="{ 'is-on': form.username === a.username }"
              @click="pick(a)"
            >
              <span class="role-bar" :class="a.role"></span>
              <span class="demo-account-text">
                <b>{{ a.displayName }}</b>
                <span class="mono t-xs">{{ a.username }}</span>
              </span>
              <span class="chip chip-lg">{{ ROLE_LABEL[a.role] }}</span>
            </button>
            <span class="demo-account-hint t-xs muted">{{ a.hint }}</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
