import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authApi, getDirectAuthRuntimeConfig } from '@/api/client'
import { ApiError } from '@/shared/lib/api-error'
import type { LocalLoginRequest, User } from '@/api/types'
import { clearSessionScopedQueries } from '@/features/notification/notification-session'
import { encryptPassword } from './use-sm2-encrypt'
import { getPrivateSsoRuntimeConfig } from './use-private-sso-config'

export function usePasswordLogin() {
  const queryClient = useQueryClient()
  const directAuthConfig = getDirectAuthRuntimeConfig()
  const privateSsoConfig = getPrivateSsoRuntimeConfig()

  return useMutation({
    mutationFn: (request: LocalLoginRequest) => {
      if (directAuthConfig.enabled && directAuthConfig.provider) {
        let password = request.password
        if (privateSsoConfig.sm2PublicKey) {
          password = encryptPassword(request.password, privateSsoConfig.sm2PublicKey)
        }
        return authApi.directLogin(directAuthConfig.provider, {
          username: request.username,
          password,
          twoFactorCode: request.twoFactorCode,
        })
      }
      return authApi.localLogin(request)
    },
    onSuccess: (user) => {
      clearSessionScopedQueries(queryClient)
      queryClient.setQueryData<User | null>(['auth', 'me'], user)
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        return
      }
    },
  })
}
