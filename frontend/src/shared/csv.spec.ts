import { describe, expect, it } from 'vitest'
import { csvFileName, safeFileName, toCsv, withBom } from './csv'

describe('toCsv', () => {
  it('머리글과 행을 CRLF로 잇는다', () => {
    expect(toCsv(['a', 'b'], [[1, 2], [3, 4]])).toBe('a,b\r\n1,2\r\n3,4')
  })

  it('쉼표·따옴표·개행이 있으면 감싸고 따옴표는 두 번 쓴다', () => {
    expect(toCsv(['x'], [['a,b']])).toBe('x\r\n"a,b"')
    expect(toCsv(['x'], [['그는 "예"라고 했다']])).toBe('x\r\n"그는 ""예""라고 했다"')
    expect(toCsv(['x'], [['첫 줄\n둘째 줄']])).toBe('x\r\n"첫 줄\n둘째 줄"')
  })

  it('필요 없으면 감싸지 않는다 — 텍스트 편집기에서 읽기 쉽게', () => {
    expect(toCsv(['x'], [['보통 값']])).toBe('x\r\n보통 값')
  })

  it('null과 undefined는 빈 칸이 된다', () => {
    expect(toCsv(['a', 'b'], [[null, undefined]])).toBe('a,b\r\n,')
  })

  it('boolean은 Y/N으로 적는다', () => {
    expect(toCsv(['x'], [[true], [false]])).toBe('x\r\nY\r\nN')
  })

  it('행이 없어도 머리글은 남는다', () => {
    expect(toCsv(['a', 'b'], [])).toBe('a,b')
  })
})

describe('파일명', () => {
  it('경로·특수문자를 지우고 한글은 남긴다', () => {
    expect(safeFileName('AEGIS 2단계')).toBe('AEGIS-2단계')
    expect(safeFileName('a/b\\c:d')).toBe('a-b-c-d')
    expect(safeFileName('  ..좋은 이름..  ')).toBe('..좋은-이름..')
  })

  it('남는 글자가 없으면 기본값을 쓴다', () => {
    expect(safeFileName('///')).toBe('export')
    expect(safeFileName('   ')).toBe('export')
  })

  it('프로젝트명·화면·날짜를 조합한다', () => {
    expect(csvFileName('AEGIS', 'wbs', new Date('2026-09-02T05:00:00Z')))
      .toBe('AEGIS-wbs-2026-09-02.csv')
  })
})

describe('withBom', () => {
  it('UTF-8 BOM으로 시작한다 — 없으면 Excel이 한글을 깨뜨린다', () => {
    const out = withBom('이름,값')
    expect(out.charCodeAt(0)).toBe(0xfeff)
    expect(out.slice(1)).toBe('이름,값')
  })

  it('BOM은 한 글자만 붙는다', () => {
    expect(withBom('x').length).toBe(2)
  })
})
