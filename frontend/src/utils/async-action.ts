import type { Ref } from 'vue'

/**
 * 请求层（utils/request.ts）的响应拦截器已统一弹出错误提示，
 * 页面里的事件处理函数不应重复提示，只需吞掉异常以避免
 * 未处理的 Promise rejection（UnhandledPromiseRejection）。
 * 开发环境保留一条 debug 日志，便于定位被吞掉的错误。
 */
export const ignoreHandledError = (error: unknown): void => {
  if (import.meta.env.DEV) {
    console.debug('[request] 已统一提示的错误：', error)
  }
}

/**
 * 包装由模板事件直接触发的异步操作，统一处理 loading 与错误吞噬。
 * 返回 undefined 表示失败，调用方可据此决定是否继续后续逻辑。
 *
 * @example
 * const reload = () => guardAction(async () => { list.value = await fetchList() }, loading)
 */
export const guardAction = async <T>(
  action: () => Promise<T>,
  loading?: Ref<boolean>,
): Promise<T | undefined> => {
  if (loading) loading.value = true
  try {
    return await action()
  } catch (error) {
    ignoreHandledError(error)
    return undefined
  } finally {
    if (loading) loading.value = false
  }
}
