import DOMPurify from 'dompurify'
import { marked } from 'marked'

const allowedTags = [
  'p',
  'br',
  'h1',
  'h2',
  'h3',
  'h4',
  'ul',
  'ol',
  'li',
  'strong',
  'em',
  'code',
  'pre',
  'blockquote',
  'a',
  'hr',
]

const isSafeExternalUrl = (value: string) => {
  try {
    const url = new URL(value, window.location.origin)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

const escapeHtml = (value: string) =>
  value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')

export const renderSafeMarkdown = (content: string): string => {
  const renderer = new marked.Renderer()
  renderer.html = ({ text }) => escapeHtml(text)
  const parsed = marked.parse(content || '', {
    async: false,
    breaks: false,
    gfm: true,
    renderer,
  }) as string
  const sanitized = DOMPurify.sanitize(parsed, {
    ALLOWED_TAGS: allowedTags,
    ALLOWED_ATTR: ['href', 'title'],
    ALLOW_DATA_ATTR: false,
  })

  const document = new DOMParser().parseFromString(String(sanitized), 'text/html')
  document.querySelectorAll('a').forEach((link) => {
    const href = link.getAttribute('href') || ''
    if (!isSafeExternalUrl(href)) {
      link.removeAttribute('href')
      return
    }
    link.setAttribute('target', '_blank')
    link.setAttribute('rel', 'noopener noreferrer')
  })
  return document.body.innerHTML
}
