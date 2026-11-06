/**
 * Element Plus 服务 API 与表单类型的统一出口。
 *
 * 业务代码统一从这里导入，而不要写 `from 'element-plus'`：根入口是 es/index.mjs
 * barrel，只要有一个模块引用它，Rollup 就会把 Element Plus 全部组件保留下来
 * （各组件 index.mjs 顶层的 withInstall 赋值被视为副作用），按需引入随即失效。
 *
 * 运行时服务走组件自己的深层入口；类型用 type-only 再导出，编译后被完全擦除，
 * 不会把 barrel 带回依赖图。
 */
export { ElMessage } from 'element-plus/es/components/message/index.mjs'
export { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
export { ElNotification } from 'element-plus/es/components/notification/index.mjs'

export type { FormInstance, FormRules, UploadRequestOptions } from 'element-plus'
