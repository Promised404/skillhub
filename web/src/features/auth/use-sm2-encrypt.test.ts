import { describe, it, expect, vi } from 'vitest'
import { encryptPassword } from './use-sm2-encrypt'

describe('encryptPassword', () => {
  it('should return encrypted string', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-05-31T12:00:00Z'))
    // Use a valid SM2 public key (uncompressed point, 04 + 64 bytes X + 64 bytes Y)
    const publicKey = '04' + 'b'.repeat(128)
    const result = encryptPassword('mypassword', publicKey)
    expect(result).toBeTruthy()
    expect(typeof result).toBe('string')
    expect(result.length).toBeGreaterThan(0)
    vi.useRealTimers()
  })

  it('should throw if publicKey is empty', () => {
    expect(() => encryptPassword('mypassword', '')).toThrow('SM2 public key is required')
  })
})
