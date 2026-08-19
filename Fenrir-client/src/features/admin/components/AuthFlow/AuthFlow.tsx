import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, authApi, setupApi } from '@/features/admin/api/adminApi'
import type { LoginResponse, SystemState } from '@/features/admin/api/adminApi'
import { useAdminSession } from '@/features/admin/context/AdminSessionContext'
import {
  currentRelayUrl,
  derivePubkeyFromNsec,
  signLoginWithNip07,
  signLoginWithNsec,
} from '@/features/admin/nostr/signLogin'
import { clearStoredNsec, decryptStoredNsec, encryptAndStoreNsec, hasStoredNsec } from '@/features/admin/crypto/nsecVault'
import { formatError } from '@/features/admin/lib/formatError'
import { useToast } from '@/components/ui/Toast'
import SetupTokenStep from './steps/SetupTokenStep'
import NsecStep from './steps/NsecStep'
import PinCreateStep from './steps/PinCreateStep'
import PinConfirmStep from './steps/PinConfirmStep'
import PinLoginStep from './steps/PinLoginStep'
import SetupCompleteStep from './steps/SetupCompleteStep'
import UnauthorizedOwnerStep from './steps/UnauthorizedOwnerStep'

/** The backend returns 403 only when the pubkey isn't a registered operator at all (see AuthController.login / SetupController.complete) - any other status is a real error, not an authorization verdict. */
function isUnregisteredOperator(err: unknown): boolean {
  return err instanceof ApiError && err.status === 403
}

interface AuthFlowProps {
  relayName: string
  systemState: SystemState
}

/**
 * `system-setup`: no operators exist yet, gated by the one-time setup token.
 * `browser-register`: system is READY but this browser has no encrypted nsec yet.
 * `pin-login`: system is READY and this browser already holds an encrypted nsec.
 *
 * Set once at mount from server + local-storage state - see the task's "system setup state and
 * browser credential state are separate" note. Individual steps (e.g. "use nsec instead") move
 * `step` around freely without ever needing to change `mode`, since `browser-register` and
 * `pin-login` both authenticate the same way (authApi), differing only in their entry step.
 */
type FlowMode = 'system-setup' | 'browser-register' | 'pin-login'

type FlowStep =
  | 'setup-token'
  | 'nsec'
  | 'pin-create'
  | 'pin-confirm'
  | 'pin-login'
  | 'setup-complete'
  | 'unauthorized'

/**
 * Central state controller for the Admin Console login screen (see AuthFlow's step components
 * under ./steps). Owns the nsec/PIN only in memory for the lifetime of a single registration
 * attempt - nothing sensitive is ever kept in this component's state longer than it takes to
 * encrypt-and-store it (see nsecVault) or hand it to the signing call.
 */
export default function AuthFlow({ relayName, systemState }: AuthFlowProps) {
  const { setSession } = useAdminSession()
  const { showToast } = useToast()
  const navigate = useNavigate()

  const [mode] = useState<FlowMode>(() =>
    systemState === 'INITIAL_SETUP' ? 'system-setup' : hasStoredNsec() ? 'pin-login' : 'browser-register',
  )
  const [step, setStep] = useState<FlowStep>(() =>
    mode === 'system-setup' ? 'setup-token' : mode === 'pin-login' ? 'pin-login' : 'nsec',
  )

  const [setupToken, setSetupToken] = useState('')
  const [pendingNsec, setPendingNsec] = useState('')
  const [pendingPin, setPendingPin] = useState('')
  const [pendingLogin, setPendingLogin] = useState<LoginResponse | null>(null)
  const [unauthorizedPubkey, setUnauthorizedPubkey] = useState('')

  /** Challenges are single-use with a short TTL, so always fetch a fresh one right before signing. */
  const completeAuth = useCallback(
    async (nsecValue: string | null): Promise<LoginResponse> => {
      const relayUrl = currentRelayUrl()
      if (mode === 'system-setup') {
        const { challenge } = await setupApi.challenge(setupToken)
        const rawEvent =
          nsecValue === null
            ? await signLoginWithNip07(challenge, relayUrl)
            : await signLoginWithNsec(nsecValue, challenge, relayUrl)
        return setupApi.complete(setupToken, rawEvent)
      }
      const { challenge } = await authApi.challenge()
      const rawEvent =
        nsecValue === null
          ? await signLoginWithNip07(challenge, relayUrl)
          : await signLoginWithNsec(nsecValue, challenge, relayUrl)
      return authApi.login(rawEvent)
    },
    [mode, setupToken],
  )

  /**
   * Runs the sign+submit call, then routes the outcome: a 403 (pubkey not a registered operator -
   * see isUnregisteredOperator) sends the user to UnauthorizedOwnerStep instead of surfacing an
   * inline error, since it's not a "try again" situation. Every other error is rethrown for the
   * calling step to show inline (system-setup 403s, e.g. an expired setup token, are real errors
   * here and always rethrown). A toast fires either way. Any *registered* role (OWNER, ADMIN,
   * general operator) is admitted - the console itself restricts operator management to OWNER.
   */
  async function attemptLogin(nsecValue: string | null): Promise<LoginResponse | null> {
    try {
      const result = await completeAuth(nsecValue)
      showToast('success', 'Signed in successfully')
      return result
    } catch (err) {
      if (mode !== 'system-setup' && isUnregisteredOperator(err)) {
        showToast('error', formatError(err, 'not authorized'))
        setUnauthorizedPubkey(nsecValue ? await derivePubkeyFromNsec(nsecValue).catch(() => '') : '')
        setStep('unauthorized')
        return null
      }
      showToast('error', formatError(err, 'sign-in failed'))
      throw err
    }
  }

  function handleTokenVerified(token: string) {
    setSetupToken(token)
    setStep('nsec')
  }

  async function handleNip07Submit() {
    const result = await attemptLogin(null)
    if (!result) return
    if (mode === 'system-setup') {
      setPendingLogin(result)
      setStep('setup-complete')
    } else {
      setSession(result)
    }
  }

  function handleNsecReady(nsec: string) {
    setPendingNsec(nsec)
    setStep('pin-create')
  }

  function handlePinCreated(pin: string) {
    setPendingPin(pin)
    setStep('pin-confirm')
  }

  async function handlePinConfirmed(pin: string) {
    const result = await attemptLogin(pendingNsec)
    if (!result) {
      setPendingNsec('')
      setPendingPin('')
      return
    }
    await encryptAndStoreNsec(pendingNsec, pin)
    setPendingNsec('')
    setPendingPin('')
    if (mode === 'system-setup') {
      setPendingLogin(result)
      setStep('setup-complete')
    } else {
      setSession(result)
    }
  }

  async function handlePinLoginSubmit(pin: string) {
    const nsec = await decryptStoredNsec(pin)
    const result = await attemptLogin(nsec)
    if (!result) return
    setSession(result)
  }

  /** Wipes any locally-stored credential and drops back to nsec entry - used for both "forgot PIN" and "not authorized". */
  function handleTryAnotherAccount() {
    clearStoredNsec()
    setPendingNsec('')
    setPendingPin('')
    setUnauthorizedPubkey('')
    setStep('nsec')
  }

  function handleGoToFeed() {
    if (pendingLogin) setSession(pendingLogin)
    navigate('/feed', { replace: true })
  }

  switch (step) {
    case 'setup-token':
      return <SetupTokenStep relayName={relayName} onVerified={handleTokenVerified} />

    case 'nsec':
      return (
        <NsecStep
          relayName={relayName}
          onNsecReady={handleNsecReady}
          onNip07Submit={handleNip07Submit}
          onBack={mode === 'system-setup' ? () => setStep('setup-token') : undefined}
        />
      )

    case 'pin-create':
      return (
        <PinCreateStep
          onCreated={handlePinCreated}
          onBack={() => {
            setPendingNsec('')
            setStep('nsec')
          }}
        />
      )

    case 'pin-confirm':
      return (
        <PinConfirmStep
          originalPin={pendingPin}
          onConfirmed={handlePinConfirmed}
          onBack={() => {
            setPendingPin('')
            setStep('pin-create')
          }}
        />
      )

    case 'pin-login':
      return (
        <PinLoginStep relayName={relayName} onSubmit={handlePinLoginSubmit} onUseNsecInstead={handleTryAnotherAccount} />
      )

    case 'setup-complete':
      return <SetupCompleteStep onContinue={handleGoToFeed} />

    case 'unauthorized':
      return (
        <UnauthorizedOwnerStep
          relayName={relayName}
          pubkey={unauthorizedPubkey}
          onTryAnotherAccount={handleTryAnotherAccount}
        />
      )
  }
}
