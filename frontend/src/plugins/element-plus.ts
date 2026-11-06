/**
 * Element Plus 按需注册。
 *
 * 为什么不用 `app.use(ElementPlus)`：整包插件对象会引用全部组件，Rollup 无法做
 * tree-shaking，element-plus chunk 会膨胀到 1 MB 以上。这里静态枚举项目模板中
 * 实际出现的组件，只让这些组件进入依赖图。
 *
 * 为什么全部走 es/components/* 深层入口：从 'element-plus' 根入口导入会加载
 * es/index.mjs 这个 barrel，它顶层 import 了全部组件模块，而各组件 index.mjs 的
 * withInstall 赋值会被当作副作用保留，实测整个 91 个组件目录仍会留在 chunk 里。
 * 深层入口各自独立，Rollup 才能只保留被引用的组件。
 *
 * 为什么仍保留全量 CSS：`element-plus/dist/index.css` 的传输体积只有约 48 kB
 * (gzip)，但 ElMessage / ElMessageBox / ElNotification / v-loading 都是脱离模板
 * 动态挂载的节点，自动按需引样式极易漏掉它们。保持全量 CSS 可让这些节点的样式
 * 与改造前字节一致，收益舍去很小、风险却直接归零。
 *
 * 维护约束：模板里新增 <el-xxx> 标签时，必须同步在 components 中登记，否则运行时
 * 会出现 "Failed to resolve component"。
 */
import type { App, Component } from 'vue'

import { ElAlert } from 'element-plus/es/components/alert/index.mjs'
import { ElBadge } from 'element-plus/es/components/badge/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElCollapse, ElCollapseItem } from 'element-plus/es/components/collapse/index.mjs'
import {
  ElAside,
  ElContainer,
  ElHeader,
  ElMain,
} from 'element-plus/es/components/container/index.mjs'
import {
  ElDescriptions,
  ElDescriptionsItem,
} from 'element-plus/es/components/descriptions/index.mjs'
import { ElDialog } from 'element-plus/es/components/dialog/index.mjs'
import { ElDrawer } from 'element-plus/es/components/drawer/index.mjs'
import { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElImage } from 'element-plus/es/components/image/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElInputNumber } from 'element-plus/es/components/input-number/index.mjs'
import { ElLoading } from 'element-plus/es/components/loading/index.mjs'
import { ElMenu, ElMenuItem } from 'element-plus/es/components/menu/index.mjs'
import { ElPagination } from 'element-plus/es/components/pagination/index.mjs'
import { ElProgress } from 'element-plus/es/components/progress/index.mjs'
import { ElRadio, ElRadioButton, ElRadioGroup } from 'element-plus/es/components/radio/index.mjs'
import { ElRate } from 'element-plus/es/components/rate/index.mjs'
import { ElSegmented } from 'element-plus/es/components/segmented/index.mjs'
import { ElOption, ElSelect } from 'element-plus/es/components/select/index.mjs'
import { ElStep, ElSteps } from 'element-plus/es/components/steps/index.mjs'
import { ElTable, ElTableColumn } from 'element-plus/es/components/table/index.mjs'
import { ElTabPane, ElTabs } from 'element-plus/es/components/tabs/index.mjs'
import { ElTag } from 'element-plus/es/components/tag/index.mjs'
import { ElTimeline, ElTimelineItem } from 'element-plus/es/components/timeline/index.mjs'
import { ElUpload } from 'element-plus/es/components/upload/index.mjs'
import { ElMessage, ElMessageBox, ElNotification } from './element-plus-api'

/** 模板中出现的 41 个 el-* 标签对应的组件，按 PascalCase 全局注册（Vue 会自动解析 kebab-case）。 */
const components: Record<string, Component> = {
  ElAlert,
  ElAside,
  ElBadge,
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElContainer,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElHeader,
  ElIcon,
  ElImage,
  ElInput,
  ElInputNumber,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElOption,
  ElPagination,
  ElProgress,
  ElRadio,
  ElRadioButton,
  ElRadioGroup,
  ElRate,
  ElSegmented,
  ElSelect,
  ElStep,
  ElSteps,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
  ElTag,
  ElTimeline,
  ElTimelineItem,
  ElUpload,
}

/**
 * 这些插件的 install 会把 app._context 写回服务/指令，供 v-loading 与弹层渲染时
 * 继承应用上下文；只 import 函数调用是不够的。
 */
const plugins = [ElLoading, ElMessage, ElMessageBox, ElNotification]

export function setupElementPlus(app: App): void {
  for (const [name, component] of Object.entries(components)) {
    app.component(name, component)
  }
  for (const plugin of plugins) {
    app.use(plugin)
  }
}
