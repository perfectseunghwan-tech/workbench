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
const tableCellStyleAttributes = {
  backgroundColor: {
    default: null,
    parseHTML: (element: HTMLElement) => element.style.backgroundColor || null,
    rendered: false,
  },
  borderColor: {
    default: null,
    parseHTML: (element: HTMLElement) => element.style.borderColor || null,
    rendered: false,
  },
  borderWidth: {
    default: null,
    parseHTML: (element: HTMLElement) => element.style.borderWidth || null,
    rendered: false,
  },
  borderStyle: {
    default: null,
    parseHTML: (element: HTMLElement) => element.style.borderStyle || null,
    rendered: false,
  },
}
function cellStyle(attributes: Record<string, unknown>) {
  return [
    attributes.backgroundColor ? `background-color: ${attributes.backgroundColor}` : '',
    attributes.borderColor ? `border-color: ${attributes.borderColor}` : '',
    attributes.borderWidth ? `border-width: ${attributes.borderWidth}` : '',
    attributes.borderStyle ? `border-style: ${attributes.borderStyle}` : '',
  ]
    .filter(Boolean)
    .join('; ')
}
const StyledTableCell = TableCell.extend({
  addAttributes() {
    return { ...this.parent?.(), ...tableCellStyleAttributes }
  },
  renderHTML({ HTMLAttributes, node }) {
    const style = cellStyle(node.attrs)
    return [
      'td',
      mergeAttributes(this.options.HTMLAttributes, HTMLAttributes, style ? { style } : {}),
      0,
    ]
  },
})
const StyledTableHeader = TableHeader.extend({
  addAttributes() {
    return { ...this.parent?.(), ...tableCellStyleAttributes }
  },
  renderHTML({ HTMLAttributes, node }) {
    const style = cellStyle(node.attrs)
    return [
      'th',
      mergeAttributes(this.options.HTMLAttributes, HTMLAttributes, style ? { style } : {}),
      0,
    ]
  },
})
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
    StyledTableHeader,
    StyledTableCell,
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

function setCellAttribute(name: string, value: string | null) {
  editor.value?.chain().focus().setCellAttribute(name, value).run()
}

function setCellColor(name: 'backgroundColor' | 'borderColor', event: Event) {
  setCellAttribute(name, (event.target as HTMLInputElement).value)
}

function currentCellAttribute(name: string, fallback: string) {
  const currentEditor = editor.value
  if (!currentEditor) return fallback
  const nodeName = currentEditor.isActive('tableHeader') ? 'tableHeader' : 'tableCell'
  return String(currentEditor.getAttributes(nodeName)[name] || fallback)
}

function clearCellStyle() {
  const chain = editor.value?.chain().focus()
  if (!chain) return
  chain
    .setCellAttribute('backgroundColor', null)
    .setCellAttribute('borderColor', null)
    .setCellAttribute('borderWidth', null)
    .setCellAttribute('borderStyle', null)
    .run()
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
      ><template v-if="editor.isActive('table')"
        ><span class="editor-toolbar-divider" aria-hidden="true"></span
        ><label class="editor-color-control" title="선택한 셀 배경색">
          배경
          <input
            type="color"
            :value="currentCellAttribute('backgroundColor', '#ffffff')"
            aria-label="셀 배경색"
            @input="setCellColor('backgroundColor', $event)" /></label
        ><label class="editor-color-control" title="선택한 셀 테두리 색">
          선
          <input
            type="color"
            :value="currentCellAttribute('borderColor', '#cbd5e1')"
            aria-label="셀 테두리 색"
            @input="setCellColor('borderColor', $event)" /></label
        ><select
          :value="currentCellAttribute('borderWidth', '1px')"
          aria-label="셀 테두리 굵기"
          title="선택한 셀 테두리 굵기"
          @change="setCellAttribute('borderWidth', ($event.target as HTMLSelectElement).value)"
        >
          <option value="1px">선 1px</option>
          <option value="2px">선 2px</option>
          <option value="3px">선 3px</option>
          <option value="4px">선 4px</option></select
        ><select
          :value="currentCellAttribute('borderStyle', 'solid')"
          aria-label="셀 테두리 모양"
          title="선택한 셀 테두리 모양"
          @change="setCellAttribute('borderStyle', ($event.target as HTMLSelectElement).value)"
        >
          <option value="solid">실선</option>
          <option value="dashed">파선</option>
          <option value="dotted">점선</option>
          <option value="double">이중선</option></select
        ><button type="button" aria-label="셀 서식 초기화" @click="clearCellStyle">
          셀 서식 초기화
        </button></template
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
