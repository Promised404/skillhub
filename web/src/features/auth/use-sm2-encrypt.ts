import { sm2 } from 'sm-crypto'

/**
 * Encrypts a password with SM2 public key for secure transmission.
 * Plaintext format: `password-timestamp` to enable replay attack prevention on SSO side.
 */
export function encryptPassword(password: string, publicKey: string): string {
  if (!publicKey) {
    throw new Error('SM2 public key is required')
  }
  const timestamp = Date.now()
  const plaintext = `${password}-${timestamp}`
  const encrypted = sm2.doEncrypt(plaintext, publicKey)
  return encrypted
}
