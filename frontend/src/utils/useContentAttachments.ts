import { ref, type Ref } from 'vue'
import type { ContentFile, NewContentFile, RichContent } from '../services/investmentStrategyApi'
import { collectImageReferences } from './journalContent'

type PendingFile = { key: string; file: File; type: 'IMAGE' | 'FILE' }

export function useContentAttachments(content: Ref<RichContent>) {
  const existing = ref<ContentFile[]>([])
  const pending = ref<PendingFile[]>([])
  const error = ref('')

  function addImage(file: File, key: string) {
    if (existing.value.length + pending.value.length >= 30) {
      error.value = '첨부파일은 30개까지 가능합니다.'
      removeImageNode('uploadKey', key)
      return
    }
    pending.value.push({ key, file, type: 'IMAGE' })
  }
  function addFiles(event: Event) {
    const input = event.target as HTMLInputElement
    for (const file of Array.from(input.files || [])) {
      if (existing.value.length + pending.value.length >= 30) {
        error.value = '첨부파일은 30개까지 가능합니다.'
        break
      }
      pending.value.push({ key: crypto.randomUUID(), file, type: 'FILE' })
    }
    input.value = ''
  }
  function removeImageNode(attribute: 'fileId' | 'uploadKey', value: string | number) {
    const copy = structuredClone(content.value)
    function clean(node: Record<string, any>) {
      if (!Array.isArray(node.content)) return
      node.content = node.content.filter(
        (child: Record<string, any>) =>
          !(child.type === 'journalImage' && String(child.attrs?.[attribute]) === String(value)),
      )
      node.content.forEach(clean)
    }
    clean(copy)
    content.value = copy
  }
  function removeExisting(file: ContentFile) {
    if (file.fileType === 'IMAGE') removeImageNode('fileId', file.fileId)
    existing.value = existing.value.filter((item) => item.fileId !== file.fileId)
  }
  function removePending(item: PendingFile) {
    if (item.type === 'IMAGE') removeImageNode('uploadKey', item.key)
    pending.value = pending.value.filter((other) => other.key !== item.key)
  }
  function submission() {
    const refs = collectImageReferences(content.value)
    pending.value = pending.value.filter(
      (item) => item.type !== 'IMAGE' || refs.uploadKeys.has(item.key),
    )
    existing.value = existing.value.filter(
      (item) => item.fileType !== 'IMAGE' || refs.fileIds.has(item.fileId),
    )
    const newFiles: NewContentFile[] = pending.value.map((item, index) => ({
      uploadKey: item.key,
      fileType: item.type,
      originalFileName: item.file.name,
      sortOrder: existing.value.length + index,
    }))
    return {
      retainedFileIds: existing.value.map((item) => item.fileId),
      newFiles,
      files: pending.value.map((item) => item.file),
    }
  }
  return { existing, pending, error, addImage, addFiles, removeExisting, removePending, submission }
}
