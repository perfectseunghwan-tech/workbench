import type { JournalContent } from '../services/investmentJournalApi'
export const emptyJournalContent = (): JournalContent => ({ type: 'doc', content: [] })
export function collectImageReferences(value: JournalContent) {
  const uploadKeys = new Set<string>()
  const fileIds = new Set<number>()
  function visit(node: any) {
    if (node?.type === 'journalImage') {
      if (node.attrs?.uploadKey) uploadKeys.add(node.attrs.uploadKey)
      if (Number(node.attrs?.fileId) > 0) fileIds.add(Number(node.attrs.fileId))
    }
    node?.content?.forEach(visit)
  }
  visit(value)
  return { uploadKeys, fileIds }
}
export function formatBytes(value: number | null) {
  if (value == null) return '—'
  if (value < 1024) return `${value} B`
  if (value < 1048576) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1048576).toFixed(1)} MB`
}
export function localDate() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
