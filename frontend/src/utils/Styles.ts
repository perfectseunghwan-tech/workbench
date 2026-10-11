/**
 * PrimeVue Pass Through styles shared by every screen.
 * Screen-specific layout stays in each view; controls keep one visual language here.
 */
export const btnStyles = {
  body: {
    root: {
      class:
        'min-h-10 rounded-lg border border-blue-600 bg-blue-600 px-4 font-semibold text-white shadow-none transition-colors hover:border-blue-700 hover:bg-blue-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-500 disabled:cursor-not-allowed disabled:opacity-50',
    },
    label: { class: 'font-semibold' },
  },
  secondary: {
    root: {
      class:
        'min-h-10 rounded-lg border border-slate-300 bg-white px-4 font-semibold text-slate-700 shadow-none transition-colors hover:border-slate-400 hover:bg-slate-50',
    },
    label: { class: 'font-semibold' },
  },
  icon: {
    root: {
      class:
        'h-9 w-9 rounded-lg border border-slate-200 bg-white p-0 text-slate-600 shadow-none transition-colors hover:border-blue-200 hover:bg-blue-50 hover:text-blue-700',
    },
  },
} as const

export const selectStyles = {
  body: {
    root: {
      class:
        'min-h-10 rounded-lg border border-slate-300 bg-white shadow-none transition-colors hover:border-blue-400 data-[p-focused=true]:border-blue-500 data-[p-focused=true]:ring-2 data-[p-focused=true]:ring-blue-100',
    },
    label: { class: 'flex items-center px-3 py-2 text-sm text-slate-800' },
    dropdown: { class: 'w-9 text-slate-500' },
    overlay: { class: 'rounded-lg border border-slate-200 shadow-xl' },
    option: { class: 'rounded-md px-3 py-2 text-sm' },
  },
} as const

export const inputTextStyles = {
  body: {
    root: {
      class:
        'min-h-10 rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-800 shadow-none outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100',
    },
  },
} as const

export const paginatorStyles = {
  root: { class: 'gap-1 border-0 bg-transparent px-3 py-3' },
  content: { class: 'gap-1' },
  page: { class: 'h-8 min-w-8 rounded-md text-sm' },
  first: { class: 'h-8 min-w-8 rounded-md' },
  prev: { class: 'h-8 min-w-8 rounded-md' },
  next: { class: 'h-8 min-w-8 rounded-md' },
  last: { class: 'h-8 min-w-8 rounded-md' },
} as const

export const dataTableStyles = {
  root: { class: 'overflow-hidden rounded-xl border border-slate-200 bg-white' },
  table: { class: 'w-full border-collapse text-sm' },
  headerRow: { class: 'bg-blue-50 text-blue-950' },
  headerCell: {
    class:
      'h-12 border-b border-blue-100 px-4 text-center text-xs font-bold text-blue-950',
  },
  bodyCell: { class: 'border-b border-slate-100 px-4 py-3 text-slate-700' },
} as const

export const detailDialogStyles = {
  root: { class: 'aqms-detail-dialog overflow-hidden rounded-2xl shadow-2xl' },
  header: { class: 'border-b border-slate-200 px-6 py-5' },
  title: { class: 'text-xl font-bold text-slate-900' },
  content: { class: 'px-6 py-5' },
  footer: { class: 'border-t border-slate-200 px-6 py-4' },
} as const
