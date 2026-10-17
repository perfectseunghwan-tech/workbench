<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { Node, mergeAttributes } from '@tiptap/core'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { Table } from '@tiptap/extension-table'
import TableRow from '@tiptap/extension-table-row'
import TableHeader from '@tiptap/extension-table-header'
import TableCell from '@tiptap/extension-table-cell'

interface EditorDocument {
  type: 'doc'
  content?: Record<string, unknown>[]
}

const props = withDefaults(
  defineProps<{
    modelValue: EditorDocument
    readonly?: boolean
    imagesEnabled?: boolean
    journalId?: number
    strategyId?: number
    strategyPostId?: number
  }>(),
  { readonly: false, imagesEnabled: false },
)
const emit = defineEmits<{
  (e: 'update:modelValue', value: EditorDocument): void
  (e: 'image-selected', file: File, key: string): void
}>()

const objectUrls = new Set<string>()
const imageNode = Node.create({
  name: 'journalImage',
  group: 'block',
  atom: true,
  addAttributes() {
    return {
      fileId: { default: null },
      uploadKey: { default: null },
      previewUrl: { default: null },
      alt: { default: '' },
      title: { default: null },
    }
  },
  parseHTML() {
    return [{ tag: 'img[data-journal-image]' }]
  },
  renderHTML({ HTMLAttributes }) {
    const src =
      HTMLAttributes.previewUrl ||
      (HTMLAttributes.fileId
        ? props.journalId
          ? `/api/investment-journals/${props.journalId}/files/${HTMLAttributes.fileId}`
          : props.strategyId && props.strategyPostId
            ? `/api/investment-strategies/${props.strategyId}/posts/${props.strategyPostId}/files/${HTMLAttributes.fileId}`
            : props.strategyId
              ? `/api/investment-strategies/${props.strategyId}/files/${HTMLAttributes.fileId}`
              : ''
        : '')
    return [
      'img',
      mergeAttributes(
        { 'data-journal-image': '', src, alt: HTMLAttributes.alt || '' },
        HTMLAttributes,
      ),
    ]
  },
})

function insertImageFile(file: File) {
  if (!props.imagesEnabled || !file.type.startsWith('image/')) return false
  const key = crypto.randomUUID()
  const url = URL.createObjectURL(file)
  objectUrls.add(url)
  editor.value
    ?.chain()
    .focus()
    .insertContent({
      type: 'journalImage',
      attrs: { uploadKey: key, previewUrl: url, alt: file.name },
    })
    .run()
  emit('image-selected', file, key)
  return true
}

function cleanupUrls(value: EditorDocument) {
  const text = JSON.stringify(value)
  for (const url of objectUrls)
    if (!text.includes(url)) {
      URL.revokeObjectURL(url)
      objectUrls.delete(url)
    }
}

const editor = useEditor({
  content: props.modelValue,
  editable: !props.readonly,
  extensions: [
    StarterKit,
    Table.configure({ resizable: true }),
    TableRow,
    TableHeader,
    TableCell,
    ...(props.imagesEnabled ? [imageNode] : []),
  ],
  editorProps: {
    handlePaste: (_view, event) => {
      const file = Array.from(event.clipboardData?.files || []).find((item) =>
        item.type.startsWith('image/'),
      )
      return file ? insertImageFile(file) : false
    },
    handleDrop: (_view, event) => {
      const file = Array.from(event.dataTransfer?.files || []).find((item) =>
        item.type.startsWith('image/'),
      )
      return file ? insertImageFile(file) : false
    },
  },
  onUpdate: ({ editor }) => {
    const value = editor.getJSON() as EditorDocument
    cleanupUrls(value)
    emit('update:modelValue', value)
  },
})

watch(
  () => props.modelValue,
  (value) => {
    if (
      editor.value &&
      !editor.value.isDestroyed &&
      JSON.stringify(editor.value.getJSON()) !== JSON.stringify(value)
    ) {
      editor.value.commands.setContent(value)
    }
    cleanupUrls(value)
  },
  { deep: true },
)
watch(
  () => props.readonly,
  (value) => editor.value?.setEditable(!value),
)

function imageSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) insertImageFile(file)
  input.value = ''
}

function editAlt() {
  const current = String(editor.value?.getAttributes('journalImage').alt || '')
  const alt = window.prompt('이미지 대체 텍스트', current)
  if (alt !== null) editor.value?.chain().focus().updateAttributes('journalImage', { alt }).run()
}

onBeforeUnmount(() => {
  objectUrls.forEach((url) => URL.revokeObjectURL(url))
  objectUrls.clear()
  editor.value?.destroy()
})
</script>

<template>
  <div class="rich-editor" :class="{ readonly }">
    <div v-if="!readonly && editor" class="editor-toolbar" role="toolbar" aria-label="본문 서식">
      <button type="button" aria-label="문단" @click="editor.chain().focus().setParagraph().run()">
        문단</button
      ><button
        type="button"
        aria-label="제목"
        :class="{ active: editor.isActive('heading', { level: 2 }) }"
        @click="editor.chain().focus().toggleHeading({ level: 2 }).run()"
      >
        제목</button
      ><button
        type="button"
        aria-label="굵게"
        :class="{ active: editor.isActive('bold') }"
        @click="editor.chain().focus().toggleBold().run()"
      >
        <b>B</b></button
      ><button
        type="button"
        aria-label="기울임"
        @click="editor.chain().focus().toggleItalic().run()"
      >
        <i>I</i></button
      ><button
        type="button"
        aria-label="취소선"
        @click="editor.chain().focus().toggleStrike().run()"
      >
        S</button
      ><button
        type="button"
        aria-label="글머리 목록"
        @click="editor.chain().focus().toggleBulletList().run()"
      >
        • 목록</button
      ><button
        type="button"
        aria-label="번호 목록"
        @click="editor.chain().focus().toggleOrderedList().run()"
      >
        1. 목록</button
      ><button
        type="button"
        aria-label="인용"
        @click="editor.chain().focus().toggleBlockquote().run()"
      >
        인용</button
      ><button
        type="button"
        aria-label="구분선"
        @click="editor.chain().focus().setHorizontalRule().run()"
      >
        구분선</button
      ><button
        type="button"
        aria-label="표 삽입"
        @click="editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()"
      >
        표</button
      ><button
        type="button"
        aria-label="행 추가"
        @click="editor.chain().focus().addRowAfter().run()"
      >
        +행</button
      ><button
        type="button"
        aria-label="열 추가"
        @click="editor.chain().focus().addColumnAfter().run()"
      >
        +열</button
      ><button type="button" aria-label="행 삭제" @click="editor.chain().focus().deleteRow().run()">
        -행</button
      ><button
        type="button"
        aria-label="열 삭제"
        @click="editor.chain().focus().deleteColumn().run()"
      >
        -열</button
      ><button
        type="button"
        aria-label="표 삭제"
        @click="editor.chain().focus().deleteTable().run()"
      >
        표 삭제</button
      ><button
        type="button"
        aria-label="헤더 셀 전환"
        @click="editor.chain().focus().toggleHeaderCell().run()"
      >
        헤더</button
      ><button
        type="button"
        aria-label="셀 병합"
        @click="editor.chain().focus().mergeCells().run()"
      >
        병합</button
      ><button type="button" aria-label="셀 분할" @click="editor.chain().focus().splitCell().run()">
        분할</button
      ><template v-if="imagesEnabled"
        ><label class="editor-file"
          >이미지<input
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp"
            @change="imageSelected" /></label
        ><button type="button" aria-label="이미지 대체 텍스트 수정" @click="editAlt">
          대체텍스트
        </button></template
      >
      <button type="button" aria-label="실행 취소" @click="editor.chain().focus().undo().run()">
        ↶</button
      ><button type="button" aria-label="다시 실행" @click="editor.chain().focus().redo().run()">
        ↷
      </button>
    </div>
    <EditorContent :editor="editor" />
  </div>
</template>
