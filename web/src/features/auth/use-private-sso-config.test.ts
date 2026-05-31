import { afterEach, beforeEach, describe, expect, it } from 'vitest'

const originalWindow = globalThis.window

function setMockWindow(runtimeConfig?: Record<string, string | undefined>) {
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    writable: true,
    value: {
      __skillhub_runtime_config__: runtimeConfig,
    },
  })
}

import { getPrivateSsoRuntimeConfig } from './use-private-sso-config'

beforeEach(() => {
  setMockWindow()
})

afterEach(() => {
  if (originalWindow) {
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      writable: true,
      value: originalWindow,
    })
    return
  }

  Reflect.deleteProperty(globalThis, 'window')
})

describe('getPrivateSsoRuntimeConfig', () => {
  it('should return twoFactorEnabled=false and empty sm2PublicKey by default', () => {
    const config = getPrivateSsoRuntimeConfig()
    expect(config.twoFactorEnabled).toBe(false)
    expect(config.sm2PublicKey).toBe('')
  })

  it('should return twoFactorEnabled=true when flag is set', () => {
    window.__skillhub_runtime_config__ = {
      authPrivateSsoTwoFactorEnabled: 'true',
      authPrivateSsoSm2PublicKey: 'test-public-key',
    }
    const config = getPrivateSsoRuntimeConfig()
    expect(config.twoFactorEnabled).toBe(true)
    expect(config.sm2PublicKey).toBe('test-public-key')
  })
})
