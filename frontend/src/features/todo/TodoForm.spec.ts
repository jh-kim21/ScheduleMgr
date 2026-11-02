// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TodoForm from './TodoForm.vue'
import type { TodoItem, TodoItemInput } from '../../api/todoApi'
import type { ProjectMember } from '../../api/memberApi'

/** ModalDialog가 body로 teleport 하므로 document.body를 직접 조회한다. */
const members: ProjectMember[] = [
  { id: 3, name: '김재학', email: null, position: null, createdAt: '', updatedAt: '' },
]

describe('TodoForm', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  function open(
    onSubmit: (input: TodoItemInput) => Promise<boolean>,
    extra: { editing?: TodoItem | null; error?: string | null } = {},
  ) {
    wrapper = mount(TodoForm, { props: { editing: null, members, onSubmit, ...extra } })
    return wrapper
  }

  function type(el: HTMLInputElement | HTMLTextAreaElement, value: string) {
    el.value = value
    el.dispatchEvent(new Event('input'))
  }

  const titleInput = () => document.body.querySelector<HTMLInputElement>('.todo-form input[type="text"]')!
  const assigneeSelect = () => document.body.querySelectorAll<HTMLSelectElement>('.todo-form select')[1]

  async function submit() {
    document.body.querySelector('form')!.dispatchEvent(new Event('submit'))
    await new Promise((resolve) => setTimeout(resolve, 0))
  }

  it('저장이 성공하면 close를 emit 한다 (대조군)', async () => {
    const onSubmit = vi.fn().mockResolvedValue(true)
    open(onSubmit)
    type(titleInput(), '계약서 검토')
    await wrapper!.vm.$nextTick()

    await submit()

    expect(onSubmit).toHaveBeenCalledTimes(1)
    expect(wrapper!.emitted('close')).toHaveLength(1)
  })

  it('저장이 거부되면 대화상자가 열린 채 오류가 보이고 입력값이 남는다', async () => {
    const onSubmit = vi.fn().mockResolvedValue(false)
    open(onSubmit, { error: '담당자를 찾을 수 없습니다' })
    type(titleInput(), '계약서 검토')
    await wrapper!.vm.$nextTick()

    await submit()

    expect(onSubmit).toHaveBeenCalledTimes(1)
    expect(wrapper!.emitted('close')).toBeUndefined()
    expect(document.body.querySelector('[role="dialog"]')).not.toBeNull()
    expect(document.body.querySelector('[role="alert"]')?.textContent).toContain('담당자를 찾을 수 없습니다')
    expect(titleInput().value).toBe('계약서 검토')
  })

  it('담당자를 미지정으로 두면 null이 나간다 (0이 아님)', async () => {
    const onSubmit = vi.fn().mockResolvedValue(true)
    open(onSubmit)
    type(titleInput(), '주간 보고')
    await wrapper!.vm.$nextTick()

    await submit()

    const input = onSubmit.mock.calls[0][0] as TodoItemInput
    expect(input.assigneeMemberId).toBeNull()
    expect(input.dueDate).toBeNull()
    expect(input.memo).toBeNull()
    expect(input.priority).toBe('MEDIUM')
  })

  it('구성원을 고르면 그 id가 숫자로 나가고, 다시 미지정으로 돌리면 null이다', async () => {
    const onSubmit = vi.fn().mockResolvedValue(true)
    open(onSubmit)
    type(titleInput(), '회의 준비')

    const select = assigneeSelect()
    select.value = select.options[1].value
    select.dispatchEvent(new Event('change'))
    await wrapper!.vm.$nextTick()
    await submit()
    expect((onSubmit.mock.calls[0][0] as TodoItemInput).assigneeMemberId).toBe(3)

    select.value = select.options[0].value
    select.dispatchEvent(new Event('change'))
    await wrapper!.vm.$nextTick()
    await submit()
    expect((onSubmit.mock.calls[1][0] as TodoItemInput).assigneeMemberId).toBeNull()
  })

  it('제목이 비어 있으면 제출하지 않는다', async () => {
    const onSubmit = vi.fn().mockResolvedValue(true)
    open(onSubmit)

    await submit()

    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('제목은 문맥을 붙여 계산한다', () => {
    open(vi.fn())
    expect(document.body.querySelector('[role="dialog"]')?.textContent).toContain('TODO 추가')
  })
})
