export const EMPTY_TEXT = '-'

export const formatNullable = (value: string | number | null | undefined) => {
  return value === null || value === undefined || value === '' ? EMPTY_TEXT : value
}

export const formatEmpty = (value: string | number | null | undefined): string => {
  return String(formatNullable(value))
}

export const formatAge = (age: number | null | undefined) => {
  return age === null || age === undefined ? EMPTY_TEXT : age
}

export const formatFileSize = (size: number | null | undefined) => {
  if (size === null || size === undefined) {
    return EMPTY_TEXT
  }

  if (size < 1024) {
    return `${size} B`
  }

  const sizeInKb = size / 1024

  if (sizeInKb < 1024) {
    return `${sizeInKb.toFixed(1)} KB`
  }

  return `${(sizeInKb / 1024).toFixed(1)} MB`
}

export const formatDateTime = (value: string | null | undefined) => {
  return formatEmpty(value)
}

export const formatRetryCount = (
  retryCount: number | null | undefined,
  maxRetryCount: number | null | undefined,
) => {
  return `${formatEmpty(retryCount)} / ${formatEmpty(maxRetryCount)}`
}

export const formatDurationMs = (value: number | null | undefined) => {
  if (value === null || value === undefined) {
    return EMPTY_TEXT
  }

  if (value < 1000) {
    return `${value} ms`
  }

  return `${(value / 1000).toFixed(2)} s`
}

export const formatPercent = (value: number | null | undefined) => {
  if (value === null || value === undefined) {
    return EMPTY_TEXT
  }

  return `${(value * 100).toFixed(1)}%`
}

export const formatScore = (value: number | null | undefined) => {
  if (value === null || value === undefined) {
    return EMPTY_TEXT
  }

  return value.toFixed(2)
}
