export interface PrivateSsoRuntimeConfig {
  twoFactorEnabled: boolean
  sm2PublicKey: string
}

export function getPrivateSsoRuntimeConfig(): PrivateSsoRuntimeConfig {
  const config = typeof window !== 'undefined' ? (window.__SKILLHUB_RUNTIME_CONFIG__ ?? {}) : {}
  const twoFactorEnabled = config.authPrivateSsoTwoFactorEnabled?.trim().toLowerCase() === 'true'
  const sm2PublicKey = config.authPrivateSsoSm2PublicKey?.trim() ?? ''
  return { twoFactorEnabled, sm2PublicKey }
}
