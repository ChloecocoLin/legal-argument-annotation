<script setup lang="ts">
// 登录页。归成员C（分工.md §六）。
//
// 与原型 login.html 的**关键差别**：原型让人「选择进入角色」，这里不选。
// 「登录后自选角色」等于把 RBAC 做成摆设——角色是账号的属性（app_user_role），
// 由后端按令牌判定，前端连猜都猜不到。所以右边换成**演示账号列表**：
// 点一下填入账号，口令统一是 demo。演示时想切角色就换个账号登录，这才是真的换身份。
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

// 与 backend/src/main/resources/data.sql 的 app_user 种子一一对应——**含姓名**。
// 曾经在这里写过「李标注 / 张裁定」这种角色味的名字，登录后顶栏却显示种子里的「李铭 / 张宁」，
// 同一个账号在两处叫不同名字，看着像串号。以种子为准。
// 口令都是 {noop}demo：这是**演示环境**的占位，不是可以带进生产的口令策略。
const DEMO_PASSWORD = 'demo'
const DEMO_ACCOUNTS: DemoAccount[] = [
  { username: 'legal.researcher', displayName: '王慧', role: 'admin', hint: '全部功能，也是自查脚本用的账号' },
  { username: 'ann.li', displayName: '李铭', role: 'annotator', hint: '关系标注、图示审查' },
  { username: 'arb.zhang', displayName: '张宁', role: 'arbiter', hint: '关系裁定、图示审查' },
  { username: 'stu.chen', displayName: '陈默', role: 'student', hint: '关系练习' },
  { username: 'tea.wu', displayName: '吴敏', role: 'teacher', hint: '关系练习' }
]

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const form = ref({ username: 'legal.researcher', password: DEMO_PASSWORD })
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
              placeholder="演示环境统一为 demo"
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

        <div class="note info" style="margin-top: 20px">
          <Icon name="info" />
          <div class="note-body">
            这是课程演示环境，下列账号口令统一为 <b class="mono">demo</b>，点账号即可填入。
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
