import { renderToStaticMarkup } from 'react-dom/server'
import type { AuthMethod } from '@/api/types'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const navigateMock = vi.fn()
const useSearchMock = vi.fn()
const getDirectAuthRuntimeConfigMock = vi.fn()
const useAuthMethodsMock = vi.fn()
const loginButtonMock = vi.fn(({ methods }: { methods?: AuthMethod[] }) => (
  <div data-testid="login-button-mock">
    {`login-button:${methods?.length ?? 0}:${methods?.[0]?.provider ?? 'none'}`}
  </div>
))

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: ReactNode }) => <a>{children}</a>,
  useNavigate: () => navigateMock,
  useSearch: () => useSearchMock(),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => {
        if (key === 'login.enterpriseWechatTitle') {
          return 'Enterprise WeCom Login'
        }
        if (key === 'login.enterpriseWechatHint') {
          return 'Use WeCom to continue.'
        }
        return key
      },
      i18n: { resolvedLanguage: 'en' },
    }),
  }
})

vi.mock('lucide-react', () => ({
  Eye: () => null,
  EyeOff: () => null,
}))

vi.mock('@/api/client', () => ({
  getDirectAuthRuntimeConfig: () => getDirectAuthRuntimeConfigMock(),
}))

vi.mock('@/features/auth/login-button', () => ({
  LoginButton: (props: { methods?: AuthMethod[] }) => loginButtonMock(props),
}))

vi.mock('@/features/auth/session-bootstrap-entry', () => ({
  SessionBootstrapEntry: () => null,
}))

vi.mock('@/features/auth/use-auth-methods', () => ({
  useAuthMethods: () => useAuthMethodsMock(),
}))

vi.mock('@/features/auth/use-password-login', () => ({
  usePasswordLogin: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
    error: null,
  }),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: ReactNode }) => children,
}))

vi.mock('@/shared/ui/input', () => ({
  Input: () => null,
}))

vi.mock('@/shared/ui/tabs', () => ({
  Tabs: ({ children }: { children: ReactNode }) => children,
  TabsContent: ({ children }: { children: ReactNode }) => children,
  TabsList: ({ children }: { children: ReactNode }) => children,
  TabsTrigger: ({ children }: { children: ReactNode }) => children,
}))

import { LoginPage } from './login'

describe('LoginPage', () => {
  beforeEach(() => {
    navigateMock.mockReset()
    useSearchMock.mockReturnValue({ returnTo: '' })
    getDirectAuthRuntimeConfigMock.mockReturnValue({ enabled: false })
    useAuthMethodsMock.mockReturnValue({ data: [], isLoading: false })
    loginButtonMock.mockClear()
  })

  it('exports a named component function', () => {
    expect(typeof LoginPage).toBe('function')
  })

  it('renders enterprise-only UI when WeCom is the only method', () => {
    useAuthMethodsMock.mockReturnValue({
      data: [
        {
          id: 'wechatwork',
          methodType: 'OAUTH_REDIRECT',
          provider: 'WeChatWork',
          displayName: 'WeCom',
          actionUrl: '/oauth/wechatwork',
        },
      ],
    })

    const html = renderToStaticMarkup(<LoginPage />)

    expect(html).toContain('Enterprise WeCom Login')
    expect(html).toContain('FR24 SkillHub')
    expect(html).toContain('login-button:1:WeChatWork')
    expect(html).not.toContain('login.tabPassword')
    expect(html).not.toContain('login.register')
    expect(html).not.toContain('login.forgotPassword')
  })

  it('keeps default multi-method login form for non-enterprise-only scenarios', () => {
    useAuthMethodsMock.mockReturnValue({
      data: [
        {
          id: 'password',
          methodType: 'PASSWORD',
          provider: 'local',
          displayName: 'Local Account',
          actionUrl: '/api/auth/login',
        },
        {
          id: 'github',
          methodType: 'OAUTH_REDIRECT',
          provider: 'github',
          displayName: 'GitHub',
          actionUrl: '/oauth/github',
        },
      ],
    })

    const html = renderToStaticMarkup(<LoginPage />)

    expect(html).toContain('login.title')
    expect(html).toContain('login.subtitle')
    expect(html).toContain('login.tabPassword')
    expect(html).toContain('login.tabOAuth')
    expect(html).toContain('login.submit')
    expect(html).toContain('login.register')
  })

  it('omits OAuth UI when the backend exposes no OAuth methods', () => {
    useAuthMethodsMock.mockReturnValue({
      data: [
        {
          id: 'password',
          methodType: 'PASSWORD',
          provider: 'local',
          displayName: 'Local Account',
          actionUrl: '/api/auth/login',
        },
      ],
      isLoading: false,
    })

    const html = renderToStaticMarkup(<LoginPage />)

    expect(html).toContain('login.tabPassword')
    expect(html).not.toContain('login.tabOAuth')
    expect(html).not.toContain('login-button:')
  })

  it('keeps password UI when direct auth runtime is enabled', () => {
    getDirectAuthRuntimeConfigMock.mockReturnValue({ enabled: true, provider: 'private-sso' })
    useAuthMethodsMock.mockReturnValue({
      data: [
        {
          id: 'wechatwork',
          methodType: 'OAUTH_REDIRECT',
          provider: 'WeChatWork',
          displayName: 'WeCom',
          actionUrl: '/oauth/wechatwork',
        },
      ],
      isLoading: false,
    })

    const html = renderToStaticMarkup(<LoginPage />)

    expect(html).toContain('login.tabPassword')
    expect(html).toContain('login.submit')
    expect(html).not.toContain('Enterprise WeCom Login')
  })

  it('keeps standard login surface while auth methods are loading', () => {
    useAuthMethodsMock.mockReturnValue({ data: undefined, isLoading: true })

    const html = renderToStaticMarkup(<LoginPage />)

    expect(html).toContain('login.tabPassword')
    expect(html).not.toContain('login.tabOAuth')
    expect(html).not.toContain('Enterprise WeCom Login')
  })
})
