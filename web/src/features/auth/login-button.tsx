import { useTranslation } from 'react-i18next'
import type { AuthMethod } from '@/api/types'
import { Button } from '@/shared/ui/button'
import { useAuthMethods } from './use-auth-methods'

interface LoginButtonProps {
  returnTo?: string
  methods?: AuthMethod[]
}

/**
 * Returns the appropriate icon for a given OAuth provider.
 */
function OAuthIcon({ provider }: { provider: string }) {
  const normalizedProvider = provider.toLowerCase()
  return (
    <img
      src={`/${normalizedProvider}-logo.svg`}
      alt={provider}
      className="w-5 h-5 mr-3"
    />
  )
}

/**
 * Renders OAuth login buttons from the auth-method catalog returned by the backend.
 */
export function LoginButton({ returnTo, methods }: LoginButtonProps) {
  if (methods) {
    return <OAuthLoginButtons methods={methods} />
  }

  return <CatalogLoginButton returnTo={returnTo} />
}

function CatalogLoginButton({ returnTo }: { returnTo?: string }) {
  const { t } = useTranslation()
  const { data, isLoading } = useAuthMethods(returnTo)
  const authMethods = data ?? []

  const providers = authMethods.filter((method) => method.methodType === 'OAUTH_REDIRECT')

  if (isLoading) {
    return (
      <div className="space-y-3">
        <Button className="w-full h-12" disabled>
          <div className="w-5 h-5 rounded-full animate-shimmer mr-3" />
          {t('loginButton.loading')}
        </Button>
      </div>
    )
  }

  return <OAuthLoginButtons methods={providers} />
}

function OAuthLoginButtons({ methods }: { methods: AuthMethod[] }) {
  const { t } = useTranslation()
  const providers = methods.filter((method) => method.methodType === 'OAUTH_REDIRECT')

  return (
    <div className="space-y-3">
      {providers.map((provider) => (
        <Button
          key={provider.id}
          className="w-full h-12 text-base"
          variant="outline"
          onClick={() => {
            window.location.href = provider.actionUrl
          }}
        >
          <OAuthIcon provider={provider.provider} />
          {t('loginButton.loginWith', { name: provider.displayName })}
        </Button>
      ))}
    </div>
  )
}
