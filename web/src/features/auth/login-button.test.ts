import { createElement } from 'react'
import type { ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as loginButton from './login-button'
import { LoginButton } from './login-button'

const useAuthMethodsMock = vi.fn()

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, options?: { name?: string }) => {
        if (key === 'loginButton.loading') {
          return 'Loading...'
        }
        if (key === 'loginButton.loginWith') {
          return `Login with ${options?.name ?? ''}`.trim()
        }
        return key
      },
    }),
  }
})

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: ReactNode }) => createElement('button', { type: 'button' }, children),
}))

vi.mock('./use-auth-methods', () => ({
  useAuthMethods: (...args: unknown[]) => useAuthMethodsMock(...args),
}))

/**
 * LoginButton renders OAuth login actions from backend-provided auth methods.
 */
describe('login-button module exports', () => {
  beforeEach(() => {
    useAuthMethodsMock.mockReset()
  })

  it('exports LoginButton component', () => {
    expect(loginButton.LoginButton).toBeTypeOf('function')
  })

  it('renders WeCom provider logo and login copy', () => {
    useAuthMethodsMock.mockReturnValue({ data: [], isLoading: false })

    const html = renderToStaticMarkup(
      createElement(LoginButton, {
        methods: [
          {
            id: 'wechatwork',
            methodType: 'OAUTH_REDIRECT',
            provider: 'WeChatWork',
            displayName: 'WeCom',
            actionUrl: '/oauth/wechatwork',
          },
        ],
      }),
    )

    expect(html).toContain('/wechatwork-logo.svg')
    expect(html).toContain('Login with WeCom')
    expect(useAuthMethodsMock).not.toHaveBeenCalled()
  })
})
