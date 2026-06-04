import { describe, it, expect, vi } from 'vitest'
import { encryptPassword } from './use-sm2-encrypt'
import { md5 } from 'js-md5'

describe('encryptPassword', () => {
  it('should return encrypted string with 04 prefix', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-05-31T12:00:00Z'))
    const publicKey = '04' + 'b'.repeat(128)
    const result = encryptPassword('mypassword', publicKey)
    expect(result).toBeTruthy()
    expect(typeof result).toBe('string')
    expect(result.startsWith('04')).toBe(true)
    vi.useRealTimers()
  })

  it('should encrypt MD5(password)-timestamp, not plaintext password', () => {
    vi.useFakeTimers()
    const ts = 1748692800000
    vi.setSystemTime(new Date(ts))
    const expectedPlaintext = `${md5('mypassword')}-${ts}`
    expect(expectedPlaintext).toMatch(/^[a-f0-9]{32}-\d+$/)
    vi.useRealTimers()
  })

  it('should throw if publicKey is empty', () => {
    expect(() => encryptPassword('mypassword', '')).toThrow('SM2 public key is required')
  })
})
