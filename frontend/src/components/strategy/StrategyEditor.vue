<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { Table } from '@tiptap/extension-table'
import TableRow from '@tiptap/extension-table-row'
import TableHeader from '@tiptap/extension-table-header'
import TableCell from '@tiptap/extension-table-cell'
import type { RichContent } from '../../services/investmentStrategyApi'
const props = withDefaults(defineProps<{ modelValue: RichContent; readonly?: boolean }>(), {
  readonly: false,
})
const emit = defineEmits<{ (e: 'update:modelValue', v: RichContent): void }>()
const editor = useEditor({
  content: props.modelValue,
  editable: !props.readonly,
  extensions: [StarterKit, Table.configure({ resizable: true }), TableRow, TableHeader, TableCell],
  onUpdate: ({ editor }) => emit('update:modelValue', editor.getJSON() as RichContent),
})
watch(
  () => props.modelValue,
  (v) => {
    if (editor.value && JSON.stringify(editor.value.getJSON()) !== JSON.stringify(v))
      editor.value.commands.setContent(v)
  },
  { deep: true },
)
watch(
  () => props.readonly,
  (v) => editor.value?.setEditable(!v),
)
onBeforeUnmount(() => editor.value?.destroy())
</script>
<template>
  <div class="strategy-editor" :class="{ readonly }">
    <div v-if="!readonly && editor" class="strategy-toolbar">
      <button type="button" @click="editor.chain().focus().setParagraph().run()">문단</button
      ><button type="button" @click="editor.chain().focus().toggleHeading({ level: 2 }).run()">
        제목</button
      ><button type="button" @click="editor.chain().focus().toggleBold().run()"><b>B</b></button
      ><button type="button" @click="editor.chain().focus().toggleItalic().run()"><i>I</i></button
      ><button type="button" @click="editor.chain().focus().toggleBulletList().run()">• 목록</button
      ><button type="button" @click="editor.chain().focus().toggleOrderedList().run()">
        1. 목록</button
      ><button type="button" @click="editor.chain().focus().toggleBlockquote().run()">인용</button
      ><button
        type="button"
        @click="editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()"
      >
        표</button
      ><button type="button" @click="editor.chain().focus().addRowAfter().run()">+행</button
      ><button type="button" @click="editor.chain().focus().addColumnAfter().run()">+열</button
      ><button type="button" @click="editor.chain().focus().deleteTable().run()">표 삭제</button
      ><button type="button" @click="editor.chain().focus().undo().run()">↶</button
      ><button type="button" @click="editor.chain().focus().redo().run()">↷</button>
    </div>
    <EditorContent :editor="editor" />
  </div>
</template>
