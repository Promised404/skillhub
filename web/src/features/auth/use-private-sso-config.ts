export interface PrivateSsoRuntimeConfig {
  twoFactorEnabled: boolean
  sm2PublicKey: string
}

declare global {
  interface Window {
    __skillhub_runtime_config__?: Record<string, string | undefined>
  }
}

export function getPrivateSsoRuntimeConfig(): PrivateSsoRuntimeConfig {
  const config = typeof window !== 'undefined' ? (window.__skillhub_runtime_config__ ?? {}) : {}
  const twoFactorEnabled = config.authPrivateSsoTwoFactorEnabled?.trim().toLowerCase() === 'true'
  const sm2PublicKey = config.authPrivateSsoSm2PublicKey?.trim() ?? ''
  return { twoFactorEnabled, sm2PublicKey }
}
