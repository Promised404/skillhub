import md5 from 'js-md5'
import { sm2 } from 'sm-crypto'

/**
 * Encrypts a password with SM2 public key for secure transmission.
 * Matches the OMS login encryption: MD5(password)-timestamp, then SM2 encrypt.
 */
export function encryptPassword(password: string, publicKey: string): string {
  if (!publicKey) {
    throw new Error('SM2 public key is required')
  }
  const timestamp = Date.now()
  const plaintext = `${md5(password)}-${timestamp}`
  const encrypted = sm2.doEncrypt(plaintext, publicKey, 1)
  return '04' + encrypted
}
