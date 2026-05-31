declare module 'sm-crypto' {
  export const sm2: {
    doEncrypt(data: string, publicKey: string, cipherMode?: number): string
    doDecrypt(data: string, privateKey: string, cipherMode?: number): string
    doSignature(data: string, privateKey: string, options?: Record<string, unknown>): string
    doVerifySignature(data: string, signature: string, publicKey: string, options?: Record<string, unknown>): boolean
    getPublicKeyFromPrivateKey(privateKey: string): string
    generateKeyPairHex(): { privateKey: string; publicKey: string }
  }

  export const sm3: {
    sm3(data: string | number[]): string
  }

  export const sm4: {
    encrypt(data: string, key: string, options?: Record<string, unknown>): string
    decrypt(data: string, key: string, options?: Record<string, unknown>): string
  }
}
