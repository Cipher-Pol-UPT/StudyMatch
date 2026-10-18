// @vitest-environment node
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { ERROR_CODES, isBackendError } from '../src/api/errors';
import * as enums from '../src/types/enums';
import en from '../src/i18n/en.json';
import pt from '../src/i18n/pt.json';

const backend = new URL('../../backend/src/main/java/com/studymatch/', import.meta.url);
const enumNamespaces: Record<string, keyof typeof en> = {
  AccountStatus:'accountStatuses', StudyRoomStatus:'studyRoomStatuses', Role:'roles', Language:'languages',
  StatuteType:'statutes', AssignmentType:'assignmentTypes', GroupStatus:'groupStatuses', EnrolmentStatus:'enrolmentStatuses',
  TutorApplicationStatus:'tutorApplicationStatuses', Achievement:'achievements',
};
const keys = (value: unknown, prefix = ''): string[] => Object.entries(value as Record<string, unknown>)
  .flatMap(([key, entry]) => typeof entry === 'string' ? [prefix + key] : keys(entry, prefix + key + '.'));

describe('backend contracts', () => {
  it('mirrors every Java enum exactly', () => {
    for (const file of readdirSync(new URL('enums/', backend))) {
      if (!file.endsWith('.java')) continue;
      const name = file.slice(0,-5) as keyof typeof enums;
      const source = readFileSync(new URL('enums/' + file, backend), 'utf8');
      const values = source.split('{')[1].match(/\b[A-Z][A-Z_]+\b/g);
      expect([...enums[name]]).toEqual(values);
    }
    expect(enums.Role).toEqual(['ADMIN','STAFF','TEACHER','STUDENT']);
  });

  it('mirrors backend business errors and translates every enum and error', () => {
    const source = readFileSync(new URL('exceptions/ErrorCode.java', backend), 'utf8');
    expect([...ERROR_CODES]).toEqual([...source.matchAll(/^\s*([A-Z][A-Z_]+)\(/gm)].map(match => match[1]));
    for (const code of ERROR_CODES) { expect(en.errors[code]).toBeTruthy(); expect(pt.errors[code]).toBeTruthy(); }
    for (const [name, namespace] of Object.entries(enumNamespaces)) {
      for (const code of enums[name as keyof typeof enums]) {
        expect((en[namespace] as Record<string, unknown>)[code]).toBeTruthy();
        expect((pt[namespace] as Record<string, unknown>)[code]).toBeTruthy();
      }
    }
  });

  it('keeps language keys and interpolation parameters aligned', () => {
    expect(keys(en).sort()).toEqual(keys(pt).sort());
    const compare = (a: unknown, b: unknown) => {
      if (typeof a === 'string' && typeof b === 'string') expect(a.match(/{{\w+}}/g) ?? []).toEqual(b.match(/{{\w+}}/g) ?? []);
      else for (const [key, value] of Object.entries(a as Record<string, unknown>)) compare(value, (b as Record<string, unknown>)[key]);
    };
    compare(en,pt);
  });

  it('accepts omitted params and rejects malformed error bodies', () => {
    expect(isBackendError({code:'STUDYROOM_FULL'})).toBe(true);
    expect(isBackendError({code:'CAPACITY_BELOW_PARTICIPANTS',params:{participants:5}})).toBe(true);
    expect(isBackendError({code:'VALIDATION_FAILED',fieldErrors:[{field:'password',code:'SIZE',params:{min:8,max:20}}]})).toBe(true);
    for (const value of [null, [], {code:'unknown'}, {code:'STUDYROOM_FULL',params:[]}, {code:'STUDYROOM_FULL',params:null},
      {code:'VALIDATION_FAILED',fieldErrors:[{field:5,code:'SIZE'}]}, {code:'VALIDATION_FAILED',fieldErrors:[{field:'password',code:'size'}]}]) {
      expect(isBackendError(value)).toBe(false);
    }
  });

  it('does not contain network adapters, business rule helpers or lint exclusions', () => {
    const directory = new URL('../src/studymatch-ui/',import.meta.url);
    const scan = (folder: string): string => readdirSync(folder,{withFileTypes:true}).map(entry => entry.isDirectory()
      ? scan(join(folder,entry.name)) : /\.tsx?$/.test(entry.name) ? readFileSync(join(folder,entry.name),'utf8') : '').join('\n');
    const source = scan(fileURLToPath(directory));
    expect(source).not.toMatch(/\b(fetch|XMLHttpRequest|AuthAdapter|MOCK_USERS|scorePersonality|DEFAULT_PERSONALITY_QUESTIONS|studyRoomAccess|ENTER_EARLY_MIN|MEET_URL_PATTERN)\b/);
    const config = readFileSync(new URL('../eslint.config.js',import.meta.url),'utf8');
    expect(config).not.toContain('studymatch-ui/**');
    expect(source).not.toContain('eslint-disable');
  });
});
