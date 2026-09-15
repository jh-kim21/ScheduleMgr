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

/**
 * RFC 4180 파서 — 테스트 전용. 내보낸 CSV가 Excel 같은 수입 도구에서 *원래 값으로 되읽히는지*
 * 확인하려는 것이라, `toCsv`의 출력 문자열을 눈으로 비교하는 것만으로는 부족하다. 값 안의 줄바꿈이
 * 레코드를 쪼개는 사고는 escape 단계가 아니라 파싱 단계에서 드러난다.
 */
function parseCsv(text: string): string[][] {
  const rows: string[][] = [[]]
  let field = ''
  let quoted = false
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i]
    if (quoted) {
      if (ch === '"' && text[i + 1] === '"') {
        field += '"'
        i += 1
      } else if (ch === '"') {
        quoted = false
      } else {
        field += ch
      }
      continue
    }
    if (ch === '"') quoted = true
    else if (ch === ',') {
      rows[rows.length - 1].push(field)
      field = ''
    } else if (ch === '\r' && text[i + 1] === '\n') {
      rows[rows.length - 1].push(field)
      field = ''
      rows.push([])
      i += 1
    } else field += ch
  }
  rows[rows.length - 1].push(field)
  return rows
}

describe('여러 줄 값의 왕복', () => {
  it('값 안의 줄바꿈이 레코드를 쪼개지 않는다 — RAID 설명·대응 방안이 여러 줄이다', () => {
    const description = '첫 줄\n둘째 줄\n셋째 줄'
    const response = '대응 1\n대응 2'
    const csv = toCsv(
      ['종류', '설명', '대응/확인 방안'],
      [
        ['위험', description, response],
        ['이슈', '한 줄', ''],
      ],
    )

    const parsed = parseCsv(csv)
    expect(parsed).toHaveLength(3)
    expect(parsed[1]).toEqual(['위험', description, response])
    expect(parsed[2]).toEqual(['이슈', '한 줄', ''])
  })

  it('값 안의 CRLF도 레코드 구분자와 헷갈리지 않는다', () => {
    // 화면의 textarea 는 항상 \n 을 주지만, 손으로 고친 JSON 을 가져오면 \r\n 이 들어올 수 있다.
    const value = '앞줄\r\n뒷줄'
    const parsed = parseCsv(toCsv(['x', 'y'], [[value, '옆 칸']]))
    expect(parsed).toHaveLength(2)
    expect(parsed[1]).toEqual([value, '옆 칸'])
  })

  it('줄바꿈과 따옴표·쉼표가 한 값에 같이 있어도 보존된다', () => {
    const value = '그는 "예"라고 했다,\n그리고 나갔다'
    expect(parseCsv(toCsv(['x'], [[value]]))[1]).toEqual([value])
  })
})
